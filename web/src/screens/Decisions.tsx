import { PageHeader, LiveChips } from '../ui/components'

export function Decisions() {
  return (
    <PageHeader
      eyebrow="Decisions"
      title="Every application, every outcome"
      description="The current state of each application. Click a row to see how the engine decided, reproduce it, or read its ledger trail."
      right={<LiveChips />}
    />
  )
}
