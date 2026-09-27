import { PageHeader, LiveChips } from '../ui/components'

export function Overview() {
  return (
    <PageHeader
      eyebrow="Overview"
      title="Portfolio at a glance"
      description="Live decisions, the rules behind them and what needs attention. Every figure here is read from the decision ledger."
      right={<LiveChips />}
    />
  )
}
