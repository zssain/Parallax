// Central query-key registry. Mutations invalidate by the coarse prefixes here.
export const keys = {
  me: ['me'] as const,
  overview: ['overview'] as const,

  applications: (params: Record<string, unknown>) => ['applications', params] as const,
  applicationsAll: ['applications'] as const,
  application: (id: string) => ['application', id] as const,
  reproduce: (seq: number) => ['reproduce', seq] as const,
  notice: (id: string) => ['notice', id] as const,

  reviewQueue: ['reviews', 'queue'] as const,
  overrideStats: ['reviews', 'override-stats'] as const,

  ledger: (source: string) => ['ledger', source] as const,
  ledgerAll: ['ledger'] as const,
  ledgerStats: ['ledger', 'stats'] as const,

  labVersions: ['lab', 'versions'] as const,
  labLive: ['lab', 'live'] as const,
  replayJob: (jobId: string) => ['lab', 'replay', jobId] as const,
  flips: (jobId: string, page: number) => ['lab', 'flips', jobId, page] as const,
  flipDetail: (jobId: string, seq: number) => ['lab', 'flip', jobId, seq] as const,
  shadowResults: (v: string) => ['lab', 'shadow', v] as const,
  compare: (a: string, b: string) => ['lab', 'compare', a, b] as const,

  driftLatest: ['drift', 'latest'] as const,

  systemStatus: ['system', 'status'] as const,
  idempotencyKeys: ['system', 'idempotency-keys'] as const,
  bureauPulls: ['system', 'bureau-pulls'] as const,

  assistantStatus: ['assistant', 'status'] as const,
  assistantTools: ['assistant', 'tools'] as const,

  accounts: (status: string) => ['accounts', status] as const,
  accountsAll: ['accounts'] as const,
  account: (id: string) => ['account', id] as const,
  collectionsSummary: ['collections', 'summary'] as const,
  collections: (bucket: string) => ['collections', 'queue', bucket] as const,
  collectionsAll: ['collections'] as const,
}
