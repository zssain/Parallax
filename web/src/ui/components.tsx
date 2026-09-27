import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { fmtTs, money } from './format'
import { useSystemStatus } from '../api/hooks'
import type { ListItem } from '../api/types'

// The prototype's OCM: outcome → [glyph, label]. `oc(outcome)` renders the icon-chip.
export const OCM: Record<string, [string, string]> = {
  APPROVED: ['✓', 'Approved'],
  REFER: ['◐', 'Refer'],
  DECLINED: ['✕', 'Declined'],
}

/** The prototype's kpi(): a tile with an uppercase label, a big number and a muted sub-line. */
export function Kpi({ label, value, sub, cls }: { label: string; value: ReactNode; sub?: ReactNode; cls?: string }) {
  return (
    <div className="tile">
      <div className="lbl">{label}</div>
      <div className={`big ${cls || ''}`} style={{ fontSize: 40 }}>
        {value}
      </div>
      {sub != null && <small className="t-muted">{sub}</small>}
    </div>
  )
}

/** The prototype's oc(): an outcome chip with a coloured glyph. */
export function OutcomePill({ outcome }: { outcome?: string | null }) {
  if (!outcome || !OCM[outcome]) return <>—</>
  return (
    <span className={`oc ${outcome}`}>
      <i>{OCM[outcome][0]}</i>
      {OCM[outcome][1]}
    </span>
  )
}

export function StatusChip({ status }: { status?: string }) {
  return <span className={`st ${status || ''}`}>{status}</span>
}

/** A tile card (the prototype's `.tile`; add `w` for the paper-white variant). */
export function Card({ children, className, style }: { children: ReactNode; className?: string; style?: React.CSSProperties }) {
  return (
    <div className={`tile w ${className || ''}`} style={style}>
      {children}
    </div>
  )
}

/** PageHeader = the prototype's head(title, description, right). Title may include <em>. */
export function PageHeader({
  title,
  description,
  right,
}: {
  title: ReactNode
  description: string
  right?: ReactNode
}) {
  return (
    <section className="ph">
      <div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      <div className="ph-r">{right}</div>
    </section>
  )
}

/** The top-bar bureau + rules chips, from GET /system/status (refetched 15s). */
export function LiveChips() {
  const { data } = useSystemStatus({ refetchInterval: 15000 })
  const down = data?.bureauCircuit === 'OPEN'
  return (
    <>
      <span className={`chip ${down ? 'down' : ''}`}>
        <i />
        {down ? 'Bureau down' : 'Bureau live'}
      </span>
      <span className="chip">
        <b>Rules</b> {data?.liveVersion || '—'}
      </span>
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
            <td className="t-muted">{r.product}</td>
            <td className="mono">{r.score ?? '—'}</td>
            <td>
              <OutcomePill outcome={r.outcome} />
              {r.currentKind === 'OVERRIDE' && <span className="pill2"> override</span>}
              {r.currentKind === 'REDECISION' && <span className="pill2"> re-decided</span>}
            </td>
            <td className="mono">{r.creditLimit ? money(r.creditLimit) : '—'}</td>
            <td className="mono">{r.ruleVersion}</td>
            <td className="t-muted mono">{fmtTs(r.recordedAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
