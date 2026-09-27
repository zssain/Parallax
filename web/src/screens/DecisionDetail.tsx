import { useParams } from 'react-router-dom'
import { PageHeader } from '../ui/components'

export function DecisionDetail() {
  const { id } = useParams()
  return (
    <PageHeader
      eyebrow="Decision detail"
      title={id || 'Decision'}
      description="How the engine reached this outcome — policy checks, scorecard points, ranked reason codes — and the ledger evidence that proves it."
    />
  )
}
