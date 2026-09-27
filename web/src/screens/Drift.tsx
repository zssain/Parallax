import { useEffect, useRef, useState } from 'react'
import { PageHeader, Card } from '../ui/components'
import { useDriftLatest, useRunDrift, useSimulateDrift } from '../api/hooks'
import { useAuth } from '../app/AuthProvider'
import { useToast } from '../app/ToastProvider'
import { fmt, pct, psiCls } from '../ui/format'
import { ApiError } from '../api/client'
import type { DriftReport } from '../api/types'

const W = 760
const H = 220

export function Drift() {
  const { me } = useAuth()
  const { toast } = useToast()
  const { data: latest, isError } = useDriftLatest()
  const runDrift = useRunDrift()
  const simulate = useSimulateDrift()

  const [shift, setShift] = useState(0)
  const [sim, setSim] = useState<DriftReport | null>(null)
  const debounce = useRef<ReturnType<typeof setTimeout> | null>(null)

  const report = sim || latest
  const canRun = me?.role === 'STRATEGIST' || me?.role === 'APPROVER'

  useEffect(() => () => { if (debounce.current) clearTimeout(debounce.current) }, [])

  function onSlide(v: number) {
    setShift(v)
    if (debounce.current) clearTimeout(debounce.current)
    debounce.current = setTimeout(() => {
      simulate.mutate(v, { onSuccess: (r) => setSim(r) })
    }, 300)
  }

  const header = (chip?: React.ReactNode) => (
    <PageHeader
      title={
        <>
          Drift <em>monitor</em>
        </>
      }
      description="Compares today's applicant score distribution with the development baseline. A nightly job runs this and alerts above the threshold."
      right={
        <>
          {chip}
          {canRun && (
            <button
              className="btn p"
              onClick={() =>
                runDrift.mutate(undefined, {
                  onSuccess: () => { setSim(null); setShift(0); toast('Drift report refreshed.', 'ok') },
                  onError: (e) => toast(e instanceof ApiError ? e.detail || 'Could not run drift' : 'Could not run drift', 'bad'),
                })
              }
            >
              Run now
            </button>
          )}
        </>
      }
    />
  )

  if (isError && !report) {
    return (
      <>
        {header()}
        <Card>
          <div className="empty">No drift report yet. Run one now.</div>
        </Card>
      </>
    )
  }
  if (!report) return header()

  const bins = report.bins || []
  const mx = Math.max(...bins.flatMap((b) => [b.baseline || 0, b.current || 0]), 0.0001)
  const bw = W / (bins.length || 1)

  return (
    <>
      {header(<span className={`st ${report.status || ''}`}>PSI {(report.psi || 0).toFixed(3)}</span>)}
      <div className="bento" style={{ marginBottom: 14 }}>
        <div className="tile w s4">
          <div className="lbl">PSI · total</div>
          <div className={`big huge ${psiCls(report.status)}`}>{(report.psi || 0).toFixed(3)}</div>
          <small className="t-muted">
            {report.status} · thresholds 0.10 watch / 0.25 investigate
          </small>
        </div>
        <div className="tile s4">
          <div className="lbl">Simulate a market shift</div>
          <p className="t-muted" style={{ lineHeight: 1.6, margin: '12px 0 16px', fontSize: 13.5 }}>
            Push utilization and inquiries up across new applicants, like a tightening economy.
          </p>
          <input
            type="range"
            min={0}
            max={1.5}
            step={0.05}
            value={shift}
            style={{ width: '100%', accentColor: 'var(--gold)' }}
            onChange={(e) => onSlide(Number(e.target.value))}
          />
          <div className="row" style={{ justifyContent: 'space-between', fontSize: 12, marginTop: 6 }}>
            <span className="t-muted">none</span>
            <b className="mono">{shift.toFixed(2)}</b>
            <span className="t-muted">severe</span>
          </div>
          {sim && (
            <div className="row" style={{ marginTop: 10 }}>
              <span className="pill2 t-warn">SIMULATION — synthetic applicants, not stored</span>
              <span className="sp" />
              <button className="link" onClick={() => { setSim(null); setShift(0) }}>
                Reset to latest
              </button>
            </div>
          )}
        </div>
        <div className="tile s4">
          <div className="lbl">Samples</div>
          <div className="big" style={{ fontSize: 40 }}>
            {fmt(report.baselineN || 0)}{' '}
            <span className="t-muted" style={{ fontSize: 18 }}>
              baseline
            </span>
          </div>
          <div className="big" style={{ fontSize: 40, marginTop: 4 }}>
            {fmt(report.currentN || 0)}{' '}
            <span className="t-muted" style={{ fontSize: 18 }}>
              current
            </span>
          </div>
        </div>
      </div>

      <div className="g g21">
        <Card>
          <div className="th">
            <h3>Score distribution</h3>
            <span className="legend">
              <span>
                <b style={{ color: 'var(--line)' }}>■</b> baseline
              </span>
              <span>
                <b className="t-acc">■</b> current
              </span>
            </span>
          </div>
          <svg viewBox={`0 -10 ${W} ${H + 34}`} style={{ width: '100%' }}>
            {bins.map((b, i) => {
              const x = i * bw
              const h1 = ((b.baseline || 0) / mx) * H
              const h2 = ((b.current || 0) / mx) * H
              return (
                <g key={i}>
                  <rect x={x + bw * 0.14} y={H - h1} width={bw * 0.34} height={h1} fill="var(--line)" rx="2" />
                  <rect x={x + bw * 0.52} y={H - h2} width={bw * 0.34} height={h2} fill="var(--gold)" rx="2" />
                  <text x={x + bw / 2} y={H + 18} textAnchor="middle" fontSize="11" fill="var(--muted)" fontFamily="ui-monospace,monospace">
                    {b.from}–{(b.to || 0) > 850 ? 850 : b.to}
                  </text>
                </g>
              )
            })}
          </svg>
        </Card>

        <div className="tile">
          <div className="th">
            <h3>By band</h3>
          </div>
          <table>
            <thead>
              <tr>
                <th>Band</th>
                <th>Base</th>
                <th>Now</th>
                <th>PSI</th>
              </tr>
            </thead>
            <tbody>
              {bins.map((b, i) => (
                <tr key={i}>
                  <td className="mono">{b.from}+</td>
                  <td className="mono">{pct(b.baseline || 0, 1)}</td>
                  <td className="mono">{pct(b.current || 0, 1)}</td>
                  <td className={`mono ${(b.contribution || 0) > 0.02 ? 't-warn' : ''}`}>{(b.contribution || 0).toFixed(4)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  )
}
