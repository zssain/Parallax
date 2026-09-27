import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { keys } from '../keys'
import type { DriftReport } from '../types'

export function useDriftLatest() {
  return useQuery({
    queryKey: keys.driftLatest,
    queryFn: () => apiFetch<DriftReport>('/api/v1/drift/latest'),
    retry: false,
  })
}

export function useRunDrift() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch<DriftReport>('/api/v1/drift/run', { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.driftLatest })
      qc.invalidateQueries({ queryKey: keys.overview })
    },
  })
}

export function useSimulateDrift() {
  return useMutation({
    mutationFn: (shift: number) => apiFetch<DriftReport>('/api/v1/drift/simulate', { method: 'POST', body: { shift } }),
  })
}
