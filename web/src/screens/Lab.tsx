import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageHeader, Card, OutcomePill, StatusChip } from '../ui/components'
import {
  useVersions,
  useLiveVersion,
  useCreateVersion,
  useUpdateConfig,
  useDiscardVersion,
  useBackToDraft,
  useStartReplay,
  useReplayJob,
  useFlips,
  useProposeVersion,
  useApproveVersion,
  useRejectVersion,
  useRollback,
  useToggleShadow,
  useShadowResults,
} from '../api/hooks'
import { useAuth } from '../app/AuthProvider'
import { useToast } from '../app/ToastProvider'
import { useModal } from '../app/ModalProvider'
import { apiFetch, ApiError } from '../api/client'
import { fmt, fmtTs, money, pct } from '../ui/format'
import type { FlipDetail, JobView, ReplayReport, RuleConfig, VersionView } from '../api/types'

const OUT = ['APPROVED', 'REFER', 'DECLINED']

interface Field {
  key: string
  label: string
  unit: string
  get: (c: RuleConfig) => number
  set: (c: RuleConfig, v: number) => void
}
const FIELDS: Field[] = [
  { key: 'approveCutoff', label: 'Approve cutoff', unit: 'score', get: (c) => c.approveCutoff ?? 0, set: (c, v) => (c.approveCutoff = v) },
  { key: 'referCutoff', label: 'Refer cutoff', unit: 'score', get: (c) => c.referCutoff ?? 0, set: (c, v) => (c.referCutoff = v) },
  { key: 'atpShare', label: 'Ability-to-pay share of residual income', unit: '%', get: (c) => Math.round((c.atpShare ?? 0) * 100), set: (c, v) => (c.atpShare = v / 100) },
  { key: 'minPayPct', label: 'Estimated minimum payment', unit: '% of limit', get: (c) => +(((c.minPayPct ?? 0) * 100).toFixed(1)), set: (c, v) => (c.minPayPct = v / 100) },
  { key: 'topLimit', label: 'Top-band credit limit (800+)', unit: '$', get: (c) => c.bandLimits?.[0]?.limit ?? 0, set: (c, v) => { if (c.bandLimits?.[0]) c.bandLimits[0].limit = v } },
  { key: 'util50', label: 'Points: utilization 50–74%', unit: 'pts', get: (c) => c.utilPts?.[3] ?? 0, set: (c, v) => { if (c.utilPts) c.utilPts[3] = v } },
  { key: 'inq34', label: 'Points: 3–4 inquiries', unit: 'pts', get: (c) => c.inqPts?.[2] ?? 0, set: (c, v) => { if (c.inqPts) c.inqPts[2] = v } },
]

const clone = <T,>(o: T): T => JSON.parse(JSON.stringify(o))

