import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../client'
import { keys } from '../keys'
import type {
  AccountDetail,
  AccountPage,
  CliRequestBody,
  CliRequestResult,
  CollectionActionRequest,
  CollectionItem,
  CollectionsSummary,
  SimulateMonthRequest,
} from '../types'

export function useAccounts(status = 'ALL') {
  const qs = status && status !== 'ALL' ? `?status=${status}` : ''
  return useQuery({ queryKey: keys.accounts(status), queryFn: () => apiFetch<AccountPage>(`/api/v1/accounts${qs}`) })
}

export function useAccount(id: string | undefined) {
  return useQuery({
    queryKey: keys.account(id || ''),
    queryFn: () => apiFetch<AccountDetail>(`/api/v1/accounts/${id}`),
    enabled: !!id,
  })
}

export function useSimulateMonth(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: SimulateMonthRequest) =>
      apiFetch<AccountDetail>(`/api/v1/accounts/${id}/simulate-month`, { method: 'POST', body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.account(id) })
      qc.invalidateQueries({ queryKey: keys.accountsAll })
      qc.invalidateQueries({ queryKey: keys.collectionsAll })
    },
  })
}

export function useRequestCli(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CliRequestBody) =>
      apiFetch<CliRequestResult>(`/api/v1/accounts/${id}/cli-requests`, { method: 'POST', body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.account(id) })
      qc.invalidateQueries({ queryKey: keys.accountsAll })
    },
  })
}

export function useCollectionsSummary() {
  return useQuery({
    queryKey: keys.collectionsSummary,
    queryFn: () => apiFetch<CollectionsSummary>('/api/v1/collections/summary'),
  })
}

export function useCollections(bucket = 'ALL') {
  const qs = bucket && bucket !== 'ALL' ? `?bucket=${bucket}` : ''
  return useQuery({
    queryKey: keys.collections(bucket),
    queryFn: () => apiFetch<CollectionItem[]>(`/api/v1/collections${qs}`),
  })
}

export function useCollectionAction() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vars: { accountId: string; body: CollectionActionRequest }) =>
      apiFetch<void>(`/api/v1/collections/${vars.accountId}/actions`, { method: 'POST', body: vars.body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.collectionsAll })
      qc.invalidateQueries({ queryKey: keys.accountsAll })
    },
  })
}
