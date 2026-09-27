import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader, Card, OutcomePill } from '../ui/components'
import { useLedger, useLedgerStats, useVerifyChain, useAttemptUpdate, useTamperTest } from '../api/hooks'
import { useToast } from '../app/ToastProvider'
import { fmtTs } from '../ui/format'

export function Ledger() {
  const navigate = useNavigate()
  const { toast } = useToast()
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useLedger('ALL')
  const { data: stats } = useLedgerStats()
  const verify = useVerifyChain()
  const attempt = useAttemptUpdate()
  const tamper = useTamperTest()

  const [result, setResult] = useState<ReactNode>(null)

  const rows = (data?.pages || []).flatMap((p) => p.items)

  function doVerify() {
    verify.mutate(undefined, {
      onSuccess: (r) => {
        setResult(
          <Card style={{ marginBottom: 20, borderColor: r.ok ? 'var(--ok)' : 'var(--bad)' }}>
            <b className={r.ok ? 't-ok' : 't-bad'}>
              {r.ok ? `✓ Chain intact — ${r.checked} records verified from genesis` : `✗ Chain broken at seq #${r.brokenAtSeq}`}
            </b>
          </Card>,
        )
        toast(r.ok ? 'Chain verified ✓' : 'Chain broken', r.ok ? 'ok' : 'bad')
      },
    })
  }
  function doAttempt() {
    attempt.mutate(undefined, {
      onSuccess: (d) => {
        setResult(
          <Card style={{ marginBottom: 20 }}>
            <div className="code">{`parallax=> ${d.statement};\n${d.error}\n-- role parallax_app has INSERT and SELECT only`}</div>
          </Card>,
        )
      },
    })
  }
  function doTamper() {
    tamper.mutate(undefined, {
      onSuccess: (t) => {
        setResult(
          <Card style={{ marginBottom: 20, borderColor: 'var(--bad)' }}>
            <b>Tamper test on a copy:</b> changed seq #{t.modifiedSeq} to APPROVED with a $25,000 limit, bypassing the
            database.
            <br />
            <b className="t-bad">Verification: chain breaks at seq #{t.brokenAtSeq}</b> — the stored hash no longer matches
            the record's content, and every later link depends on it.
          </Card>,
        )
      },
    })
  }

  return (
    <>
      <PageHeader
        eyebrow="Decision ledger"
        title="Append-only, hash-chained"
        description="Every decision, override, re-decision and promotion. The service role has no UPDATE or DELETE grant; each record stores the hash of the one before it."
        right={
          <>
            <button className="btn" onClick={doAttempt}>
              Attempt UPDATE
            </button>
            <button className="btn" onClick={doTamper}>
              Tamper test
            </button>
            <button className="btn p" onClick={doVerify}>
              Verify chain
            </button>
          </>
        }
      />
      <div className="kpis k4">
        <div className="kpi">
          <div className="lbl">Records</div>
          <div className="v">{stats?.records ?? '—'}</div>
          <small>all kinds</small>
        </div>
        <div className="kpi">
          <div className="lbl">Decisions</div>
          <div className="v">{stats?.decisions ?? '—'}</div>
          <small>engine outcomes</small>
        </div>
        <div className="kpi">
          <div className="lbl">Overrides</div>
          <div className="v">{stats?.overrides ?? '—'}</div>
          <small>underwriter reviews</small>
        </div>
        <div className="kpi">
          <div className="lbl">Governance</div>
          <div className="v">{stats?.governance ?? '—'}</div>
          <small>promotions &amp; rollbacks</small>
        </div>
      </div>

      {result}

      <Card>
        <table>
          <thead>
            <tr>
              <th>Seq</th>
              <th>Kind</th>
              <th>Subject</th>
              <th>Outcome</th>
              <th>Rules</th>
              <th>Time</th>
              <th>prev → hash</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr
                key={r.seq}
                className={r.applicationId ? 'cl' : ''}
                onClick={() => r.applicationId && navigate(`/app/decisions/${r.applicationId}`)}
              >
                <td className="mono">#{r.seq}</td>
                <td>
                  <span className="pill">{r.kind}</span>
                </td>
                <td>
                  {r.applicationId ? (
                    <>
                      <b className="mono">{r.applicationId}</b> <small className="t-muted">{r.displayName}</small>
                    </>
                  ) : (
                    <small>{r.note}</small>
                  )}
                </td>
                <td>{r.outcome ? <OutcomePill outcome={r.outcome} /> : '—'}</td>
                <td className="mono">{r.ruleVersion || '—'}</td>
                <td className="t-muted">{fmtTs(r.createdAt)}</td>
                <td className="hash">
                  {(r.prevHash || '').slice(0, 8)}… → {(r.hash || '').slice(0, 8)}…
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="row" style={{ marginTop: 14 }}>
          {hasNextPage && (
            <button className="btn" onClick={() => fetchNextPage()} disabled={isFetchingNextPage}>
              {isFetchingNextPage ? 'Loading…' : 'Load more'}
            </button>
          )}
          <span className="sp" />
        </div>
        <p className="note">Hashes are SHA-256 over canonical JSON; inserts are serialized by a Postgres advisory lock.</p>
      </Card>
    </>
  )
}
