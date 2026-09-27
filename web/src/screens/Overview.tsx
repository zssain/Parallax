import { useNavigate } from 'react-router-dom'
import { PageHeader, LiveChips, Kpi, Card, DecisionTable } from '../ui/components'
import { useOverview } from '../api/hooks'
import { fmtMonth, pct, psiCls } from '../ui/format'
import type { Attention } from '../api/types'

const W = 520
const H = 120

function attTarget(target?: string): string {
  if (!target) return '/app'
  if (target.startsWith('lab:')) return `/app/lab?v=${target.slice(4)}`
  return `/app/${target}`
}

export function Overview() {
  const navigate = useNavigate()
  const { data, isLoading } = useOverview()

  const header = (
    <PageHeader
      eyebrow="Overview"
      title="Portfolio at a glance"
      description="Live decisions, the rules behind them and what needs attention. Every figure here is read from the decision ledger."
      right={<LiveChips />}
    />
  )
  if (isLoading || !data) return header

  const n = data.decisions ?? 0
  const ap = data.approved ?? 0
  const rf = data.refer ?? 0
  const dc = data.declined ?? 0
  const q = data.reviewQueue ?? 0
  const psi = data.psi
  const attention = (data.attention ?? []) as Attention[]

  // Trend polyline over the 12 months, skipping any null-rate months.
  const trend = data.approvalTrend ?? []
  const pts = trend.map((p, i) => ({ i, rate: p.rate })).filter((p) => p.rate != null) as { i: number; rate: number }[]
  const rates = pts.map((p) => p.rate)
  const mn = Math.min(...rates) - 0.02
  const mx = Math.max(...rates) + 0.02
  const denom = trend.length > 1 ? trend.length - 1 : 1
  const xy = (i: number, rate: number) => ({
    x: (i / denom) * W,
    y: H - ((rate - mn) / (mx - mn)) * H,
  })
  const polyline = pts.map((p) => { const c = xy(p.i, p.rate); return `${c.x.toFixed(1)},${c.y.toFixed(1)}` }).join(' ')
  const firstLabel = trend.length ? fmtMonth(trend[0].month!) : ''
  const last = trend.length ? trend[trend.length - 1] : undefined
  const lastLabel = last ? `${fmtMonth(last.month!)} · ${last.rate != null ? pct(last.rate, 1) : '—'}` : ''

  return (
    <>
      {header}
      <div className="kpis">
        <Kpi label="Decisions" value={n} sub="applications in ledger" />
        <Kpi label="Approval rate" value={pct(data.approvalRate ?? 0, 1)} sub={`${ap} approved`} />
        <Kpi label="Review queue" value={q} sub="open REFERs" cls={q > 0 ? 't-warn' : ''} />
        <Kpi label="Live rules" value={data.liveVersion?.version || '—'} sub={`since ${data.liveVersion?.since || '—'}`} cls="t-acc" />
        <Kpi
          label="Score drift (PSI)"
          value={psi?.value != null ? psi.value.toFixed(3) : '—'}
          sub={psi?.status || '—'}
          cls={psiCls(psi?.status)}
        />
      </div>

      <div className="g g12">
        <Card>
          <h3>Needs attention</h3>
          {attention.map((a, i) => (
            <div key={i} className="att" onClick={() => navigate(attTarget(a.target))}>
              <i className={a.severity} />
              <b>{a.message}</b>
              <span className="t-muted">→</span>
            </div>
          ))}
          {!attention.length && <div className="empty">Nothing needs attention.</div>}
        </Card>

        <Card>
          <h3>
            Outcome mix <span className="lbl">current decisions</span>
          </h3>
          <div className="stack">
            <div style={{ width: `${n ? (ap / n) * 100 : 0}%`, background: 'var(--ok)' }} />
            <div style={{ width: `${n ? (rf / n) * 100 : 0}%`, background: 'var(--warn)' }} />
            <div style={{ width: `${n ? (dc / n) * 100 : 0}%`, background: 'var(--bad)' }} />
          </div>
          <div className="legend">
            <span>
              <b className="t-ok">{ap}</b> approved
            </span>
            <span>
              <b className="t-warn">{rf}</b> refer
            </span>
            <span>
              <b className="t-bad">{dc}</b> declined
            </span>
          </div>
          <div style={{ marginTop: 26 }} className="lbl">
            Approval rate · last 12 months
          </div>
          <svg viewBox={`-4 -8 ${W + 8} ${H + 26}`} style={{ width: '100%', height: 150, marginTop: 10 }}>
            <polyline points={polyline} fill="none" stroke="var(--acc)" strokeWidth="2.5" />
            {pts.map((p) => {
              const c = xy(p.i, p.rate)
              return <circle key={p.i} cx={c.x} cy={c.y} r="3" fill="var(--acc)" />
            })}
            <text x="0" y={H + 18} fontSize="11" fill="var(--muted)">
              {firstLabel}
            </text>
            <text x={W} y={H + 18} fontSize="11" fill="var(--muted)" textAnchor="end">
              {lastLabel}
            </text>
          </svg>
        </Card>
      </div>

      <Card>
        <h3>
          Recent decisions{' '}
          <button className="link" onClick={() => navigate('/app/decisions')}>
            View all →
          </button>
        </h3>
        <DecisionTable rows={data.recent ?? []} />
      </Card>
    </>
  )
}
