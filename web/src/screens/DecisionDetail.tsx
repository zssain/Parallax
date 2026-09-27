import { useState, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { PageHeader, OutcomePill, OCM } from '../ui/components'
import { useApplication, useReproduce, useAdverseActionNotice } from '../api/hooks'
import { useModal } from '../app/ModalProvider'
import { useToast } from '../app/ToastProvider'
import { clamp, fmtTs, money } from '../ui/format'
import { FRAUD_CHECKS } from '../ui/reasons'
import { ApiError } from '../api/client'
import type { Detail, ReproduceView, ShadowBlock, TrailItem } from '../api/types'

// The prototype's scoreRuler(score, cfg): a 300–850 track with refer/approve zones and a score pin.
function ScoreRuler({ score, refer, approve }: { score?: number; refer?: number; approve?: number }) {
  const p = (s: number) => clamp((s - 300) / 550, 0, 1) * 100
  const r = refer != null ? p(refer) : 0
  const a = approve != null ? p(approve) : 0
  return (
    <div className="sruler">
      <div className="sr-track">
        <div className="sr-z">
          <span style={{ width: `${r}%`, background: '#f0957c88' }} />
          <span style={{ width: `${a - r}%`, background: '#e8b86a88' }} />
          <span style={{ flex: 1, background: '#b7cf9788' }} />
        </div>
        {score ? (
          <i className="sr-pin" style={{ left: `${p(score)}%` }}>
            <b>{score}</b>
          </i>
        ) : null}
      </div>
      <div className="sr-ax">
        <span style={{ left: 0, transform: 'none' }}>300</span>
        <span style={{ left: `${r}%` }}>{refer}</span>
        <span style={{ left: `${a}%` }}>{approve}</span>
        <span style={{ right: 0, transform: 'none' }}>850</span>
      </div>
    </div>
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
    <div className="tile w">
      <div className="th">
        <h3>Ledger trail</h3>
        <span className="lbl">
          {trail.length} record{trail.length > 1 ? 's' : ''}
        </span>
      </div>
      <div className="tl">
        {trail.map((r) => (
          <div className="tli" key={r.seq}>
            <b>
              #{r.seq} · {r.kind} <OutcomePill outcome={r.outcome} />
            </b>
            <div>
              {fmtTs(r.createdAt)} · {r.ruleVersion || '—'}
              {r.override && ` · ${r.override.by}: ${r.override.codeDescription} — “${r.override.note}”`}
            </div>
            <div className="hash">
              prev {r.prevHash} → {r.hash}
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
    </div>
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

  const header = (
    <PageHeader
      title={id || 'Decision'}
      description="How the engine reached this outcome — policy checks, scorecard points, ranked reason codes — and the ledger evidence that proves it."
    />
  )

  if (!data) return header

  const d: Detail = data
  const base = d.base
  const current = d.current

  if (d.status === 'ENGINE_PENDING' && !base) {
    return (
      <>
        {header}
        <div className="tile w">
          <div className="th">
            <h3>Waiting for the decision engine</h3>
          </div>
          <p className="t-muted" style={{ lineHeight: 1.6 }}>
            The decision engine was unavailable after retries, so this application is queued for the engine retry job. This
            page polls every 3 seconds and updates as soon as a decision lands.
          </p>
        </div>
      </>
    )
  }

  const question = `Why was ${id} ${
    base?.outcome === 'APPROVED' ? 'approved' : base?.outcome === 'REFER' ? 'referred' : 'declined'
  }?`
  const declined = base?.outcome === 'DECLINED' || current?.outcome === 'DECLINED'
  const openRefer = current?.outcome === 'REFER'

  const bd = d.breakdown
  const outcome = current?.outcome || ''

  function showNotice() {
    notice.mutate(id!, {
      onSuccess: (text) =>
        openModal(
          <>
            <h3>Adverse action notice</h3>
            <p className="t-muted" style={{ margin: '4px 0 16px' }}>
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

  const left = (
    <aside className="dos-l">
      <div className="lbl">
        {d.applicationId} · {d.product}
      </div>
      <div className={`out ${outcome}`}>{OCM[outcome] ? OCM[outcome][1] : '—'}</div>
      <div className="who2">{d.displayName}</div>
      {base?.score != null && (
        <ScoreRuler score={base.score} refer={bd?.referCutoff} approve={bd?.approveCutoff} />
      )}
      <div style={{ marginTop: 14 }}>
        <div className="drow">
          <span>{outcome === 'APPROVED' ? 'Credit limit' : 'Score'}</span>
          <b>{outcome === 'APPROVED' ? money(current?.creditLimit || 0) : (base?.score ?? '—')}</b>
        </div>
        <div className="drow">
          <span>Rules</span>
          <b>{base?.ruleVersion}</b>
        </div>
        <div className="drow">
          <span>Ledger</span>
          <b>seq #{base?.seq}</b>
        </div>
        <div className="drow">
          <span>Recorded</span>
          <b>{fmtTs(base?.createdAt)}</b>
        </div>
        {current?.kind === 'OVERRIDE' && current.override && (
          <div className="drow">
            <span>Override</span>
            <b>
              {current.override.by} · {current.override.code}
            </b>
          </div>
        )}
        {current?.kind === 'REDECISION' && (
          <div className="drow">
            <span>Re-decided</span>
            <b>after bureau recovery</b>
          </div>
        )}
      </div>
      <div style={{ marginTop: 14 }}>
        <button className="dbtn g" onClick={() => navigate(`/app/assistant?ask=${encodeURIComponent(question)}`)}>
          <span>✦ Ask assistant</span>
          <span>→</span>
        </button>
        {declined && (
          <button className="dbtn" onClick={showNotice}>
            <span>Adverse action notice</span>
            <span>→</span>
          </button>
        )}
        {openRefer && (
          <button className="dbtn" onClick={() => navigate(`/app/queue?app=${id}`)}>
            <span>Review in queue</span>
            <span>→</span>
          </button>
        )}
        <button className="dbtn" onClick={() => navigate('/app/decisions')}>
          <span>← All decisions</span>
          <span />
        </button>
      </div>
    </aside>
  )

  // Bureau-unavailable variant (B01): no engine evaluation, only the fallback tile + ledger trail.
  const isBureauUnavailable = !d.bureau && (base?.reasonCodes || []).some((r) => r.code === 'B01')
  if (isBureauUnavailable) {
    return (
      <>
        {header}
        <div className="dos" style={{ marginTop: 14 }}>
          {left}
          <div>
            <div className="tile w" style={{ marginBottom: 14 }}>
              <div className="th">
                <h3>Bureau unavailable</h3>
              </div>
              <p className="t-muted" style={{ lineHeight: 1.6 }}>
                The bureau circuit breaker was OPEN when this application arrived, so the pipeline fell back to REFER with
                internal reason B01 instead of failing. It's queued for automatic re-decision when the circuit closes
                (System → Restore bureau).
              </p>
              <div className="rc b" style={{ marginTop: 14 }}>
                <code>B01</code>Credit report temporarily unavailable (internal)
              </div>
            </div>
            <LedgerTrail trail={d.trail || []} shadow={d.shadow as ShadowBlock | null} />
          </div>
        </div>
      </>
    )
  }

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
      {header}
      <div className="dos" style={{ marginTop: 14 }}>
        {left}
        <div>
          <div className="g g2">
            <div className="tile">
              <div className="th">
                <h3>Reason codes</h3>
                <span className="lbl">max 4 · ranked</span>
              </div>
              {applicantReasons.length ? (
                applicantReasons.map((r) => (
                  <div className="rc" key={r.code}>
                    <code>{r.code}</code>
                    {r.description}
                  </div>
                ))
              ) : (
                <p className="t-muted">
                  {base?.outcome === 'APPROVED' ? 'Approved — no adverse reasons.' : 'No applicant-facing reasons.'}
                </p>
              )}
              {fraudFlags.length > 0 && (
                <>
                  <div className="lbl" style={{ margin: '16px 0 8px' }}>
                    Internal fraud flags · never shown
                  </div>
                  {fraudFlags.map((f) => (
                    <div className="rc f" key={f.code}>
                      <code>{f.code}</code>
                      {f.description}
                    </div>
                  ))}
                </>
              )}
              {base?.outcome === 'APPROVED' && (
                <div style={{ marginTop: 8 }}>
                  <div className="chkrow">
                    <span>Score-band limit</span>
                    <b>{money(bd?.bandLimit || 0)}</b>
                  </div>
                  <div className="chkrow">
                    <span>Ability-to-pay max</span>
                    <b>{money(base.atpMax || 0)}</b>
                  </div>
                  <div className="chkrow">
                    <span>Assigned · lower of the two</span>
                    <b className="t-ok">{money(base.creditLimit || 0)}</b>
                  </div>
                </div>
              )}
            </div>

            <div className="tile">
              <div className="th">
                <h3>Policy & identity</h3>
              </div>
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
            </div>
          </div>

          <div className="tile w" style={{ marginBottom: 14 }}>
            <div className="th">
              <h3>Scorecard</h3>
              <span className="lbl">
                300 base + {pointsSum} points = {base?.score}
              </span>
            </div>
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
                      <td className="mono">{p.value}</td>
                      <td className="t-muted">{p.band}</td>
                      <td className="mono">
                        <b>{p.points}</b> / {p.maxPoints}
                      </td>
                      <td className={`mono ${lost > 0 ? 't-bad' : ''}`}>{lost ? '−' + lost : '0'}</td>
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
            <p className="note">Age is not a scoring factor.</p>
          </div>

          <div className="g g2">
            <div className="tile">
              <div className="th">
                <h3>Input snapshot</h3>
                <button className="btn sm p" onClick={runReproduce}>
                  Reproduce ↻
                </button>
              </div>
              <div className="code">{JSON.stringify(base?.engineInput, null, 2)}</div>
              {repro && (
                <div className="rc" style={{ marginTop: 12 }}>
                  <code style={{ color: repro.identical ? 'var(--moss)' : 'var(--rust)' }}>
                    {repro.identical ? '✓' : '✗'}
                  </code>
                  <span>
                    GET /decisions/{base?.seq}/reproduce · re-ran the stored input under <b>{repro.ruleVersion}</b>:{' '}
                    {repro.identical ? (
                      <>
                        <b>identical</b> outcome, score, limit and reasons.
                      </>
                    ) : (
                      <>
                        <b>MISMATCH</b> — investigate engine version skew.
                      </>
                    )}
                  </span>
                </div>
              )}
            </div>

            <div className="tile">
              <div className="th">
                <h3>Bureau pull</h3>
                <span className="pill2">{d.bureau?.pullType}</span>
              </div>
              <div className="chkrow">
                <span>Pull ID</span>
                <b className="mono">{d.bureau?.pullId}</b>
              </div>
              <div className="chkrow">
                <span>Report</span>
                <b>{d.bureau?.reused ? 'Reused · 30-day window' : 'Fresh pull'}</b>
              </div>
              <div className="chkrow">
                <span>SSN at rest</span>
                <b className="mono">{d.ssnEncPreview}</b>
              </div>
              <div className="code" style={{ marginTop: 12, maxHeight: 180 }}>
                {d.bureau?.rawXml}
              </div>
            </div>
          </div>

          <LedgerTrail trail={d.trail || []} shadow={d.shadow as ShadowBlock | null} />
        </div>
      </div>
    </>
  )
}
