// In-memory credential holder shared by the fetch client and the AuthProvider. Never persisted to
// any browser storage (SPEC §16), so a page refresh returns to /login.
export interface Credentials {
  username: string
  password: string
}

let creds: Credentials | null = null
let unauthorizedHandler: (() => void) | null = null

export const authStore = {
  set(c: Credentials) {
    creds = c
  },
  clear() {
    creds = null
  },
  get(): Credentials | null {
    return creds
  },
  /** Authorization: Basic … for the current (or an explicitly supplied) identity. */
  header(override?: Credentials): string | null {
    const c = override ?? creds
    if (!c) return null
    return 'Basic ' + btoa(`${c.username}:${c.password}`)
  },
  setUnauthorizedHandler(fn: () => void) {
    unauthorizedHandler = fn
  },
  onUnauthorized() {
    unauthorizedHandler?.()
  },
}
