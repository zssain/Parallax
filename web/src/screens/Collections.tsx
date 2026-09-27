import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader, Card } from '../ui/components'
import { useCollectionsSummary, useCollections, useCollectionAction } from '../api/hooks'
import { useModal } from '../app/ModalProvider'
import { useToast } from '../app/ToastProvider'
import { fmt, fmtTs, moneyCents } from '../ui/format'

const PRIORITY_CLASS: Record<string, string> = { HIGH: 'hi', MEDIUM: 'med', LOW: 'low' }
const ACTION_LABEL: Record<string, string> = { PAYMENT_PLAN_OFFERED: 'Payment plan', CONTACTED: 'Contact' }

function ActionModal({
  account,
  actionType,
  onSubmit,
  onClose,
}: {
  account: string
  actionType: string
  onSubmit: (note: string) => void
  onClose: () => void
}) {
  const [note, setNote] = useState('')
  return (
    <>
      <h3>{actionType === 'PAYMENT_PLAN_OFFERED' ? 'Offer payment plan' : 'Log contact'}</h3>
      <p className="t-muted" style={{ marginBottom: 12 }}>
        {account}
      </p>
      <div className="f">
        <label>Note (required)</label>
        <textarea rows={3} placeholder="What was agreed or attempted?" value={note} onChange={(e) => setNote(e.target.value)} />
      </div>
      <div className="row" style={{ marginTop: 14 }}>
        <button className="btn" onClick={onClose}>
          Cancel
        </button>
        <span className="sp" />
        <button className="btn p" disabled={!note.trim()} onClick={() => onSubmit(note.trim())}>
          Record
        </button>
      </div>
    </>
  )
}

export function Collections() {
  const navigate = useNavigate()
  const { openModal, closeModal } = useModal()
  const { toast } = useToast()
  const { data: summary } = useCollectionsSummary()
  const [bucket, setBucket] = useState('ALL')
  const { data: queue } = useCollections(bucket)
  const action = useCollectionAction()

  const buckets = summary?.buckets || []
  const items = queue || []

  function openAction(accountId: string, actionType: string) {
    openModal(
      <ActionModal
        account={accountId}
        actionType={actionType}
        onClose={closeModal}
        onSubmit={(note) =>
          action.mutate(
            { accountId, body: { type: actionType, note } },
            {
              onSuccess: () => {
                toast(`${ACTION_LABEL[actionType]} recorded for ${accountId}.`, 'ok')
                closeModal()
              },
            },
          )
        }
      />,
    )
  }

  return (
    <>
      <PageHeader eyebrow="Lifecycle" title="Collections" description="Delinquent accounts by bucket, prioritised for outreach." />
      <div className="kpis k4">
        {buckets.map((b) => (
          <div
            key={b.bucket}
            className="kpi"
            style={{ cursor: 'pointer', background: bucket === b.bucket ? 'var(--accbg)' : undefined }}
            onClick={() => setBucket(bucket === b.bucket ? 'ALL' : b.bucket || 'ALL')}
          >
            <div className="lbl">{b.bucket} DPD</div>
            <div className="v">{fmt(b.count || 0)}</div>
            <small>{moneyCents(b.amountDueCents || 0)} due</small>
          </div>
        ))}
      </div>

      <Card>
        <h3>
          Work queue{' '}
          {bucket !== 'ALL' && (
            <button className="link" onClick={() => setBucket('ALL')}>
              clear filter ({bucket})
            </button>
          )}
        </h3>
        {items.length ? (
          <table>
            <thead>
              <tr>
                <th>Account</th>
                <th>Applicant</th>
                <th>DPD</th>
                <th>Bucket</th>
                <th>Amount due</th>
                <th>Balance</th>
                <th>Last contact</th>
                <th>Priority</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((c) => (
                <tr key={c.accountId}>
                  <td className="mono cl" onClick={() => navigate(`/app/accounts/${c.accountId}`)}>
                    <b>{c.accountId}</b>
                  </td>
                  <td>{c.displayName}</td>
                  <td className="t-bad">{c.daysPastDue}</td>
                  <td>{c.bucket}</td>
                  <td>{moneyCents(c.amountDueCents || 0)}</td>
                  <td>{moneyCents(c.balanceCents || 0)}</td>
                  <td className="t-muted">{c.lastContactAt ? fmtTs(c.lastContactAt) : '—'}</td>
                  <td>
                    <span className={`pill ${PRIORITY_CLASS[c.priority || 'LOW']}`}>{c.priority}</span>
                  </td>
                  <td>
                    <div className="row">
                      <button className="btn sm" onClick={() => openAction(c.accountId!, 'PAYMENT_PLAN_OFFERED')}>
                        Offer payment plan
                      </button>
                      <button className="btn sm" onClick={() => openAction(c.accountId!, 'CONTACTED')}>
                        Log contact
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="empty">No delinquent accounts. Miss a payment under Accounts → Simulate a month to see one here.</div>
        )}
      </Card>
    </>
  )
}
