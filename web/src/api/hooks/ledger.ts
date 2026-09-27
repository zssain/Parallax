import { useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { keys } from '../keys'
import type { DemoUpdate, LedgerPage, LedgerStats, TamperSimulation, VerifyResult } from '../types'

const PAGE_SIZE = 50

export function useLedger(source = 'ALL') {
  return useInfiniteQuery({
    queryKey: keys.ledger(source),
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      apiFetch<LedgerPage>(`/api/v1/ledger?source=${source}&page=${pageParam}&size=${PAGE_SIZE}`),
    getNextPageParam: (last) => {
      const loaded = (last.page + 1) * last.size
      return loaded < last.total ? last.page + 1 : undefined
    },
  })
}

export function useLedgerStats() {
  return useQuery({ queryKey: keys.ledgerStats, queryFn: () => apiFetch<LedgerStats>('/api/v1/ledger/stats') })
}

export function useVerifyChain() {
  return useMutation({ mutationFn: () => apiFetch<VerifyResult>('/api/v1/ledger/verify') })
}

export function useAttemptUpdate() {
  return useMutation({ mutationFn: () => apiFetch<DemoUpdate>('/api/v1/ledger/demo/attempt-update', { method: 'POST' }) })
}

export function useTamperTest() {
  return useMutation({
    mutationFn: () => apiFetch<TamperSimulation>('/api/v1/ledger/demo/tamper-simulation', { method: 'POST' }),
  })
}
