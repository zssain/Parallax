import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { keys } from '../keys'
import type { BureauFaultRequest, BureauFaultResult, BureauPullView, IdempotencyKeyView, SystemStatus } from '../types'

export function useSystemStatus(opts?: { refetchInterval?: number }) {
  return useQuery({
    queryKey: keys.systemStatus,
    queryFn: () => apiFetch<SystemStatus>('/api/v1/system/status'),
    refetchInterval: opts?.refetchInterval,
  })
}

export function useIdempotencyKeys() {
  return useQuery({
    queryKey: keys.idempotencyKeys,
    queryFn: () => apiFetch<IdempotencyKeyView[]>('/api/v1/system/idempotency-keys'),
  })
}

export function useBureauPulls() {
  return useQuery({ queryKey: keys.bureauPulls, queryFn: () => apiFetch<BureauPullView[]>('/api/v1/system/bureau-pulls') })
}

export function useBureauFault() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: BureauFaultRequest) =>
      apiFetch<BureauFaultResult>('/api/v1/system/bureau-fault', { method: 'POST', body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.systemStatus })
      qc.invalidateQueries({ queryKey: keys.overview })
      qc.invalidateQueries({ queryKey: keys.applicationsAll })
      qc.invalidateQueries({ queryKey: keys.ledgerAll })
      qc.invalidateQueries({ queryKey: keys.reviewQueue })
    },
  })
}
