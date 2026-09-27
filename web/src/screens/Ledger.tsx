import { PageHeader } from '../ui/components'

export function Ledger() {
  return (
    <PageHeader
      eyebrow="Decision ledger"
      title="Append-only, hash-chained"
      description="Every decision, override, re-decision and promotion. The service role has no UPDATE or DELETE grant; each record stores the hash of the one before it."
    />
  )
}
