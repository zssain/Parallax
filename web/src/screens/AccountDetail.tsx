import { useParams } from 'react-router-dom'
import { PageHeader } from '../ui/components'

export function AccountDetail() {
  const { id } = useParams()
  return (
    <PageHeader
      eyebrow="Lifecycle"
      title={id || 'Account'}
      description="Statements, transactions, payment history and credit line increases for this account."
    />
  )
}
