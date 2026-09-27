import { useNavigate } from 'react-router-dom'
import { PageHeader, DecisionTable } from '../ui/components'
import { useOverview } from '../api/hooks'
import { useAuth } from '../app/AuthProvider'
import { fmtMonth, pct, psiCls } from '../ui/format'
import type { Attention } from '../api/types'

const W = 520
const H = 100

function attTarget(target?: string): string {
  if (!target) return '/app'
  if (target.startsWith('lab:')) return `/app/lab?v=${target.slice(4)}`
  return `/app/${target}`
}

// Attention rows carry a severity + target but no short label; derive one for the .ac card heading.
function attLabel(a: Attention): string {
  const t = (a.target || '').split(':')[0]
  const map: Record<string, string> = {
    queue: 'QUEUE',
    lab: 'APPROVAL',
    system: 'SYSTEM',
    drift: 'DRIFT',
    shadow: 'SHADOW',
  }
  return map[t] || (a.severity || 'NOTE').toUpperCase()
}

export function Overview() {
  const navigate = useNavigate()
  const { me } = useAuth()
  const { data, isLoading } = useOverview()

  const firstName = (me?.displayName || '').split(' ')[0]
  const hr = new Date().getHours()
  const greet = hr < 12 ? 'morning' : hr < 17 ? 'afternoon' : 'evening'

  const header = (
    <PageHeader
      title={
        <>
          Good {greet}, <em>{firstName}.</em>
        </>
      }
      description="Everything on this page is read straight from the decision ledger."
      right={
        <button className="btn p" onClick={() => navigate('/app/apply')}>
          + New application
        </button>
      }
    />
  )
  if (isLoading || !data) return header

  const n = data.decisions ?? 0
  const ap = data.approved ?? 0
  const rf = data.refer ?? 0
  const dc = data.declined ?? 0
  const q = data.reviewQueue ?? 0
  const psi = data.psi
  const lv = data.liveVersion
  const attention = (data.attention ?? []) as Attention[]

  // Trend polyline + filled area over the 12 months, skipping any null-rate months.
  const trend = data.approvalTrend ?? []
  const pts = trend.map((p, i) => ({ i, rate: p.rate })).filter((p) => p.rate != null) as { i: number; rate: number }[]
  const rates = pts.map((p) => p.rate)
  const mn = Math.min(...rates) - 0.02
  const mx = Math.max(...rates) + 0.02
  const denom = trend.length > 1 ? trend.length - 1 : 1
  const xy = (i: number, rate: number) => ({ x: (i / denom) * W, y: H - ((rate - mn) / (mx - mn)) * H })
  const P = pts.map((p) => xy(p.i, p.rate))
  const line = P.map((c) => `${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ')
  const area = P.length ? `M${P[0].x.toFixed(1)},${H} L${line.split(' ').join(' L')} L${P[P.length - 1].x.toFixed(1)},${H} Z` : ''
  const firstLabel = trend.length ? fmtMonth(trend[0].month!) : ''
  const last = trend.length ? trend[trend.length - 1] : undefined
  const lastLabel = last ? `${fmtMonth(last.month!)} · ${last.rate != null ? pct(last.rate, 1) : '—'}` : ''

  return (
    <>
      {header}
      <div className="bento" style={{ marginBottom: 26 }}>
        <div className="tile w s5 r2">
          <div className="th">
            <h3>Approval rate</h3>
            <span className="lbl">current</span>
          </div>
          <div className="big huge">{pct(data.approvalRate ?? 0, 1)}</div>
          <div className="t-muted">
            {ap} of {n} applications approved
          </div>
          <div className="stack">
            <div style={{ width: `${n ? (ap / n) * 100 : 0}%`, background: 'var(--moss)' }} />
            <div style={{ width: `${n ? (rf / n) * 100 : 0}%`, background: 'var(--ochre)' }} />
            <div style={{ width: `${n ? (dc / n) * 100 : 0}%`, background: 'var(--rust)' }} />
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
          <div className="lbl" style={{ marginTop: 26 }}>
            Approval rate · last 12 months
          </div>
          <svg viewBox={`-4 -10 ${W + 8} ${H + 30}`} style={{ width: '100%', height: 140, marginTop: 8 }}>
            {area && <path d={area} fill="var(--goldbg)" />}
            <polyline points={line} fill="none" stroke="var(--gold)" strokeWidth="2" />
            {P.map((c, i) => (
              <circle key={i} cx={c.x} cy={c.y} r="2.6" fill="var(--card)" stroke="var(--gold)" strokeWidth="1.5" />
            ))}
            <text x="0" y={H + 18} fontSize="10" fill="var(--muted)" fontFamily="ui-monospace,monospace">
              {firstLabel}
            </text>
            <text x={W} y={H + 18} fontSize="10" fill="var(--muted)" textAnchor="end" fontFamily="ui-monospace,monospace">
              {lastLabel}
            </text>
          </svg>
        </div>
        <div className="tile s3 cl" onClick={() => navigate('/app/decisions')}>
          <div className="lbl">Decisions</div>
          <div className="big">{n}</div>
          <small className="t-muted">in the ledger →</small>
        </div>
        <div className="tile s4 cl" onClick={() => navigate('/app/queue')}>
          <div className="lbl">Review queue</div>
          <div className={`big ${q ? 't-warn' : ''}`}>{q}</div>
          <small className="t-muted">open refers →</small>
        </div>
        <div className="tile s3 cl" onClick={() => navigate('/app/lab')}>
          <div className="lbl">Live rules</div>
          <div className="big">{lv?.version || '—'}</div>
          <small className="t-muted">since {lv?.since || '—'}</small>
        </div>
        <div className="tile s4 cl" onClick={() => navigate('/app/drift')}>
          <div className="lbl">Score drift · PSI</div>
          <div className={`big ${psiCls(psi?.status)}`}>{psi?.value != null ? psi.value.toFixed(3) : '—'}</div>
          <small className="t-muted">{psi?.status || '—'} →</small>
        </div>
      </div>

      <div className="th">
        <h3>Needs attention</h3>
        <span className="lbl">{attention.length} items</span>
      </div>
      <div className="attn">
        {attention.map((a, i) => (
          <div key={i} className={`ac ${a.severity || ''}`} onClick={() => navigate(attTarget(a.target))}>
            <small>{attLabel(a)}</small>
            {a.message}
          </div>
        ))}
        {!attention.length && <div className="empty">Nothing needs attention.</div>}
      </div>

      <div className="tile w">
        <div className="th">
          <h3>Recent decisions</h3>
          <button className="link" onClick={() => navigate('/app/decisions')}>
            All decisions →
          </button>
        </div>
        <DecisionTable rows={data.recent ?? []} />
      </div>
    </>
  )
}
