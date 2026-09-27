import { useNavigate } from 'react-router-dom'
import { PageHeader, Card, Kpi } from '../ui/components'
import { useAccounts } from '../api/hooks'
import { fmt, money, moneyCents, pct } from '../ui/format'

export function Accounts() {
  const navigate = useNavigate()
  const { data } = useAccounts()
  const items = data?.items || []

  const totalBalance = items.reduce((s, a) => s + (a.balanceCents || 0), 0)
  const avgUtil = items.length ? items.reduce((s, a) => s + (a.utilization || 0), 0) / items.length : 0
  const delinquent = items.filter((a) => a.status === 'DELINQUENT').length

  return (
    <>
      <PageHeader
        title={<>Accounts</>}
        description="Accounts opened from approved decisions through the transactional outbox. Statements, payments and credit line increases."
      />
      <div className="g" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 26 }}>
        <Kpi label="Accounts" value={fmt(items.length)} sub="open" />
        <Kpi label="Total balance" value={moneyCents(totalBalance)} sub="across the book" />
        <Kpi label="Average utilization" value={pct(avgUtil, 0)} sub="balance vs limit" />
        <Kpi label="Delinquent" value={fmt(delinquent)} sub="30+ days past due" cls={delinquent ? 't-warn' : ''} />
      </div>

      <Card>
        {items.length ? (
          <table>
            <thead>
              <tr>
                <th>Account</th>
                <th>Applicant</th>
                <th>Product</th>
                <th>Limit</th>
                <th>Balance</th>
                <th>Utilization</th>
                <th>Status</th>
                <th>DPD</th>
              </tr>
            </thead>
            <tbody>
              {items.map((a) => (
                <tr key={a.accountId} className="cl" onClick={() => navigate(`/app/accounts/${a.accountId}`)}>
                  <td className="mono">
                    <b>{a.accountId}</b>
                  </td>
                  <td>{a.displayName}</td>
                  <td>{a.product}</td>
                  <td>{money(a.creditLimit || 0)}</td>
                  <td>{moneyCents(a.balanceCents || 0)}</td>
                  <td>
                    <div className="row" style={{ gap: 8 }}>
                      <div className="mbar">
                        <div style={{ width: `${Math.min(100, (a.utilization || 0) * 100)}%` }} />
                      </div>
                      <span className="t-muted">{pct(a.utilization || 0, 0)}</span>
                    </div>
                  </td>
                  <td>
                    <span className={`st ${a.status === 'DELINQUENT' ? 'DRAFT' : a.status === 'CHARGED_OFF' ? 'PROPOSED' : 'LIVE'}`}>{a.status}</span>
                  </td>
                  <td className={a.daysPastDue ? 't-bad' : 't-muted'}>{a.daysPastDue || 0}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="empty">No accounts yet. Approve an application and the outbox opens one within seconds.</div>
        )}
      </Card>
    </>
  )
}
