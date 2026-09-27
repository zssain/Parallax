import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { fmtTs, money } from './format'
import { useSystemStatus } from '../api/hooks'
import type { ListItem } from '../api/types'

export function Kpi({ label, value, sub, cls }: { label: string; value: ReactNode; sub?: ReactNode; cls?: string }) {
  return (
    <div className="kpi">
      <div className="lbl">{label}</div>
      <div className={`v ${cls || ''}`}>{value}</div>
      {sub != null && <small>{sub}</small>}
    </div>
  )
}

export function OutcomePill({ outcome }: { outcome?: string | null }) {
  if (!outcome) return <>—</>
  return <span className={`oc ${outcome}`}>{outcome}</span>
}

export function StatusChip({ status }: { status?: string }) {
  return <span className={`st ${status || ''}`}>{status}</span>
}

export function Card({ children, className, style }: { children: ReactNode; className?: string; style?: React.CSSProperties }) {
  return (
    <div className={`card ${className || ''}`} style={style}>
      {children}
    </div>
  )
}

/** PageHeader = the prototype's head(eyebrow, title, description, right). */
export function PageHeader({
  eyebrow,
  title,
  description,
  right,
}: {
  eyebrow: string
  title: ReactNode
  description: string
  right?: ReactNode
}) {
  return (
    <section className="phead">
      <div>
        <div className="eyebrow">
          <i />
          {eyebrow}
        </div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      <div className="phead-r">{right}</div>
    </section>
  )
}

/** "Live data" / "Bureau outage" dot + "Rules vX" chip, from GET /system/status (refetched 15s). */
export function LiveChips() {
  const { data } = useSystemStatus({ refetchInterval: 15000 })
  const down = data?.bureauCircuit === 'OPEN'
  return (
    <>
      <span className={`dotl ${down ? 'down' : ''}`}>{down ? 'Bureau outage' : 'Live data'}</span>
      <span className="chipbox">Rules {data?.liveVersion || '—'}</span>
    </>
  )
}

/** The prototype's decTable columns. Rows open the decision detail. */
export function DecisionTable({ rows }: { rows: ListItem[] }) {
  const navigate = useNavigate()
  if (!rows.length) return <div className="empty">No decisions match.</div>
  return (
    <table>
      <thead>
        <tr>
          <th>Application</th>
          <th>Applicant</th>
          <th>Product</th>
          <th>Score</th>
          <th>Outcome</th>
          <th>Limit</th>
          <th>Rules</th>
          <th>Recorded</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.applicationId} className="cl" onClick={() => navigate(`/app/decisions/${r.applicationId}`)}>
            <td className="mono">
              <b>{r.applicationId}</b>
            </td>
            <td>{r.displayName}</td>
            <td>{r.product}</td>
            <td>{r.score ?? '—'}</td>
            <td>
              <OutcomePill outcome={r.outcome} />
              {r.currentKind === 'OVERRIDE' && <span className="pill"> override</span>}
              {r.currentKind === 'REDECISION' && <span className="pill"> re-decided</span>}
            </td>
            <td>{r.creditLimit ? money(r.creditLimit) : '—'}</td>
            <td className="mono">{r.ruleVersion}</td>
            <td className="t-muted">{fmtTs(r.recordedAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
