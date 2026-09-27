import { PageHeader, LiveChips } from '../ui/components'

export function System() {
  return (
    <PageHeader
      eyebrow="System"
      title="Health, resilience and plumbing"
      description="Service health, the bureau circuit breaker, automatic re-decisions, idempotency keys and bureau report reuse."
      right={<LiveChips />}
    />
  )
}