export function Lab() {
  const [params, setParams] = useSearchParams()
  const { me } = useAuth()
  const { toast, denyToast } = useToast()
  const { openModal, closeModal } = useModal()

  const { data: versionsData } = useVersions()
  const { data: live } = useLiveVersion()
  const createV = useCreateVersion()
  const updateCfg = useUpdateConfig()
  const discard = useDiscardVersion()
  const backToDraft = useBackToDraft()
  const startReplay = useStartReplay()
  const propose = useProposeVersion()
  const approve = useApproveVersion()
  const reject = useRejectVersion()
  const rollback = useRollback()
  const toggleShadow = useToggleShadow()

  const items = versionsData?.items || []
  const liveVersion = versionsData?.liveVersion
  const rollbackTarget = versionsData?.rollbackTarget

  const [selected, setSelected] = useState<string | null>(null)
  const [localCfg, setLocalCfg] = useState<RuleConfig | null>(null)
  const [cfgErrors, setCfgErrors] = useState<string[]>([])
  const [jobId, setJobId] = useState<string | null>(null)
  const [sim, setSim] = useState(false)
  const syncedRef = useRef<string | null>(null)

  // Resolve the selected version from ?v= or the newest candidate / live.
  useEffect(() => {
    if (!items.length) return
    const wanted = params.get('v')
    const valid = selected && items.some((v) => v.version === selected)
    if (!valid) {
      const target =
        (wanted && items.find((v) => v.version === wanted)) ||
        items.find((v) => ['DRAFT', 'REPLAYED', 'PROPOSED'].includes(v.status || '')) ||
        items.find((v) => v.version === liveVersion) ||
        items[0]
      setSelected(target?.version || null)
    }
  }, [items, params, selected, liveVersion])

  const version: VersionView | undefined = items.find((v) => v.version === selected)
  const liveCfg = live?.config

  // Sync the editable local config once per selection.
  useEffect(() => {
    if (version?.config && syncedRef.current !== version.version) {
      setLocalCfg(clone(version.config))
      setCfgErrors([])
      syncedRef.current = version.version || null
      setJobId(version.latestReplayJobId || null)
    }
  }, [version])

  // Poll the active replay job every 500 ms until terminal.
  const { data: job } = useReplayJob(jobId || undefined, true)
  const jobStatus = job?.status
  const isRunning = jobStatus === 'QUEUED' || jobStatus === 'RUNNING'

  const editable = version?.status === 'DRAFT' && !version.usedBy

  function can(role: string) {
    return me?.role === role
  }

  function onEditField(f: Field, value: number) {
    if (!localCfg || !version) return
    const next = clone(localCfg)
    f.set(next, value)
    setLocalCfg(next)
    updateCfg.mutate(
      { v: version.version!, config: next },
      {
        onSuccess: () => setCfgErrors([]),
        onError: (e) => {
          const body = e instanceof ApiError ? (e.body as { errors?: string[] } | undefined) : undefined
          setCfgErrors(body?.errors || [e instanceof ApiError ? e.detail || 'Invalid config' : 'Invalid config'])
        },
      },
    )
  }

  function onNewCandidate() {
    if (!can('STRATEGIST')) return denyToast('STRATEGIST')
    if (!liveCfg) return
    const cfg = clone(liveCfg)
    cfg.approveCutoff = 700
    createV.mutate(
      { config: cfg },
      {
        onSuccess: (v) => {
          setSelected(v.version || null)
          setParams({ v: v.version || '' })
          toast(`${v.version} created as a DRAFT with approve cutoff 680 → 700. Edit any parameter, then run the replay.`)
        },
        onError: (e) => {
          const open = items.find((x) => ['DRAFT', 'REPLAYED', 'PROPOSED'].includes(x.status || ''))
          if (open) setSelected(open.version || null)
          toast(e instanceof ApiError ? e.detail || 'An open candidate already exists.' : 'An open candidate already exists.', 'warn')
        },
      },
    )
  }

  function onRunReplay() {
    if (!can('STRATEGIST')) return denyToast('STRATEGIST')
    if (!version) return
    startReplay.mutate(version.version!, {
      onSuccess: (r) => setJobId(r.jobId),
      onError: (e) => toast(e instanceof ApiError ? e.detail || 'Could not start the replay' : 'Could not start the replay', 'bad'),
    })
  }

  // Toast + report render when the polled job reaches DONE.
  const announced = useRef<string | null>(null)
  useEffect(() => {
    if (job?.status === 'DONE' && job.jobId && announced.current !== job.jobId) {
      announced.current = job.jobId
      toast(`Replay ${job.jobId} complete: ${fmt(job.report ? (job.report as ReplayReport).n : job.total || 0)} decisions in ${job.totalMs} ms.`, 'ok')
    }
  }, [job, toast])

  function onPropose() {
    if (!can('STRATEGIST')) return denyToast('STRATEGIST')
    propose.mutate(version!.version!, {
      onSuccess: () => toast(`${version!.version} proposed. It now needs a second person with the APPROVER role.`),
      onError: (e) => toast(e instanceof ApiError ? e.detail || 'Could not propose' : 'Could not propose', 'bad'),
    })
  }
  function onApprove() {
    if (!can('APPROVER')) return denyToast('APPROVER')
    approve.mutate(version!.version!, {
      onSuccess: () => toast(`${version!.version} is now LIVE. Promotion recorded in the ledger. New applications use it immediately.`, 'ok'),
      onError: (e) => toast(e instanceof ApiError ? e.detail || 'Could not approve' : 'Could not approve', 'bad'),
    })
  }
  function onReject() {
    if (!can('APPROVER')) return denyToast('APPROVER')
    reject.mutate({ v: version!.version!, note: 'Rejected — revise and replay.' })
  }
  function onRollback() {
    if (!can('APPROVER')) return denyToast('APPROVER')
    rollback.mutate(undefined, {
      onSuccess: () => toast(`Rolled back to ${rollbackTarget}. Recorded in the ledger.`, 'warn'),
      onError: (e) => toast(e instanceof ApiError ? e.detail || 'Could not roll back' : 'Could not roll back', 'bad'),
    })
  }
  function onToggleShadow() {
    if (!can('STRATEGIST')) return denyToast('STRATEGIST')
    const enabling = !version!.shadow
    toggleShadow.mutate(
      { v: version!.version!, enabled: enabling },
      { onSuccess: () => toast(enabling ? `${version!.version} now scores every new application silently. Submit one to see it.` : 'Shadow mode stopped.') },
    )
  }

  const steps = ['DRAFT', 'REPLAYED', 'PROPOSED', 'LIVE']
  const si = version?.status === 'RETIRED' ? -1 : steps.indexOf(version?.status || '')
  const knownTotal = job?.total ?? (job?.report ? (job.report as ReplayReport).n : undefined)

  return (
    <>
      <PageHeader
        eyebrow="Strategy Lab"
        title="Test rules on history before they ship"
        description="Draft a candidate version, replay every historical decision through it, read an honest impact report, then promote through maker-checker approval."
        right={
          <button className="btn p" onClick={onNewCandidate}>
            + New candidate from {liveVersion}
          </button>
        }
      />

      <div className="vcards">
        {items.map((x) => (
          <div key={x.version} className={`vcard ${x.version === selected ? 'on' : ''}`} onClick={() => setSelected(x.version || null)}>
            <StatusChip status={x.status} />
            {x.shadow && <span className="pill"> shadow</span>}
            <h4>{x.version}</h4>
            <p>
              {x.note}
              <br />
              by {x.createdBy}
              {x.approvedBy && ` · approved by ${x.approvedBy}`}
              {x.proposedBy && x.status === 'PROPOSED' && ` · proposed by ${x.proposedBy}`}
              <br />
              {x.usedBy ? (
                <>
                  <b>Immutable</b> · used by {x.usedBy} decisions
                </>
              ) : (
                fmtTs(x.createdAt).slice(0, 10)
              )}
            </p>
          </div>
        ))}
      </div>

      {version && (
        <Card style={{ marginBottom: 20 }}>
          <h3>
            {version.version}{' '}
            <span className="row">
              {version.status === 'DRAFT' && (
                <>
                  <button className="btn p" onClick={onRunReplay} disabled={isRunning || cfgErrors.length > 0}>
                    Run replay on {knownTotal ? `${fmt(knownTotal)} decisions` : 'history'}
                  </button>
                  <button className="btn bad" onClick={() => can('STRATEGIST') ? discard.mutate(version.version!) : denyToast('STRATEGIST')}>
                    Discard
                  </button>
                </>
              )}
              {version.status === 'REPLAYED' && (
                <>
                  <button className="btn" onClick={onToggleShadow}>
                    {version.shadow ? 'Stop shadow mode' : 'Run in shadow mode'}
                  </button>
                  <button className="btn" onClick={() => can('STRATEGIST') ? backToDraft.mutate(version.version!) : denyToast('STRATEGIST')}>
                    Edit (back to draft)
                  </button>
                  <button className="btn p" onClick={onPropose}>
                    Propose for approval
                  </button>
                </>
              )}
              {version.status === 'PROPOSED' && (
                <>
                  <button className="btn bad" onClick={onReject}>
                    Reject
                  </button>
                  <button className="btn p" onClick={onApprove}>
                    Approve &amp; promote to LIVE
                  </button>
                </>
              )}
              {version.status === 'LIVE' && rollbackTarget && (
                <button className="btn bad" onClick={onRollback}>
                  Roll back to {rollbackTarget}
                </button>
              )}
            </span>
          </h3>

          {si >= 0 ? (
            <div className="wf">
              {steps.map((s, i) => (
                <div key={s} className={i < si ? 'done' : i === si ? 'cur' : ''}>
                  {i < si ? '✓ ' : ''}
                  {s[0] + s.slice(1).toLowerCase()}
                </div>
              ))}
            </div>
          ) : (
            <p className="note" style={{ marginBottom: 14 }}>
              Retired version — kept forever so its decisions stay reproducible.
            </p>
          )}

          <div className="g g21" style={{ margin: 0 }}>
            <div>
              <table>
                <thead>
                  <tr>
                    <th>Parameter</th>
                    <th>Live {liveVersion}</th>
                    <th>{version.version}</th>
                    <th>Unit</th>
                  </tr>
                </thead>
                <tbody>
                  {FIELDS.map((f) => {
                    const a = liveCfg ? f.get(liveCfg) : 0
                    const b = localCfg ? f.get(localCfg) : 0
                    return (
                      <tr key={f.key} className={a !== b ? 'diff' : ''}>
                        <td>{f.label}</td>
                        <td className="mono">{a}</td>
                        <td>
                          {editable ? (
                            <input
                              type="number"
                              step="any"
                              defaultValue={b}
                              style={{ width: 110, padding: '7px 9px', border: '1px solid var(--line)', borderRadius: 7, background: 'var(--panel2)', color: 'var(--ink)' }}
                              onChange={(e) => onEditField(f, Number(e.target.value))}
                            />
                          ) : (
                            <span className="mono">{b}</span>
                          )}
                        </td>
                        <td className="t-muted">{f.unit}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
              {editable && (
                <p className="note">Changed rows are highlighted. Edits are validated on change; a version becomes immutable once any decision uses it.</p>
              )}
            </div>
            <div>
              <div className="lbl">Config validation</div>
              <div className="vlist">
                {cfgErrors.length ? (
                  cfgErrors.map((e, i) => (
                    <div className="t-bad" key={i}>
                      ✗ {e}
                    </div>
                  ))
                ) : (
                  <div className="t-ok">✓ Bands contiguous, cutoffs ordered, ranges valid</div>
                )}
              </div>
              <div className="lbl" style={{ marginTop: 20 }}>
                Governance
              </div>
              <div className="vlist t-muted">
                <div>Proposer: {version.proposedBy || '—'}</div>
                <div>Approver: {version.approvedBy || '—'}</div>
                <div style={{ marginTop: 6 }}>
                  Maker-checker: the proposer cannot approve their own change. Strategists propose; approvers promote.
                </div>
              </div>
              {isRunning && (
                <>
                  <div className="lbl" style={{ marginTop: 20 }}>
                    Replaying…
                  </div>
                  <div className="progress">
                    <div style={{ width: `${job?.total ? ((job.progress || 0) / job.total) * 100 : 0}%` }} />
                  </div>
                  <div className="note">
                    {fmt(job?.progress || 0)} / {fmt(job?.total || 0)} decisions
                  </div>
                </>
              )}
            </div>
          </div>
        </Card>
      )}

      {job?.status === 'DONE' && job.report ? (
        <ReplayReportView job={job} sim={sim} setSim={setSim} openModal={openModal} closeModal={closeModal} />
      ) : version?.status === 'DRAFT' ? (
        <Card>
          <div className="empty">Run a replay to see approval shifts, flipped applicants, exposure-weighted loss and segment impact.</div>
        </Card>
      ) : null}

      {version?.shadow && <ShadowCard version={version.version!} />}
    </>
  )
}

function ReplayReportView({
  job,
  sim,
  setSim,
  openModal,
  closeModal,
}: {
  job: JobView
  sim: boolean
  setSim: (v: boolean) => void
  openModal: (c: React.ReactNode) => void
  closeModal: () => void
}) {
  const R = job.report as ReplayReport
  const { data: flipsPage } = useFlips(job.jobId, 0, 20)
  const flips = flipsPage?.items || []

  const dA = R.candidate.approvalRate - R.baseline.approvalRate
  const flipCount = R.flips
  const elC = sim ? (R.candidate.expectedLossSimulated ?? R.candidate.expectedLossObserved) : R.candidate.expectedLossObserved
  const dEL = R.baseline.expectedLossObserved ? (elC - R.baseline.expectedLossObserved) / R.baseline.expectedLossObserved : 0

  async function openCompare(seq: number, appId?: string) {
    try {
      const d = await apiFetch<FlipDetail>(`/api/v1/lab/replays/${job.jobId}/flips/${seq}`)
      openModal(<FlipCompare d={d} appId={appId} onClose={closeModal} />)
    } catch {
      /* ignore */
    }
  }

  return (
    <>
      <Card style={{ marginBottom: 20 }}>
        <h3>
          Replay report · {job.jobId}{' '}
          <span className="t-muted" style={{ fontSize: 13, fontWeight: 500 }}>
            {R.candidate.version} vs live {R.baseline.version} · {fmt(R.n)} historical decisions · load {job.loadMs} ms · evaluate{' '}
            {job.evaluateMs} ms · total {job.totalMs} ms
          </span>
        </h3>
        <div className="kpis k4" style={{ marginBottom: 18, boxShadow: 'none' }}>
          <div className="kpi">
            <div className="lbl">Approval rate</div>
            <div className={`v ${dA < 0 ? 't-warn' : 't-ok'}`}>
              {pct(R.baseline.approvalRate, 1)} → {pct(R.candidate.approvalRate, 1)}
            </div>
            <small>
              {dA >= 0 ? '+' : ''}
              {(dA * 100).toFixed(1)} pts
            </small>
          </div>
          <div className="kpi">
            <div className="lbl">Decisions flipped</div>
            <div className="v">{fmt(flipCount)}</div>
            <small>{pct(flipCount / R.n, 1)} of history</small>
          </div>
          <div className="kpi">
            <div className="lbl">{sim ? 'Expected loss (incl. simulation)' : 'Expected loss (observed)'}</div>
            <div className={`v ${dEL <= 0 ? 't-ok' : 't-bad'}`}>
              {money(R.baseline.expectedLossObserved)} → {money(elC)}
            </div>
            <small>
              {dEL >= 0 ? '+' : ''}
              {(dEL * 100).toFixed(1)}% · limit changes: {fmt(R.limitChanges)}
            </small>
          </div>
          <div className="kpi">
            <div className="lbl">Outcome unknown</div>
            <div className={`v ${R.outcomeUnknown.count ? 't-warn' : 't-ok'}`}>{fmt(R.outcomeUnknown.count)}</div>
            <small>{R.outcomeUnknown.count ? `${money(R.outcomeUnknown.exposure)} exposure never observed` : 'Tightening only — fully observable'}</small>
          </div>
        </div>
        {R.immature.count > 0 && (
          <p className="note" style={{ marginTop: 0 }}>
            {fmt(R.immature.count)} recent approvals have no outcome yet and are excluded.
          </p>
        )}
        <div className="row" style={{ marginBottom: 18 }}>
          <span className={`toggle ${sim ? 'on' : ''}`} onClick={() => setSim(!sim)}>
            <i />
            Include <b>simulated</b> outcomes for applicants never observed
          </span>
          {sim && (
            <span className="pill" style={{ background: 'var(--warnbg)', color: 'var(--warn)' }}>
              SIMULATION — counterfactual outcomes from the generator, not observed data
            </span>
          )}
        </div>

        <div className="g g12" style={{ margin: 0 }}>
          <div>
            <div className="lbl" style={{ marginBottom: 10 }}>
              Flip matrix · rows live, columns candidate
            </div>
            <table className="mx">
              <thead>
                <tr>
                  <th />
                  {OUT.map((o) => (
                    <th key={o}>{o.slice(0, 4)}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {OUT.map((o, i) => (
                  <tr key={o}>
                    <th>{o.slice(0, 4)}</th>
                    {OUT.map((_, j) => (
                      <td key={j} className={i !== j && R.matrix[i][j] ? 'hl' : ''}>
                        {fmt(R.matrix[i][j])}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="note">
              Expected loss = PD × EAD × LGD. EAD = limit × CCF {R.assumptions.ccf}; LGD {R.assumptions.lgd} (stated
              assumptions). PD from {R.assumptions.pdSource}. Applicants a candidate newly approves were never observed
              (reject inference) and are excluded unless simulation is on.
            </p>
          </div>
          <div>
            <div className="lbl" style={{ marginBottom: 10 }}>
              Segment impact · by live score band
            </div>
            <table>
              <thead>
                <tr>
                  <th>Band</th>
                  <th>Apps</th>
                  <th>Approval live → cand</th>
                  <th>Δ</th>
                  <th>Loss live → cand</th>
                </tr>
              </thead>
              <tbody>
                {R.segments.map((s) => {
                  const d = s.n ? (s.candidateApprovals - s.baselineApprovals) / s.n : 0
                  return (
                    <tr key={s.band}>
                      <td>{s.band}</td>
                      <td>{fmt(s.n)}</td>
                      <td>
                        {s.n ? pct(s.baselineApprovals / s.n, 1) : '—'} → {s.n ? pct(s.candidateApprovals / s.n, 1) : '—'}
                      </td>
                      <td>
                        <div className="segbar">
                          <span style={{ width: Math.min(80, Math.abs(d) * 400), background: d < 0 ? 'var(--warn)' : d > 0 ? 'var(--ok)' : 'var(--line)' }} />
                          <b className={d < 0 ? 't-warn' : d > 0 ? 't-ok' : 't-muted'}>
                            {d >= 0 ? '+' : ''}
                            {(d * 100).toFixed(1)}
                          </b>
                        </div>
                      </td>
                      <td className="mono">
                        {money(s.baselineLoss)} → {money(s.candidateLoss)}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      </Card>

      <Card style={{ marginBottom: 20 }}>
        <h3>
          Flipped applicants <span className="lbl">first {Math.min(20, flips.length)} · click to compare</span>
        </h3>
        <table>
          <thead>
            <tr>
              <th>Record</th>
              <th>Score live → cand</th>
              <th>Outcome live → cand</th>
              <th>Candidate reasons</th>
              <th>Observed?</th>
            </tr>
          </thead>
          <tbody>
            {flips.map((f) => (
              <tr key={f.seq} className="cl" onClick={() => openCompare(f.seq!, f.applicationId)}>
                <td className="mono">{f.applicationId}</td>
                <td>
                  {f.baseline?.score} → {f.candidate?.score}
                </td>
                <td>
                  <OutcomePill outcome={f.baseline?.outcome} /> → <OutcomePill outcome={f.candidate?.outcome} />
                </td>
                <td>
                  {(f.candidateReasons || []).slice(0, 3).map((r) => (
                    <span key={r} className="pill">
                      {r}
                    </span>
                  )) || '—'}
                </td>
                <td>{f.observed ? <span className="t-ok">observed</span> : <span className="t-warn">outcome unknown</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </>
  )
}

function FlipCompare({ d, appId, onClose }: { d: FlipDetail; appId?: string; onClose: () => void }) {
  const col = (title: string, side: FlipDetail['baseline']) => {
    const dec = side?.decision
    return (
      <div className="card">
        <div className="lbl">{title}</div>
        <div style={{ margin: '8px 0 12px' }}>
          <OutcomePill outcome={dec?.outcome} /> <b style={{ fontSize: 22, marginLeft: 8 }}>{dec?.score}</b>{' '}
          {dec?.creditLimit ? '· ' + money(dec.creditLimit) : ''}
        </div>
        {(dec?.scoreParts || []).map((p) => (
          <div className="chkrow" key={p.code}>
            <span>{p.attribute}</span>
            <b>
              {p.points}/{p.maxPoints}
            </b>
          </div>
        ))}
        <div style={{ marginTop: 10 }}>
          {(dec?.reasonCodes || []).length ? (
            (dec?.reasonCodes || []).map((r) => (
              <div className="rc" key={r}>
                <code>{r}</code>
              </div>
            ))
          ) : (
            <p className="t-muted">No reasons (approved)</p>
          )}
        </div>
        <p className="note">
          Cutoffs: approve ≥ {side?.approveCutoff}, refer ≥ {side?.referCutoff}
        </p>
      </div>
    )
  }
  return (
    <>
      <h3>
        {appId || d.applicationId} · live vs candidate
      </h3>
      <p className="t-muted" style={{ marginBottom: 16 }}>
        Same stored input, two rule versions. {d.observed ? 'Outcome observed.' : 'Never approved historically — outcome unknown.'}
      </p>
      <div className="g g2">
        {col(`Live ${d.baseline?.version}`, d.baseline)}
        {col(`Candidate ${d.candidate?.version}`, d.candidate)}
      </div>
      <div className="code">{JSON.stringify(d.engineInput, null, 2)}</div>
      <div className="row" style={{ marginTop: 16 }}>
        <span className="sp" />
        <button className="btn p" onClick={onClose}>
          Close
        </button>
      </div>
    </>
  )
}

function ShadowCard({ version }: { version: string }) {
  const { data } = useShadowResults(version, true)
  const items = data?.items || []
  return (
    <Card>
      <h3>
        Shadow mode · {version}{' '}
        <span className="lbl">
          {data?.count ?? 0} live applications scored silently · {data?.disagreements ?? 0} disagreements
        </span>
      </h3>
      {items.length ? (
        <table>
          <thead>
            <tr>
              <th>Application</th>
              <th>Live</th>
              <th>Shadow</th>
              <th>Agrees</th>
            </tr>
          </thead>
          <tbody>
            {items.map((r) => (
              <tr key={r.applicationId}>
                <td className="mono">{r.applicationId}</td>
                <td>
                  <OutcomePill outcome={r.live?.outcome} /> {r.live?.creditLimit ? money(r.live.creditLimit) : ''}
                </td>
                <td>
                  <OutcomePill outcome={r.shadow?.outcome} /> {r.shadow?.creditLimit ? money(r.shadow.creditLimit) : ''}
                </td>
                <td className={r.agrees ? 't-ok' : 't-warn'}>{r.agrees ? 'Yes' : 'No'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="empty">Waiting for live traffic. Submit an application under New application.</div>
      )}
      <p className="note">Shadow results are logged beside the ledger record and never returned to the applicant.</p>
    </Card>
  )
}
