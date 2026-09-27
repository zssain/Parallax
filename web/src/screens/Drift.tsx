import { PageHeader } from '../ui/components'

export function Drift() {
  return (
    <PageHeader
      eyebrow="Drift monitor"
      title="Population Stability Index"
      description="Compares the current applicant score distribution with the development baseline. A scheduled job runs this nightly and alerts above the threshold."
    />
  )
}
