import { useNavigate } from 'react-router-dom'
import { PageHeader, Card } from '../ui/components'
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
        eyebrow="Lifecycle"
        title="Accounts"
        description="Accounts opened from approved decisions through the transactional outbox. Statements, payments and credit line increases."
      />
      <div className="kpis k4">
        <div className="kpi">
          <div className="lbl">Accounts</div>
          <div className="v">{fmt(items.length)}</div>
          <small>open</small>
        </div>
        <div className="kpi">
          <div className="lbl">Total balance</div>
          <div className="v">{moneyCents(totalBalance)}</div>
          <small>across the book</small>
        </div>
        <div className="kpi">
          <div className="lbl">Average utilization</div>
          <div className="v">{pct(avgUtil, 0)}</div>
          <small>balance vs limit</small>
        </div>
        <div className="kpi">
          <div className="lbl">Delinquent</div>
          <div className={`v ${delinquent ? 't-warn' : ''}`}>{fmt(delinquent)}</div>
          <small>30+ days past due</small>
        </div>
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
