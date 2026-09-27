import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader, LiveChips, Card, OutcomePill } from '../ui/components'
import { useLiveVersion, useSubmitApplication } from '../api/hooks'
import { useModal } from '../app/ModalProvider'
import { useToast } from '../app/ToastProvider'
import { money, pct } from '../ui/format'
import type { PipelineStep, RuleConfig, SubmitResult } from '../api/types'
import type { RawResult } from '../api/client'

const PROFILES: [string, string, string][] = [
  ['prime', 'Prime', '1'],
  ['near', 'Near-prime', '3'],
  ['sub', 'Subprime', '6'],
  ['thin', 'Thin file', '8'],
]
const SCENARIOS: [string, string, string][] = [
  ['none', 'None', '1'],
  ['addr', 'Address mismatch with bureau file', '7'],
  ['ssn', 'SSN issued before date of birth', '8'],
  ['dead', 'SSN on deceased list', '9'],
  ['vel', 'Velocity: 3+ applications in 24 h', '1'],
]
const PRODUCTS: [string, string][] = [
  ['REWARDS_CARD', 'Rewards Card'],
  ['STORE_CARD', 'Store Card'],
  ['HEALTHCARE_CARD', 'Healthcare Card'],
]
const STEP_LABELS = [
  'Validate request',
  'Idempotency check',
  'Credit bureau · SOAP pull',
  'Fraud & identity screen',
  'Decision engine',
  'Ledger commit · single transaction',
]

