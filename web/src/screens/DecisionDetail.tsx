import { useState, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { PageHeader, Card, OutcomePill } from '../ui/components'
import { useApplication, useReproduce, useAdverseActionNotice } from '../api/hooks'
import { useModal } from '../app/ModalProvider'
import { useToast } from '../app/ToastProvider'
import { cap, clamp, fmtTs, money } from '../ui/format'
import { FRAUD_CHECKS } from '../ui/reasons'
import { ApiError } from '../api/client'
import type { Detail, ReproduceView, ShadowBlock, TrailItem } from '../api/types'

function Gauge({ score, cut }: { score?: number; cut?: number }) {
  const f = score ? clamp((score - 300) / 550, 0, 1) : 0
  const c = cut ? clamp((cut - 300) / 550, 0, 1) : 0
  const a = Math.PI * (1 - c)
  return (
    <svg viewBox="0 0 220 125" style={{ width: '100%' }}>
      <path d="M20 110 A90 90 0 0 1 200 110" fill="none" stroke="var(--panel2)" strokeWidth="16" strokeLinecap="round" />
      <path
        d="M20 110 A90 90 0 0 1 200 110"
        fill="none"
        stroke="var(--acc)"
        strokeWidth="16"
        strokeLinecap="round"
        strokeDasharray={`${(283 * f).toFixed(1)} 400`}
      />
      <line
        x1={110 + 78 * Math.cos(a)}
        y1={110 - 78 * Math.sin(a)}
        x2={110 + 102 * Math.cos(a)}
        y2={110 - 102 * Math.sin(a)}
        stroke="var(--gold)"
        strokeWidth="3"
      />
    </svg>
  )
}

function renderNotice(text: string): ReactNode {
  const lines = text.split('\n').map((l) => l.trim()).filter(Boolean)
  if (!lines.length) return null
  const h4 = lines[0]
  const rest = lines.slice(1)
  const footerIdx = rest.findIndex((l) => l.startsWith('Template'))
  const footer = footerIdx >= 0 ? rest[footerIdx] : ''
  const body = footerIdx >= 0 ? rest.slice(0, footerIdx) : rest

  const out: ReactNode[] = []
  let ol: string[] = []
  const flush = () => {
    if (ol.length) {
      out.push(
        <ol key={`ol${out.length}`} style={{ margin: '12px 0 12px 22px' }}>
          {ol.map((li, i) => (
            <li key={i}>{li}</li>
          ))}
        </ol>,
      )
      ol = []
    }
  }
  body.forEach((l) => {
    const m = l.match(/^\d+\.\s+(.*)/)
    if (m) ol.push(m[1])
    else {
      flush()
      out.push(<p key={`p${out.length}`}>{l}</p>)
    }
  })
  flush()
  return (
    <div className="notice">
      <h4>{h4}</h4>
      {out}
      {footer && <span className="mono" style={{ fontSize: 11 }}>{footer}</span>}
    </div>
  )
}

function LedgerTrail({ trail, shadow }: { trail: TrailItem[]; shadow?: ShadowBlock | null }) {
  return (
    <Card>
      <h3>Ledger trail</h3>
      <div className="tl">
        {trail.map((r) => (
          <div className="tli" key={r.seq}>
            <b>
              #{r.seq} · {r.kind} · <OutcomePill outcome={r.outcome} />
            </b>
            <div>
              {fmtTs(r.createdAt)} · {r.ruleVersion || '—'}
              {r.override && ` · ${r.override.by}: ${r.override.codeDescription} — “${r.override.note}”`}
            </div>
            <div className="hash">
              prev {r.prevHash} → hash {r.hash}
            </div>
          </div>
        ))}
      </div>
      {shadow && (
        <>
          <div className="lbl" style={{ margin: '10px 0 8px' }}>
            Shadow evaluation · {shadow.version}
          </div>
          <div className="chkrow">
            <span>Candidate outcome</span>
            <span>
              <OutcomePill outcome={shadow.outcome} /> · {shadow.score}
              {shadow.creditLimit ? ' · ' + money(shadow.creditLimit) : ''}
            </span>
          </div>
          <div className="chkrow">
            <span>Agrees with live</span>
            <b className={shadow.agrees ? 't-ok' : 't-warn'}>{shadow.agrees ? 'Yes' : 'No — logged as disagreement'}</b>
          </div>
        </>
      )}
    </Card>
  )
}

export function DecisionDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { openModal, closeModal } = useModal()
  const { toast } = useToast()
  const reproduce = useReproduce()
  const notice = useAdverseActionNotice()
  const [repro, setRepro] = useState<ReproduceView | null>(null)

  const { data } = useApplication(id, { pollWhilePending: true })

  const header = (actions?: ReactNode) => (
    <PageHeader
      eyebrow="Decision detail"
      title={id || 'Decision'}
      description="How the engine reached this outcome — policy checks, scorecard points, ranked reason codes — and the ledger evidence that proves it."
      right={actions}
    />
  )

  if (!data) return header(<button className="btn" onClick={() => navigate('/app/decisions')}>← Back</button>)

  const d: Detail = data
  const base = d.base
  const current = d.current

  if (d.status === 'ENGINE_PENDING' && !base) {
    return (
      <>
        {header(<button className="btn" onClick={() => navigate('/app/decisions')}>← Back</button>)}
        <Card>
          <h3>Waiting for the decision engine</h3>
          <p className="t-muted" style={{ lineHeight: 1.6 }}>
            The decision engine was unavailable after retries, so this application is queued for the engine retry job. This
            page polls every 3 seconds and updates as soon as a decision lands.
          </p>
        </Card>
      </>
    )
  }

  const question = `Why was ${id} ${
    base?.outcome === 'APPROVED' ? 'approved' : base?.outcome === 'REFER' ? 'referred' : 'declined'
  }?`
  const declined = base?.outcome === 'DECLINED' || current?.outcome === 'DECLINED'
  const openRefer = current?.outcome === 'REFER'

  const actions = (
    <>
      <button className="btn" onClick={() => navigate('/app/decisions')}>
        ← Back
      </button>
      {declined && (
        <button
          className="btn"
          onClick={() =>
            notice.mutate(id!, {
              onSuccess: (text) =>
                openModal(
                  <>
                    <h3>Adverse action notice</h3>
                    <p className="t-muted" style={{ marginBottom: 16 }}>
                      Rendered from a deterministic template with the ledger's reason codes. No AI involved.
                    </p>
                    {renderNotice(text)}
                    <div className="row" style={{ marginTop: 18 }}>
                      <span className="sp" />
                      <button className="btn p" onClick={closeModal}>
                        Close
                      </button>
                    </div>
                  </>,
                ),
              onError: (e) => toast(e instanceof ApiError ? e.detail || 'No notice available' : 'No notice available', 'bad'),
            })
          }
        >
          Adverse action notice
        </button>
      )}
      {openRefer && (
        <button className="btn" onClick={() => navigate(`/app/queue?app=${id}`)}>
          Review in queue
        </button>
      )}
      <button className="btn p" onClick={() => navigate(`/app/assistant?ask=${encodeURIComponent(question)}`)}>
        ✦ Ask assistant
      </button>
    </>
  )

  const banner = (
    <div className={`banner ${current?.outcome}`}>
      <div>
        <div className="lbl" style={{ color: '#fff', opacity: 0.8 }}>
          {d.applicationId} · {d.product}
        </div>
        <h2>{cap(current?.outcome || '')}</h2>
        <div className="sub">
          {d.displayName} · decided under {base?.ruleVersion} · ledger seq #{base?.seq}
          {current?.kind === 'OVERRIDE' && current.override && ` · overridden by ${current.override.by} (${current.override.code})`}
          {current?.kind === 'REDECISION' && ' · automatically re-decided after bureau recovery'}
        </div>
      </div>
      <div>
        <div className="sub" style={{ textAlign: 'right' }}>
          {current?.outcome === 'APPROVED' ? 'Credit limit' : 'Score'}
        </div>
        <div className="big">{current?.outcome === 'APPROVED' ? money(current.creditLimit || 0) : (base?.score ?? '—')}</div>
      </div>
    </div>
  )

  // Bureau-unavailable variant (B01): no engine evaluation, only the fallback card + ledger trail.
  const isBureauUnavailable = !d.bureau && (base?.reasonCodes || []).some((r) => r.code === 'B01')
  if (isBureauUnavailable) {
    return (
      <>
        {header(actions)}
        {banner}
        <Card>
          <h3>Bureau unavailable</h3>
          <p className="t-muted" style={{ lineHeight: 1.6 }}>
            The credit bureau circuit breaker was OPEN when this application arrived, so the pipeline fell back to REFER
            with internal reason B01 instead of failing. It is queued for automatic re-decision when the circuit closes
            (System → Restore bureau).
          </p>
          <div className="rc b" style={{ marginTop: 14 }}>
            <code>B01</code>Credit report temporarily unavailable (internal)
          </div>
        </Card>
        <LedgerTrail trail={d.trail || []} shadow={d.shadow as ShadowBlock | null} />
      </>
    )
  }

  const bd = d.breakdown
  const parts = bd?.scoreParts || []
  const applicantReasons = (base?.reasonCodes || []).filter((r) => r.applicantFacing)
  const fraudFlags = base?.fraudFlags || []
  const pointsSum = parts.reduce((s, p) => s + (p.points || 0), 0)

  function runReproduce() {
    if (base?.seq == null) return
    reproduce.mutate(base.seq, {
      onSuccess: (r) => {
        setRepro(r)
        toast(r.identical ? 'Reproduced: identical result ✓' : 'Reproduce mismatch ✗', r.identical ? 'ok' : 'bad')
      },
    })
  }

  return (
    <>
      {header(actions)}
      {banner}

      <div className="g g3">
        <Card>
          <h3>
            Score <span className="lbl">{base?.ruleVersion}</span>
          </h3>
          <div className="gauge">
            <Gauge score={base?.score} cut={bd?.approveCutoff} />
            <div className="n">{base?.score}</div>
          </div>
          <p className="note" style={{ textAlign: 'center' }}>
            300–850 · approve ≥ {bd?.approveCutoff} (gold tick) · refer ≥ {bd?.referCutoff}
          </p>
          {base?.outcome === 'APPROVED' && (
            <>
              <div className="chkrow">
                <span>Score-band limit</span>
                <b>{money(bd?.bandLimit || 0)}</b>
              </div>
              <div className="chkrow">
                <span>Ability-to-pay max</span>
                <b>{money(base.atpMax || 0)}</b>
              </div>
              <div className="chkrow">
                <span>Assigned (lower of the two)</span>
                <b className="t-ok">{money(base.creditLimit || 0)}</b>
              </div>
            </>
          )}
        </Card>

        <Card>
          <h3>Reason codes</h3>
          {applicantReasons.length ? (
            applicantReasons.map((r) => (
              <div className="rc" key={r.code}>
                <code>{r.code}</code>
                {r.description}
              </div>
            ))
          ) : (
            <p className="t-muted">{base?.outcome === 'APPROVED' ? 'Approved — no adverse reasons.' : 'No applicant-facing reasons.'}</p>
          )}
          {fraudFlags.length > 0 && (
            <>
              <div className="lbl" style={{ margin: '16px 0 8px' }}>
                Internal fraud flags · never shown to applicant
              </div>
              {fraudFlags.map((f) => (
                <div className="rc f" key={f.code}>
                  <code>{f.code}</code>
                  {f.description}
                </div>
              ))}
            </>
          )}
          <p className="note">Policy failures first, then scorecard attributes ranked by points lost versus the maximum. Capped at 4.</p>
        </Card>

        <Card>
          <h3>Policy & identity checks</h3>
          {(bd?.policyChecks || []).map((c) => (
            <div className="chkrow" key={c.code}>
              <span>
                {c.name}
                <br />
                <small className="t-muted">{c.detail}</small>
              </span>
              <span className={c.passed ? 'pass' : 'fail'}>{c.passed ? 'PASS' : 'FAIL'}</span>
            </div>
          ))}
          {FRAUD_CHECKS.map(([code, label]) => {
            const flagged = fraudFlags.some((f) => f.code === code)
            return (
              <div className="chkrow" key={code}>
                <span>{label}</span>
                <span className={flagged ? 'fail' : 'pass'}>{flagged ? 'FLAG' : 'CLEAR'}</span>
              </div>
            )
          })}
        </Card>
      </div>

      <div className="g g21">
        <Card>
          <h3>
            Scorecard breakdown <span className="lbl">how reasons are ranked</span>
          </h3>
          <table>
            <thead>
              <tr>
                <th>Attribute</th>
                <th>Value</th>
                <th>Band</th>
                <th>Points</th>
                <th>Lost</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {parts.map((p) => {
                const lost = (p.maxPoints || 0) - (p.points || 0)
                return (
                  <tr key={p.code}>
                    <td>{p.attribute}</td>
                    <td>{p.value}</td>
                    <td>{p.band}</td>
                    <td>
                      <b>{p.points}</b> / {p.maxPoints}
                    </td>
                    <td className={lost > 0 ? 't-bad' : ''}>{lost ? '−' + lost : '0'}</td>
                    <td>
                      <div className="mbar">
                        <div style={{ width: `${((p.points || 0) / (p.maxPoints || 1)) * 100}%` }} />
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          <p className="note">
            300 base + {pointsSum} points = <b>{base?.score}</b>. Age is not a scoring factor.
          </p>
        </Card>

        <Card>
          <h3>
            Engine input snapshot{' '}
            <button className="btn sm p" onClick={runReproduce}>
              Reproduce ↻
            </button>
          </h3>
          <div className="note" style={{ margin: '0 0 10px' }}>
            The exact normalized input stored in the ledger. No identity fields.
          </div>
          <div className="code">{JSON.stringify(base?.engineInput, null, 2)}</div>
          {repro && (
            <div
              className={`rc ${repro.identical ? 'b' : ''}`}
              style={{ marginTop: 14, borderLeftColor: repro.identical ? 'var(--ok)' : 'var(--bad)' }}
            >
              <code style={{ color: repro.identical ? 'var(--ok)' : 'var(--bad)' }}>{repro.identical ? '✓' : '✗'}</code>
              <span>
                GET /decisions/{base?.seq}/reproduce → re-ran stored input under <b>{repro.ruleVersion}</b>:{' '}
                {repro.identical ? (
                  <>
                    <b>identical</b> outcome, score, limit and reason codes.
                  </>
                ) : (
                  <>
                    <b>MISMATCH</b> — investigate engine version skew.
                  </>
                )}
              </span>
            </div>
          )}
        </Card>
      </div>

      <div className="g g2">
        <Card>
          <h3>
            Bureau pull <span className="pill">{d.bureau?.pullType}</span>
          </h3>
          <div className="chkrow">
            <span>Pull ID</span>
            <b className="mono">{d.bureau?.pullId}</b>
          </div>
          <div className="chkrow">
            <span>Report reuse</span>
            <b>{d.bureau?.reused ? 'Reused (within 30-day window)' : 'Fresh pull'}</b>
          </div>
          <div className="chkrow">
            <span>SSN at rest</span>
            <b className="mono">{d.ssnEncPreview}</b>
          </div>
          <div className="lbl" style={{ margin: '14px 0 8px' }}>
            SOAP response → mapped to JSON
          </div>
          <div className="code">{d.bureau?.rawXml}</div>
        </Card>

        <LedgerTrail trail={d.trail || []} shadow={d.shadow as ShadowBlock | null} />
      </div>
    </>
  )
}
