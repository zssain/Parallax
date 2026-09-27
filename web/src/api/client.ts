import { authStore, type Credentials } from '../auth/authStore'

/** A parsed RFC 7807 ProblemDetail (SPEC §15), thrown by apiFetch on any non-2xx response. */
export class ApiError extends Error {
  status: number
  title?: string
  detail?: string
  fieldErrors?: { field: string; message: string }[]
  body?: unknown

  constructor(status: number, title?: string, detail?: string, fieldErrors?: { field: string; message: string }[], body?: unknown) {
    super(detail || title || `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
    this.fieldErrors = fieldErrors
    this.body = body
  }
}

export interface FetchOpts {
  method?: string
  body?: unknown
  headers?: Record<string, string>
  /** Explicit credentials (used by login to validate a candidate identity). */
  auth?: Credentials
  /** Parse the response as text/plain (the adverse-action notice). */
  text?: boolean
  /** Extra idempotency / custom header for POST /applications. */
  idempotencyKey?: string
}

export interface RawResult<T> {
  status: number
  headers: Headers
  data: T
}

/** Low-level fetch: never throws on HTTP status. On 401 for a stored identity, triggers logout. */
export async function request<T>(path: string, opts: FetchOpts = {}): Promise<RawResult<T>> {
  const headers: Record<string, string> = { ...(opts.headers || {}) }
  const authHeader = authStore.header(opts.auth)
  if (authHeader) headers['Authorization'] = authHeader
  if (opts.idempotencyKey) headers['Idempotency-Key'] = opts.idempotencyKey

  let bodyInit: BodyInit | undefined
  if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    bodyInit = JSON.stringify(opts.body)
  }
  if (!headers['Accept']) headers['Accept'] = opts.text ? 'text/plain' : 'application/json'

  const res = await fetch(path, { method: opts.method || 'GET', headers, body: bodyInit })

  // Global 401 handling: a stored identity that is rejected is logged out. An explicit-auth call
  // (login validation) is left to the caller to interpret.
  if (res.status === 401 && !opts.auth) {
    authStore.clear()
    authStore.onUnauthorized()
  }

  let data: unknown = null
  const ct = res.headers.get('content-type') || ''
  if (res.status !== 204) {
    if (opts.text) {
      data = await res.text()
    } else if (ct.includes('json')) {
      data = await res.json().catch(() => null)
    } else {
      const t = await res.text()
      data = t ? t : null
    }
  }
  return { status: res.status, headers: res.headers, data: data as T }
}

function toApiError(status: number, data: unknown): ApiError {
  if (data && typeof data === 'object') {
    const p = data as Record<string, unknown>
    return new ApiError(
      status,
      (p.title as string) || undefined,
      (p.detail as string) || undefined,
      (p.fieldErrors as { field: string; message: string }[]) || undefined,
      data,
    )
  }
  return new ApiError(status, undefined, typeof data === 'string' ? data : undefined, undefined, data)
}

/** Typed fetch: returns the JSON (or text) body, throwing an ApiError on any non-2xx. */
export async function apiFetch<T>(path: string, opts: FetchOpts = {}): Promise<T> {
  const r = await request<T>(path, opts)
  if (r.status < 200 || r.status >= 300) throw toApiError(r.status, r.data)
  return r.data
}