interface FormState {
  firstName: string
  lastName: string
  dateOfBirth: string
  address: string
  annualIncome: string
  monthlyHousing: string
  monthlyDebt: string
  independentIncome: boolean
  bureauConsent: boolean
  product: string
  profile: string
  scenario: string
}
const DEF: FormState = {
  firstName: 'Meera',
  lastName: 'Joshi',
  dateOfBirth: '1996-04-18',
  address: '48 Elm Street, Columbus OH',
  annualIncome: '64000',
  monthlyHousing: '1350',
  monthlyDebt: '280',
  independentIncome: true,
  bureauConsent: true,
  product: 'REWARDS_CARD',
  profile: 'near',
  scenario: 'none',
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
const randKey = () => 'idem_' + Math.random().toString(36).slice(2, 12)
function genSsn(profile: string, scenario: string): string {
  const p = PROFILES.find((x) => x[0] === profile)![2]
  const s = SCENARIOS.find((x) => x[0] === scenario)![2]
  let rest = ''
  for (let i = 0; i < 6; i++) rest += Math.floor(Math.random() * 10)
  return '9' + p + s + rest
}
function ageFrom(dob: string): number {
  const d = new Date(dob)
  if (isNaN(d.getTime())) return NaN
  const t = new Date()
  let a = t.getFullYear() - d.getFullYear()
  const m = t.getMonth() - d.getMonth()
  if (m < 0 || (m === 0 && t.getDate() < d.getDate())) a--
  return a
}

interface Result {
  outcome?: string
  score?: number | null
  pending: boolean
  appId?: string
}
function PipelineFrame({
  steps,
  revealed,
  result,
  onOpen,
}: {
  steps: PipelineStep[]
  revealed: number
  result: Result | null
  onOpen: (id?: string) => void
}) {
  return (
    <>
      <h3>Deciding application</h3>
      <p className="t-muted">Synchronous orchestration by application-service</p>
      <div className="pipe">
        {steps.map((s, i) => {
          const rev = i < revealed
          const cls = !rev ? 'run' : s.status === 'OK' ? 'ok' : 'warn'
          const icon = !rev ? i + 1 : s.status === 'OK' ? '✓' : '!'
          const text = !rev ? '' : s.status === 'SKIPPED' ? 'skipped' : `${s.detail ? s.detail + ' · ' : ''}${s.ms} ms`
          return (
            <div className={`pstep ${cls}`} key={i}>
              <span className="ico">{icon}</span>
              {s.label}
              <small>{text}</small>
            </div>
          )
        })}
      </div>
      {result && (
        <div className="row" style={{ marginTop: 20 }}>
          <span>Result:</span> {!result.pending && <OutcomePill outcome={result.outcome} />}{' '}
          <span className="t-muted">
            {result.pending
              ? 'queued for retry'
              : result.score != null
                ? 'score ' + result.score
                : 'bureau unavailable — will auto re-decide'}
          </span>
          <span className="sp" />
          <button className="btn p" onClick={() => onOpen(result.appId)}>
            Open decision →
          </button>
        </div>
      )}
    </>
  )
}

export function Apply() {
  const navigate = useNavigate()
  const { openModal, setModalContent, setLocked, closeModal } = useModal()
  const { toast, denyToast } = useToast()
  const submit = useSubmitApplication()
  const { data: live } = useLiveVersion()

  const [form, setForm] = useState<FormState>(DEF)
  const [ssn, setSsn] = useState(() => genSsn(DEF.profile, DEF.scenario))
  const [idemKey, setIdemKey] = useState(randKey)
  const [velocity, setVelocity] = useState(0)
  const [errors, setErrors] = useState<Record<string, string>>({})

  // Regenerate the SSN whenever the profile or scenario changes (and reset the velocity counter).
  function change<K extends keyof FormState>(k: K, v: FormState[K]) {
    setForm((f) => {
      const next = { ...f, [k]: v }
      if (k === 'profile' || k === 'scenario') {
        setSsn(genSsn(next.profile, next.scenario))
        setVelocity(0)
      }
      return next
    })
  }

  const cfg: RuleConfig | undefined = live?.config
  const age = ageFrom(form.dateOfBirth)
  const income = Number(form.annualIncome)
  const housing = Number(form.monthlyHousing)
  const debt = Number(form.monthlyDebt)
  const residual = cfg ? income / 12 - housing - debt - (cfg.livingCost ?? 0) : 0
  const atp = cfg ? Math.max(0, Math.floor((residual * (cfg.atpShare ?? 0)) / (cfg.minPayPct ?? 1) / 100) * 100) : 0
  const dti = income > 0 ? ((housing + debt) * 12) / income : 0

  const body = useMemo(
    () => ({
      firstName: form.firstName,
      lastName: form.lastName,
      dateOfBirth: form.dateOfBirth,
      ssn,
      address: form.address,
      annualIncome: income,
      monthlyHousing: housing,
      monthlyDebt: debt,
      independentIncome: form.independentIncome,
      bureauConsent: form.bureauConsent,
      product: form.product as 'REWARDS_CARD' | 'STORE_CARD' | 'HEALTHCARE_CARD',
    }),
    [form, ssn, income, housing, debt],
  )
  const preview = { ...body, ssn: '***-**-' + ssn.slice(-4) }

  function validate(): Record<string, string> {
    const e: Record<string, string> = {}
    if (!form.firstName.trim()) e.firstName = 'Required'
    if (!form.lastName.trim()) e.lastName = 'Required'
    if (!/^\d{9}$/.test(ssn)) e.ssn = 'Enter exactly 9 digits'
    if (!form.dateOfBirth || isNaN(ageFrom(form.dateOfBirth))) e.dateOfBirth = 'Enter a valid date'
    if (!(income > 0)) e.annualIncome = 'Must be greater than 0'
    if (!(housing >= 0)) e.monthlyHousing = 'Must be 0 or more'
    if (!(debt >= 0)) e.monthlyDebt = 'Must be 0 or more'
    if (form.address.trim().length < 6) e.address = 'Enter a full address'
    return e
  }

  const initialSteps = (): PipelineStep[] =>
    STEP_LABELS.map((label, i) => ({
      step: label,
      label: i === 4 ? `Decision engine · ${live?.version || ''}` : label,
      status: 'OK' as const,
      ms: 0,
    }))

  function openDecision(id?: string) {
    closeModal()
    if (id) navigate(`/app/decisions/${id}`)
  }

  async function drive(res: RawResult<SubmitResult>) {
    const replay = res.headers.get('Idempotent-Replay') === 'true'
    if (res.status === 403) {
      closeModal()
      denyToast('UNDERWRITER')
      return
    }
    if (res.status === 400) {
      closeModal()
      const fe = (res.data as unknown as { fieldErrors?: { field: string; message: string }[] })?.fieldErrors || []
      const map: Record<string, string> = {}
      fe.forEach((f) => (map[f.field] = f.message))
      setErrors(map)
      toast('400 Bad Request — fix the highlighted fields', 'bad')
      return
    }
    if (res.status === 422) {
      closeModal()
      toast('<b>422 Unprocessable.</b> This Idempotency-Key was already used with a different request body.', 'bad')
      return
    }
    if (res.status === 409) {
      closeModal()
      toast('<b>409 Conflict</b> for the second click — the first request with this key is still IN_PROGRESS.', 'warn')
      return
    }
    if (res.status === 200 && replay) {
      closeModal()
      toast(
        `<b>200 · Idempotent replay.</b> Same key and body — returned the stored response for ${res.data.applicationId}. No duplicate created.`,
        'ok',
      )
      if (res.data.applicationId) navigate(`/app/decisions/${res.data.applicationId}`)
      return
    }
    // 201 (decided / bureau-unavailable) or 202 (engine pending): reveal the real pipeline.
    const bodyRes = res.data
    const steps = bodyRes.pipeline && bodyRes.pipeline.length ? bodyRes.pipeline : initialSteps()
    for (let i = 1; i <= steps.length; i++) {
      setModalContent(<PipelineFrame steps={steps} revealed={i} result={null} onOpen={openDecision} />)
      await sleep(150)
    }
    const result: Result = {
      outcome: bodyRes.outcome,
      score: bodyRes.score,
      pending: bodyRes.status === 'ENGINE_PENDING',
      appId: bodyRes.applicationId,
    }
    setModalContent(<PipelineFrame steps={steps} revealed={steps.length} result={result} onOpen={openDecision} />)
    setLocked(false)
  }

  async function doSubmit(double: boolean) {
    const errs = validate()
    setErrors(errs)
    if (Object.keys(errs).length) {
      toast('400 Bad Request — fix the highlighted fields', 'bad')
      return
    }
    if (form.scenario === 'vel') setVelocity((v) => v + 1)
    openModal(<PipelineFrame steps={initialSteps()} revealed={0} result={null} onOpen={openDecision} />, { locked: true })

    if (double) {
      const [a, b] = await Promise.all([
        submit.mutateAsync({ body, idempotencyKey: idemKey }),
        submit.mutateAsync({ body, idempotencyKey: idemKey }),
      ])
      const primary = [a, b].find((r) => r.status === 201 || r.status === 202) || a
      const secondary = a === primary ? b : a
      await drive(primary)
      if (secondary.status === 409)
        toast('<b>409 Conflict</b> for the second click — the first request with this key is still IN_PROGRESS.', 'warn')
      else if (secondary.status === 200 && secondary.headers.get('Idempotent-Replay') === 'true')
        toast(
          `<b>200 · Idempotent replay.</b> Same key and body — returned the stored response for ${secondary.data.applicationId}. No duplicate created.`,
          'ok',
        )
    } else {
      const res = await submit.mutateAsync({ body, idempotencyKey: idemKey })
      await drive(res)
    }
  }

  function reset() {
    setForm(DEF)
    setSsn(genSsn(DEF.profile, DEF.scenario))
    setVelocity(0)
    setErrors({})
  }

  const fieldCls = (k: string) => `f ${errors[k] ? 'err' : ''}`

  return (
    <>
      <PageHeader
        eyebrow="New application"
        title="Submit a credit application"
        description="Runs the full pipeline: validation, idempotency, SOAP bureau pull, fraud screen, decision engine and a transactional ledger write."
        right={<LiveChips />}
      />
      <div className="g g21">
        <Card>
          <div className="form">
            <div className="fsec">Applicant</div>
            <div className={fieldCls('firstName')}>
              <label>First name</label>
              <input value={form.firstName} onChange={(e) => change('firstName', e.target.value)} />
              <div className="h">{errors.firstName || ''}</div>
            </div>
            <div className={fieldCls('lastName')}>
              <label>Last name</label>
              <input value={form.lastName} onChange={(e) => change('lastName', e.target.value)} />
              <div className="h">{errors.lastName || ''}</div>
            </div>
            <div className={fieldCls('dateOfBirth')}>
              <label>Date of birth</label>
              <input type="date" value={form.dateOfBirth} onChange={(e) => change('dateOfBirth', e.target.value)} />
              <div className="h">{errors.dateOfBirth || 'Legal capacity: 18+; under 21 needs independent income'}</div>
            </div>
            <div className={fieldCls('ssn')}>
              <label>SSN (synthetic, 9 digits)</label>
              <input value={ssn} onChange={(e) => setSsn(e.target.value)} />
              <div className="h">{errors.ssn || 'Encrypted at rest · masked in logs'}</div>
            </div>
            <div className={`${fieldCls('address')} full`}>
              <label>Home address</label>
              <input value={form.address} onChange={(e) => change('address', e.target.value)} />
              <div className="h">{errors.address || 'Compared against the bureau file address'}</div>
            </div>

            <div className="fsec">Financials (monthly unless noted)</div>
            <div className={fieldCls('annualIncome')}>
              <label>Annual income (USD)</label>
              <input type="number" value={form.annualIncome} onChange={(e) => change('annualIncome', e.target.value)} />
              <div className="h">{errors.annualIncome || ''}</div>
            </div>
            <div className={fieldCls('monthlyHousing')}>
              <label>Housing payment</label>
              <input type="number" value={form.monthlyHousing} onChange={(e) => change('monthlyHousing', e.target.value)} />
              <div className="h">{errors.monthlyHousing || ''}</div>
            </div>
            <div className={fieldCls('monthlyDebt')}>
              <label>Other debt payments</label>
              <input type="number" value={form.monthlyDebt} onChange={(e) => change('monthlyDebt', e.target.value)} />
              <div className="h">{errors.monthlyDebt || ''}</div>
            </div>
            <div className="f">
              <label>Independent income</label>
              <select value={form.independentIncome ? 'yes' : 'no'} onChange={(e) => change('independentIncome', e.target.value === 'yes')}>
                <option value="yes">Yes</option>
                <option value="no">No</option>
              </select>
              <div className="h">Only matters under age 21</div>
            </div>

            <div className="fsec">Product &amp; demo controls</div>
            <div className="f">
              <label>Card product</label>
              <select value={form.product} onChange={(e) => change('product', e.target.value)}>
                {PRODUCTS.map(([k, l]) => (
                  <option key={k} value={k}>
                    {l}
                  </option>
                ))}
              </select>
            </div>
            <div className="f">
              <label>Synthetic bureau profile</label>
              <select value={form.profile} onChange={(e) => change('profile', e.target.value)}>
                {PROFILES.map(([k, l]) => (
                  <option key={k} value={k}>
                    {l}
                  </option>
                ))}
              </select>
              <div className="h">What the mock SOAP bureau returns</div>
            </div>
            <div className="f full">
              <label>Fraud scenario</label>
              <select value={form.scenario} onChange={(e) => change('scenario', e.target.value)}>
                {SCENARIOS.map(([k, l]) => (
                  <option key={k} value={k}>
                    {l}
                  </option>
                ))}
              </select>
              {form.scenario === 'vel' && <div className="h">submission {Math.min(velocity + 1, 3)} of 3</div>}
            </div>
            <label className="chk full">
              <input type="checkbox" checked={form.bureauConsent} onChange={(e) => change('bureauConsent', e.target.checked)} /> Applicant
              consents to a credit bureau inquiry (hard pull).
            </label>
          </div>
          <div className="row" style={{ marginTop: 22 }}>
            <button className="btn" onClick={reset}>
              Reset
            </button>
            <span className="sp" />
            <button className="btn" onClick={() => doSubmit(true)} title="Fires two requests with the same Idempotency-Key">
              Simulate double-click
            </button>
            <button className="btn p" onClick={() => doSubmit(false)}>
              Submit for decision ↗
            </button>
          </div>
        </Card>

        <div>
          <Card style={{ marginBottom: 20 }}>
            <h3>Request</h3>
            <div className="lbl">POST /api/v1/applications</div>
            <div className="note" style={{ margin: '6px 0 12px' }}>
              Idempotency-Key: <span className="mono t-acc">{idemKey}</span>{' '}
              <button className="link" style={{ fontSize: 12 }} onClick={() => setIdemKey(randKey())}>
                new key
              </button>
            </div>
            <div className="code">{JSON.stringify(preview, null, 2)}</div>
          </Card>
          <Card>
            <h3>Live pre-check</h3>
            <div className="chkrow">
              <span>Age at application</span>
              <b>{isNaN(age) ? '—' : age}</b>
            </div>
            <div className="chkrow">
              <span>Residual monthly income</span>
              <b className={residual < 0 ? 't-bad' : ''}>{money(residual)}</b>
            </div>
            <div className="chkrow">
              <span>Max affordable limit</span>
              <b>{money(atp)}</b>
            </div>
            <div className="chkrow">
              <span>Debt-to-income</span>
              <b>{pct(dti, 0)}</b>
            </div>
            <div className="note">Client-side estimate only. The engine is the source of truth.</div>
          </Card>
        </div>
      </div>
    </>
  )
}
