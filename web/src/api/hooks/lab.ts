import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { keys } from '../keys'
import type {
  CompareView,
  CreateVersionRequest,
  FlipDetail,
  FlipPage,
  JobView,
  RuleConfig,
  ShadowResults,
  VersionsResponse,
  VersionView,
} from '../types'

export function useVersions() {
  return useQuery({ queryKey: keys.labVersions, queryFn: () => apiFetch<VersionsResponse>('/api/v1/lab/versions') })
}

function invalidateLab(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: keys.labVersions })
  qc.invalidateQueries({ queryKey: keys.labLive })
  qc.invalidateQueries({ queryKey: keys.overview })
  qc.invalidateQueries({ queryKey: keys.ledgerAll })
}

export function useCreateVersion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateVersionRequest) =>
      apiFetch<VersionView>('/api/v1/lab/versions', { method: 'POST', body }),
    onSuccess: () => invalidateLab(qc),
  })
}

export function useUpdateConfig() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vars: { v: string; config: RuleConfig }) =>
      apiFetch<VersionView>(`/api/v1/lab/versions/${vars.v}/config`, { method: 'PUT', body: vars.config }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.labVersions }),
  })
}

export function useBackToDraft() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: string) => apiFetch<VersionView>(`/api/v1/lab/versions/${v}/draft`, { method: 'POST' }),
    onSuccess: () => invalidateLab(qc),
  })
}

export function useDiscardVersion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: string) => apiFetch<void>(`/api/v1/lab/versions/${v}`, { method: 'DELETE' }),
    onSuccess: () => invalidateLab(qc),
  })
}

export function useStartReplay() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: string) => apiFetch<{ jobId: string }>(`/api/v1/lab/versions/${v}/replays`, { method: 'POST', body: {} }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.labVersions }),
  })
}

export function useReplayJob(jobId: string | undefined, poll: boolean) {
  return useQuery({
    queryKey: keys.replayJob(jobId || ''),
    queryFn: () => apiFetch<JobView>(`/api/v1/lab/replays/${jobId}`),
    enabled: !!jobId,
    // Poll every 500 ms while the job is QUEUED or RUNNING (SPEC §10).
    refetchInterval: poll
      ? (query) => {
          const s = (query.state.data as JobView | undefined)?.status
          return s === 'QUEUED' || s === 'RUNNING' ? 500 : false
        }
      : undefined,
  })
}

export function useFlips(jobId: string | undefined, page = 0, size = 20) {
  return useQuery({
    queryKey: keys.flips(jobId || '', page),
    queryFn: () => apiFetch<FlipPage>(`/api/v1/lab/replays/${jobId}/flips?page=${page}&size=${size}`),
    enabled: !!jobId,
  })
}

export function useFlipDetail(jobId: string | undefined, seq: number | null) {
  return useQuery({
    queryKey: keys.flipDetail(jobId || '', seq ?? -1),
    queryFn: () => apiFetch<FlipDetail>(`/api/v1/lab/replays/${jobId}/flips/${seq}`),
    enabled: !!jobId && seq != null,
  })
}

export function useProposeVersion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: string) => apiFetch<VersionView>(`/api/v1/lab/versions/${v}/propose`, { method: 'POST' }),
    onSuccess: () => invalidateLab(qc),
  })
}

export function useApproveVersion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: string) => apiFetch<VersionView>(`/api/v1/lab/versions/${v}/approve`, { method: 'POST' }),
    onSuccess: () => invalidateLab(qc),
  })
}

export function useRejectVersion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vars: { v: string; note: string }) =>
      apiFetch<VersionView>(`/api/v1/lab/versions/${vars.v}/reject`, { method: 'POST', body: { note: vars.note } }),
    onSuccess: () => invalidateLab(qc),
  })
}

export function useRollback() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch<unknown>('/api/v1/lab/rollback', { method: 'POST' }),
    onSuccess: () => invalidateLab(qc),
  })
}

export function useToggleShadow() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vars: { v: string; enabled: boolean }) =>
      apiFetch<{ version: string; enabled: boolean }>(`/api/v1/lab/versions/${vars.v}/shadow`, {
        method: 'POST',
        body: { enabled: vars.enabled },
      }),
    onSuccess: () => invalidateLab(qc),
  })
}

export function useShadowResults(v: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: keys.shadowResults(v || ''),
    queryFn: () => apiFetch<ShadowResults>(`/api/v1/lab/versions/${v}/shadow-results`),
    enabled: !!v && enabled,
    refetchInterval: 5000,
  })
}

export function useCompareVersions(a: string | undefined, b: string | undefined) {
  return useQuery({
    queryKey: keys.compare(a || '', b || ''),
    queryFn: () => apiFetch<CompareView>(`/api/v1/lab/versions/compare?a=${a}&b=${b}`),
    enabled: !!a && !!b,
  })
}
