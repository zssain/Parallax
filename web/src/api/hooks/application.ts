import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch, request, type RawResult } from '../client'
import { keys } from '../keys'
import type {
  Detail,
  LiveView,
  Me,
  Overview,
  ReproduceView,
  ApplicationRequest,
  SubmitResult,
  ListResponse,
  ListItem,
} from '../types'

/** GET /applications returns the ListResponse plus filter counts (SPEC §15). */
export type DecisionsList = ListResponse & {
  items?: ListItem[]
  counts?: { ALL: number; APPROVED: number; REFER: number; DECLINED: number }
}

export function useMe(enabled = true) {
  return useQuery({ queryKey: keys.me, queryFn: () => apiFetch<Me>('/api/v1/me'), enabled })
}

export function useOverview() {
  return useQuery({ queryKey: keys.overview, queryFn: () => apiFetch<Overview>('/api/v1/overview') })
}

export interface DecisionsParams {
  outcome?: string
  q?: string
  source?: string
  page?: number
  size?: number
}

export function useDecisions(params: DecisionsParams) {
  const qs = new URLSearchParams()
  if (params.outcome && params.outcome !== 'ALL') qs.set('outcome', params.outcome)
  if (params.q) qs.set('q', params.q)
  if (params.source) qs.set('source', params.source)
  qs.set('page', String(params.page ?? 0))
  qs.set('size', String(params.size ?? 20))
  return useQuery({
    queryKey: keys.applications(params as Record<string, unknown>),
    queryFn: () => apiFetch<DecisionsList>(`/api/v1/applications?${qs.toString()}`),
    placeholderData: (prev) => prev,
  })
}

export function useApplication(id: string | undefined, opts?: { pollWhilePending?: boolean }) {
  return useQuery({
    queryKey: keys.application(id || ''),
    queryFn: () => apiFetch<Detail>(`/api/v1/applications/${id}`),
    enabled: !!id,
    // Poll every 3 s while the engine retry job is still deciding an ENGINE_PENDING application.
    refetchInterval: opts?.pollWhilePending
      ? (query) => ((query.state.data as Detail | undefined)?.status === 'ENGINE_PENDING' ? 3000 : false)
      : undefined,
  })
}

export function useLiveVersion() {
  return useQuery({ queryKey: keys.labLive, queryFn: () => apiFetch<LiveView>('/api/v1/lab/versions/live') })
}

/** Reproduce is fetched on demand, so it is exposed as a mutation. */
export function useReproduce() {
  return useMutation({
    mutationFn: (seq: number) => apiFetch<ReproduceView>(`/api/v1/decisions/${seq}/reproduce`),
  })
}

export function useAdverseActionNotice() {
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<string>(`/api/v1/applications/${id}/adverse-action-notice`, { text: true }),
  })
}

/**
 * POST /applications. Returns the raw result (status + headers + body) so the caller can drive the
 * pipeline modal and handle 200 replay / 202 pending / 409 / 422 without an exception.
 */
export function useSubmitApplication() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vars: { body: ApplicationRequest; idempotencyKey: string }): Promise<RawResult<SubmitResult>> =>
      request<SubmitResult>('/api/v1/applications', {
        method: 'POST',
        body: vars.body,
        idempotencyKey: vars.idempotencyKey,
      }),
    onSuccess: (r) => {
      if (r.status >= 200 && r.status < 300) {
        qc.invalidateQueries({ queryKey: keys.applicationsAll })
        qc.invalidateQueries({ queryKey: keys.overview })
        qc.invalidateQueries({ queryKey: keys.reviewQueue })
        qc.invalidateQueries({ queryKey: keys.ledgerAll })
        qc.invalidateQueries({ queryKey: keys.systemStatus })
      }
    },
  })
}
