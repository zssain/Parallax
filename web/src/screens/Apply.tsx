import { PageHeader, LiveChips } from '../ui/components'

export function Apply() {
  return (
    <PageHeader
      eyebrow="New application"
      title="Submit a credit application"
      description="Runs the full pipeline: validation, idempotency, SOAP bureau pull, fraud screen, decision engine and a transactional ledger write."
      right={<LiveChips />}
    />
  )
}
