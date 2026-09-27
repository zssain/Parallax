import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { PageHeader, Card } from '../ui/components'
import { useAccount, useRequestCli, useSimulateMonth } from '../api/hooks'
import { useAuth } from '../app/AuthProvider'
import { useToast } from '../app/ToastProvider'
import { fmtTs, money, moneyCents, monthShort, pct } from '../ui/format'
import { ApiError } from '../api/client'
import type { CliRequestResult } from '../api/types'

export function AccountDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { me } = useAuth()
  const { toast, denyToast } = useToast()
  const { data } = useAccount(id)
  const requestCli = useRequestCli(id!)
  const simulate = useSimulateMonth(id!)

  const acc = data?.account
  const statements = data?.statements || []
  const transactions = data?.transactions || []
  const cliRequests = data?.cliRequests || []

  const [requested, setRequested] = useState('')
  const [acceptCounter, setAcceptCounter] = useState(false)
  const [cliResult, setCliResult] = useState<CliRequestResult | null>(null)
  const [purchases, setPurchases] = useState('400')
  const [payment, setPayment] = useState('200')
  const [payOnTime, setPayOnTime] = useState(true)

  const isUnderwriter = me?.role === 'UNDERWRITER'
  const reqDefault = requested || String((acc?.creditLimit || 1000) * 2)

  function submitCli() {
    if (!isUnderwriter) return denyToast('UNDERWRITER')
    requestCli.mutate(
      { requestedLimit: Number(reqDefault), acceptCounterOffer: acceptCounter },
      {
        onSuccess: (r) => {
          setCliResult(r)
          toast(`CLI ${r.outcome} · new limit ${money(r.newLimit || 0)}`, r.outcome === 'DECLINED' ? 'bad' : 'ok')
        },
        onError: (e) => toast(e instanceof ApiError ? e.detail || 'Could not request an increase' : 'Could not request an increase', 'bad'),
      },
    )
  }

  function simulateMonth() {
    if (!isUnderwriter) return denyToast('UNDERWRITER')
    simulate.mutate(
      { purchasesCents: Math.round(Number(purchases) * 100), paymentCents: Math.round(Number(payment) * 100), payOnTime },
      { onSuccess: () => toast('Advanced one month and closed a statement.', 'ok') },
    )
  }

  const header = (
    <PageHeader
      eyebrow="Lifecycle"
      title={id || 'Account'}
      description="Statements, transactions, payment history and credit line increases for this account."
      right={
        <button className="btn" onClick={() => navigate('/app/accounts')}>
          ← Back
        </button>
      }
    />
  )
  if (!acc) return header

  const result = cliResult || (cliRequests[0] as CliRequestResult | undefined)

  return (
    <>
      {header}
      <div className="kpis k4">
        <div className="kpi">
          <div className="lbl">Credit limit</div>
          <div className="v">{money(acc.creditLimit || 0)}</div>
          <small>APR {((data?.aprBps || 0) / 100).toFixed(2)}%</small>
        </div>
        <div className="kpi">
          <div className="lbl">Balance</div>
          <div className="v">{moneyCents(acc.balanceCents || 0)}</div>
          <small>current</small>
        </div>
        <div className="kpi">
          <div className="lbl">Utilization</div>
          <div className="v">{pct(acc.utilization || 0, 0)}</div>
          <small>balance vs limit</small>
        </div>
        <div className="kpi">
          <div className="lbl">Status · DPD</div>
          <div className={`v ${acc.daysPastDue ? 't-bad' : ''}`}>
            {acc.status} · {acc.daysPastDue || 0}
          </div>
          <small>days past due</small>
        </div>
      </div>

      <div className="g g21">
        <Card>
          <h3>Payment history</h3>
          <div className="payhist">
            {statements
              .slice()
              .reverse()
              .map((s, i) => (
                <div className="col" key={i}>
                  <div className={`cell ${s.paidOnTime === true ? 'ok' : s.paidOnTime === false ? 'bad' : ''}`}>
                    {s.paidOnTime === true ? '✓' : s.paidOnTime === false ? '✕' : ''}
                  </div>
                  <div className="mo">{monthShort(s.periodEnd)}</div>
                </div>
              ))}
          </div>
          <p className="note">Paid on time, missed, or not yet due — the last 12 statements.</p>
        </Card>

        <Card>
          <h3>Credit line increase</h3>
          <div className="form" style={{ gridTemplateColumns: '1fr' }}>
            <div className="f">
              <label>Requested limit</label>
              <input type="number" value={reqDefault} onChange={(e) => setRequested(e.target.value)} />
            </div>
            <label className="chk">
              <input type="checkbox" checked={acceptCounter} onChange={(e) => setAcceptCounter(e.target.checked)} /> Accept a
              counter-offer
            </label>
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <span className="sp" />
            <button className="btn p" onClick={submitCli}>
              Request increase
            </button>
          </div>
          {result && (
            <div
              className="rc"
              style={{ marginTop: 12, borderLeftColor: result.outcome === 'DECLINED' ? 'var(--bad)' : 'var(--ok)' }}
            >
              <code style={{ color: result.outcome === 'DECLINED' ? 'var(--bad)' : 'var(--ok)' }}>{result.outcome}</code>
              <span>
                new limit {money(result.newLimit || 0)}
                {result.reasons && result.reasons.length ? ' · ' + result.reasons.join(', ') : ''}
              </span>
            </div>
          )}
        </Card>
      </div>

      <div className="g g2">
        <Card>
          <h3>Statements</h3>
          <table>
            <thead>
              <tr>
                <th>Period end</th>
                <th>Closing balance</th>
                <th>Minimum due</th>
                <th>Due date</th>
                <th>Paid</th>
                <th>On time</th>
              </tr>
            </thead>
            <tbody>
              {statements
                .slice()
                .reverse()
                .map((s, i) => (
                  <tr key={i}>
                    <td>{s.periodEnd}</td>
                    <td>{moneyCents(s.closingBalanceCents || 0)}</td>
                    <td>{moneyCents(s.minimumDueCents || 0)}</td>
                    <td className="t-muted">{s.dueDate}</td>
                    <td>{moneyCents(s.paidCents || 0)}</td>
                    <td className={s.paidOnTime === false ? 't-bad' : s.paidOnTime ? 't-ok' : 't-muted'}>
                      {s.paidOnTime === true ? 'Yes' : s.paidOnTime === false ? 'No' : '—'}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </Card>

        <Card>
          <h3>Transactions</h3>
          <table>
            <thead>
              <tr>
                <th>Posted</th>
                <th>Type</th>
                <th>Amount</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((t, i) => (
                <tr key={i}>
                  <td className="t-muted">{t.postedAt}</td>
                  <td>
                    <span className="pill">{t.type}</span>
                  </td>
                  <td>{moneyCents(t.amountCents || 0)}</td>
                  <td>{t.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      </div>

      <Card>
        <h3>Simulate a month</h3>
        <div className="form">
          <div className="f">
            <label>Purchases ($)</label>
            <input type="number" value={purchases} onChange={(e) => setPurchases(e.target.value)} />
          </div>
          <div className="f">
            <label>Payment ($)</label>
            <input type="number" value={payment} onChange={(e) => setPayment(e.target.value)} />
          </div>
          <label className="chk full">
            <input type="checkbox" checked={payOnTime} onChange={(e) => setPayOnTime(e.target.checked)} /> Paid on time
          </label>
        </div>
        <div className="row" style={{ marginTop: 12 }}>
          <span className="sp" />
          <button className="btn p" onClick={simulateMonth}>
            Advance a month
          </button>
        </div>
        <p className="note">Demo tooling: advances this account's statement clock by one month.</p>
        {cliRequests.length > 0 && (
          <p className="note">
            Last CLI: {cliRequests[0].outcome} → {money(cliRequests[0].newLimit || 0)} by {cliRequests[0].createdBy} ·{' '}
            {fmtTs(cliRequests[0].createdAt)}
          </p>
        )}
      </Card>
    </>
  )
}
