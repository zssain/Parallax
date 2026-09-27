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
      eyebrow="Drift monitor"
      title="Population Stability Index"
      description="Compares the current applicant score distribution with the development baseline. A scheduled job runs this nightly and alerts above the threshold."
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
      {header(<span className={`chipbox ${psiCls(report.status)}`}>PSI {(report.psi || 0).toFixed(3)} · {report.status}</span>)}
      <div className="kpis k4">
        <div className="kpi">
          <div className="lbl">PSI (total)</div>
          <div className={`v ${psiCls(report.status)}`}>{(report.psi || 0).toFixed(3)}</div>
          <small>{report.status}</small>
        </div>
        <div className="kpi">
          <div className="lbl">Baseline</div>
          <div className="v">{fmt(report.baselineN || 0)}</div>
          <small>development sample</small>
        </div>
        <div className="kpi">
          <div className="lbl">Current</div>
          <div className="v">{fmt(report.currentN || 0)}</div>
          <small>recent applicants</small>
        </div>
        <div className="kpi">
          <div className="lbl">Thresholds</div>
          <div className="v">0.10 / 0.25</div>
          <small>watch / investigate</small>
        </div>
      </div>

      <div className="g g21">
        <Card>
          <h3>
            Score distribution{' '}
            <span className="legend">
              <span>
                <b style={{ color: 'var(--muted)' }}>■</b> baseline
              </span>
              <span>
                <b className="t-acc">■</b> current
              </span>
            </span>
          </h3>
          <svg viewBox={`0 -10 ${W} ${H + 34}`} style={{ width: '100%' }}>
            {bins.map((b, i) => {
              const x = i * bw
              const h1 = ((b.baseline || 0) / mx) * H
              const h2 = ((b.current || 0) / mx) * H
              return (
                <g key={i}>
                  <rect x={x + bw * 0.14} y={H - h1} width={bw * 0.34} height={h1} fill="var(--line)" rx="3" />
                  <rect x={x + bw * 0.52} y={H - h2} width={bw * 0.34} height={h2} fill="var(--acc)" rx="3" />
                  <text x={x + bw / 2} y={H + 18} textAnchor="middle" fontSize="11" fill="var(--muted)">
                    {b.from}–{(b.to || 0) > 850 ? 850 : b.to}
                  </text>
                </g>
              )
            })}
          </svg>
        </Card>

        <Card>
          <h3>Simulate a market shift</h3>
          <p className="t-muted" style={{ lineHeight: 1.6, marginBottom: 14 }}>
            Drag to push utilization and inquiries up across new applicants, like a tightening economy. PSI recomputes on
            5,000 fresh synthetic applicants.
          </p>
          <input type="range" min={0} max={1.5} step={0.05} value={shift} style={{ width: '100%' }} onChange={(e) => onSlide(Number(e.target.value))} />
          <div className="row" style={{ justifyContent: 'space-between', fontSize: 12 }}>
            <span className="t-muted">none</span>
            <b>{shift.toFixed(2)}</b>
            <span className="t-muted">severe</span>
          </div>
          {sim && (
            <div className="row" style={{ marginTop: 10 }}>
              <span className="pill" style={{ background: 'var(--warnbg)', color: 'var(--warn)' }}>
                SIMULATION — synthetic applicants, not stored
              </span>
              <span className="sp" />
              <button className="link" onClick={() => { setSim(null); setShift(0) }}>
                Reset to latest
              </button>
            </div>
          )}
          <table style={{ marginTop: 16 }}>
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
                  <td>{b.from}+</td>
                  <td>{pct(b.baseline || 0, 1)}</td>
                  <td>{pct(b.current || 0, 1)}</td>
                  <td className={`mono ${(b.contribution || 0) > 0.02 ? 't-warn' : ''}`}>{(b.contribution || 0).toFixed(4)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      </div>
    </>
  )
}
