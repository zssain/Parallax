import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { PageHeader, Card, OutcomePill } from '../ui/components'
import { useReviewQueue, useOverrideStats, useApplication, useSubmitReview } from '../api/hooks'
import { useAuth } from '../app/AuthProvider'
import { useToast } from '../app/ToastProvider'
import { money, pct } from '../ui/format'
import { OVERRIDE_CODES } from '../ui/reasons'
import { ApiError } from '../api/client'
import type { QueueItem } from '../api/types'

export function Queue() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { me } = useAuth()
  const { toast, denyToast } = useToast()

  const { data: queue } = useReviewQueue()
  const { data: stats } = useOverrideStats()
  const submit = useSubmitReview()

  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [decision, setDecision] = useState('APPROVED')
  const [limit, setLimit] = useState('')
  const [code, setCode] = useState('O1')
  const [note, setNote] = useState('')

  const items = queue || []
  const preApp = params.get('app')

  // Preselect the ?app= item or the oldest; keep the selection valid as the queue changes.
  useEffect(() => {
    if (!items.length) {
      setSelectedId(null)
      return
    }
    const stillValid = selectedId && items.some((i) => i.applicationId === selectedId)
    if (!stillValid) {
      const target = (preApp && items.find((i) => i.applicationId === preApp)) || items[0]
      setSelectedId(target.applicationId || null)
    }
  }, [items, preApp, selectedId])

  const selected: QueueItem | undefined = items.find((i) => i.applicationId === selectedId)
  const { data: detail } = useApplication(selectedId || undefined)

  // Reset the form when the selection changes.
  useEffect(() => {
    if (selected) {
      setDecision('APPROVED')
      setLimit(String(selected.suggestedLimit ?? 2000))
      setCode('O1')
      setNote('')
    }
  }, [selectedId, selected])

  const descOf = useMemo(() => {
    const m: Record<string, string> = {}
    detail?.base?.reasonCodes?.forEach((r) => { if (r.code) m[r.code] = r.description || r.code })
    detail?.base?.fraudFlags?.forEach((f) => { if (f.code) m[f.code] = f.description || f.code })
    return m
  }, [detail])

  function selectNext() {
    const idx = items.findIndex((i) => i.applicationId === selectedId)
    const next = items[idx + 1] || items.find((i) => i.applicationId !== selectedId)
    setSelectedId(next?.applicationId || null)
  }

  function record() {
    if (me?.role !== 'UNDERWRITER') return denyToast('UNDERWRITER')
    if (!selected) return
    if (note.trim().length < 10) {
      toast('Add a note of at least 10 characters explaining what you verified', 'bad')
      return
    }
    let creditLimit = 0
    if (decision === 'APPROVED') {
      creditLimit = Number(limit)
      if (!(creditLimit >= 300)) {
        toast('Limit must be at least $300', 'bad')
        return
      }
      if (selected.atpMax != null && creditLimit > selected.atpMax) {
        toast(`Limit exceeds the ability-to-pay maximum of ${money(selected.atpMax)}`, 'bad')
        return
      }
    }
    submit.mutate(
      { id: selected.applicationId!, body: { decision, creditLimit, overrideCode: code, note: note.trim() } },
      {
        onSuccess: () => {
          toast(
            `${selected.applicationId} ${decision === 'APPROVED' ? 'approved' : 'declined'} by ${me?.displayName}. Override written to the ledger.`,
            'ok',
          )
          selectNext()
        },
        onError: (e) => toast(e instanceof ApiError ? e.detail || 'Could not record the decision' : 'Could not record the decision', 'bad'),
      },
    )
  }

  const header = (
    <PageHeader
      eyebrow="Review queue"
      title="Referred applications"
      description="REFERs from the score band, fraud flags or bureau outages. Underwriters approve or decline with a note and an override reason code."
      right={<span className="chipbox">{items.length} open</span>}
    />
  )

  const right = selected ? (
    <Card>
      <h3>
        {selected.applicationId} · {selected.displayName} <OutcomePill outcome="REFER" />
      </h3>
      <div className="g g2" style={{ marginBottom: 10 }}>
        <div>
          <div className="lbl">Score</div>
          <div style={{ fontSize: 26, fontWeight: 700 }}>{selected.score ?? '—'}</div>
        </div>
        <div>
          <div className="lbl">Why it was referred</div>
          <div style={{ marginTop: 6 }}>
            {(selected.reasonCodes || []).slice(0, 4).map((r) => (
              <span key={r} className="pill" title={descOf[r] || r}>
                {r}
              </span>
            ))}
          </div>
        </div>
      </div>
      {(selected.fraudFlags || []).map((r) => (
        <div className="rc f" key={r}>
          <code>{r}</code>
          {descOf[r] || r}
        </div>
      ))}
      {selected.reasonKind === 'bureau' && (
        <div className="rc b">
          <code>B01</code>Bureau was unavailable. This will auto re-decide when the circuit closes — or review manually now.
        </div>
      )}
      <div className="form" style={{ marginTop: 16 }}>
        <div className="f">
          <label>Decision</label>
          <select value={decision} onChange={(e) => setDecision(e.target.value)}>
            <option value="APPROVED">Approve</option>
            <option value="DECLINED">Decline</option>
          </select>
        </div>
        {decision === 'APPROVED' && (
          <div className="f">
            <label>Credit limit</label>
            <input type="number" value={limit} onChange={(e) => setLimit(e.target.value)} />
            <div className="h">Capped at ability-to-pay max {selected.atpMax != null ? money(selected.atpMax) : '(unknown)'}</div>
          </div>
        )}
        <div className="f full">
          <label>Override reason code</label>
          <select value={code} onChange={(e) => setCode(e.target.value)}>
            {OVERRIDE_CODES.map(([k, v]) => (
              <option key={k} value={k}>
                {k} — {v}
              </option>
            ))}
          </select>
        </div>
        <div className="f full">
          <label>Underwriter note (required)</label>
          <textarea rows={3} placeholder="What did you verify and how?" value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
      </div>
      <div className="row" style={{ marginTop: 14 }}>
        <button className="btn" onClick={() => navigate(`/app/decisions/${selected.applicationId}`)}>
          Open full decision
        </button>
        <span className="sp" />
        <button className="btn p" onClick={record}>
          Record decision
        </button>
      </div>
      <p className="note">
        Recorded as a new OVERRIDE ledger entry linked to #{selected.baseSeq}. The original decision is never modified.
      </p>
    </Card>
  ) : (
    <Card>
      <div className="empty">The review queue is empty. 🎉</div>
    </Card>
  )

  return (
    <>
      {header}
      <div className="g g12">
        <div>
          <Card style={{ marginBottom: 20 }}>
            <h3>Queue</h3>
            {items.length ? (
              <table>
                <tbody>
                  {items.map((r) => (
                    <tr
                      key={r.applicationId}
                      className={`cl ${selectedId === r.applicationId ? 'qsel' : ''}`}
                      onClick={() => setSelectedId(r.applicationId || null)}
                    >
                      <td>
                        <b className="mono">{r.applicationId}</b>
                        <br />
                        <small className="t-muted">{r.displayName}</small>
                      </td>
                      <td>{r.score ?? '—'}</td>
                      <td>
                        <span className="pill">{r.reasonKind}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="empty">Empty</div>
            )}
          </Card>
          <Card>
            <h3>Override rate by band</h3>
            <table>
              <thead>
                <tr>
                  <th>Band</th>
                  <th>Refers</th>
                  <th>Overridden to approve</th>
                </tr>
              </thead>
              <tbody>
                {(stats?.bands || []).map((b) => (
                  <tr key={b.band}>
                    <td>{b.band}</td>
                    <td>{b.refers}</td>
                    <td>
                      {b.overriddenToApprove}{' '}
                      {b.refers ? <span className="t-muted">({pct((b.rate as number) || 0)})</span> : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="note">
              A high override rate in one band suggests it is miscalibrated — a signal for the Strategy Lab.
            </p>
          </Card>
        </div>
        {right}
      </div>
    </>
  )
}
