import { useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader, Kpi, OutcomePill } from '../ui/components'
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
  // Chain head: the latest links, oldest → newest (rows come back newest-first).
  const tail = rows.slice(0, 6).reverse()

  function doVerify() {
    verify.mutate(undefined, {
      onSuccess: (r) => {
        setResult(
          <div className="tile w fade" style={{ marginBottom: 14, borderColor: r.ok ? 'var(--moss)' : 'var(--rust)' }}>
            <b className={r.ok ? 't-ok' : 't-bad'}>
              {r.ok ? `✓ Chain intact — ${r.checked} records verified from genesis` : `✕ Chain broken at seq #${r.brokenAtSeq}`}
            </b>
          </div>,
        )
        toast(r.ok ? 'Chain verified ✓' : 'Chain broken', r.ok ? 'ok' : 'bad')
      },
    })
  }
  function doAttempt() {
    attempt.mutate(undefined, {
      onSuccess: (d) => {
        setResult(
          <div className="tile w fade" style={{ marginBottom: 14 }}>
            <div className="code">{`parallax=> ${d.statement};\n${d.error}\n-- role parallax_app has INSERT and SELECT only`}</div>
          </div>,
        )
      },
    })
  }
  function doTamper() {
    tamper.mutate(undefined, {
      onSuccess: (t) => {
        setResult(
          <div className="tile w fade" style={{ marginBottom: 14, borderColor: 'var(--rust)' }}>
            <b>Tamper test on a copy:</b> changed seq #{t.modifiedSeq} to APPROVED with a $25,000 limit, bypassing the
            database.
            <br />
            <b className="t-bad">Verification: chain breaks at seq #{t.brokenAtSeq}</b> — the stored hash no longer matches
            the record's content, and every later link depends on it.
          </div>,
        )
      },
    })
  }

  return (
    <>
      <PageHeader
        title={
          <>
            Decision <em>ledger</em>
          </>
        }
        description="Append-only and hash-chained. The service role has no UPDATE or DELETE grant; each record stores the hash of the one before it."
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

      <div className="g" style={{ gridTemplateColumns: 'repeat(4,1fr)' }}>
        <Kpi label="Records" value={stats?.records ?? '—'} sub="all kinds" />
        <Kpi label="Decisions" value={stats?.decisions ?? '—'} sub="engine outcomes" />
        <Kpi label="Overrides" value={stats?.overrides ?? '—'} sub="underwriter reviews" />
        <Kpi label="Governance" value={stats?.governance ?? '—'} sub="promotions & rollbacks" />
      </div>

      <div className="tile" style={{ marginBottom: 14 }}>
        <div className="th">
          <h3>Chain head</h3>
          <span className="lbl">latest {tail.length} links</span>
        </div>
        <div className="chain">
          {tail.map((r, i) => (
            <div key={r.seq} style={{ display: 'contents' }}>
              {i ? <span className="lnk" /> : null}
              <div className="blk">
                <div className="k">{r.kind}</div>
                <b>#{r.seq}</b>
                <div>prev {(r.prevHash || '').slice(0, 8)}</div>
                <div style={{ color: 'var(--ink)' }}>hash {(r.hash || '').slice(0, 8)}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div id="vres">{result}</div>

      <div className="tile w">
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
                  <span className="pill2">{r.kind}</span>
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
                <td className="t-muted mono">{fmtTs(r.createdAt)}</td>
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
        <p className="note">
          Demo uses an FNV-based 64-bit hash in the browser; the real service uses SHA-256 with inserts serialized by a
          Postgres advisory lock.
        </p>
      </div>
    </>
  )
}
