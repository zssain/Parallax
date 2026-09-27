import { PageHeader } from '../ui/components'

export function Assistant() {
  return (
    <PageHeader
      eyebrow="Assistant"
      title="Underwriter & strategist agent"
      description="Answers questions by calling Parallax APIs as tools. Read-only credentials, masked PII, numbers only from tool results. It can never make or change a decision."
      right={<span className="chipbox">role: ASSISTANT (read-only)</span>}
    />
  )
}
