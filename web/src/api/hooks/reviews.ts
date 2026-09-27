import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { keys } from '../keys'
import type { OverrideStats, QueueItem, RecordResult, ReviewRequest } from '../types'

export function useReviewQueue(opts?: { refetchInterval?: number }) {
  return useQuery({
    queryKey: keys.reviewQueue,
    queryFn: () => apiFetch<QueueItem[]>('/api/v1/reviews/queue'),
    refetchInterval: opts?.refetchInterval,
  })
}

export function useOverrideStats() {
  return useQuery({ queryKey: keys.overrideStats, queryFn: () => apiFetch<OverrideStats>('/api/v1/reviews/override-stats') })
}

export function useSubmitReview() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vars: { id: string; body: ReviewRequest }) =>
      apiFetch<RecordResult>(`/api/v1/reviews/${vars.id}`, { method: 'POST', body: vars.body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.reviewQueue })
      qc.invalidateQueries({ queryKey: keys.applicationsAll })
      qc.invalidateQueries({ queryKey: keys.overview })
      qc.invalidateQueries({ queryKey: keys.ledgerAll })
      qc.invalidateQueries({ queryKey: keys.overrideStats })
    },
  })
}
