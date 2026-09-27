import { PageHeader, LiveChips, Card } from '../ui/components'
import { useSystemStatus, useIdempotencyKeys, useBureauPulls, useBureauFault } from '../api/hooks'
import { useToast } from '../app/ToastProvider'
import { cap, fmtTs } from '../ui/format'

export function System() {
  const { toast } = useToast()
  const { data: status } = useSystemStatus({ refetchInterval: 5000 })
  const { data: keys } = useIdempotencyKeys()
  const { data: pulls } = useBureauPulls()
  const fault = useBureauFault()

  const circuit = status?.bureauCircuit || 'CLOSED'
  const services = status?.services || []
  const redecide = status?.redecisionQueue || []

  function setBureau(mode: 'DOWN' | 'NONE') {
    fault.mutate(
      { mode },
      {
        onSuccess: (r) => {
          if (mode === 'DOWN') {
            toast('Bureau is down. Circuit OPEN — submit an application to see the REFER fallback.', 'warn')
          } else if (r.redecided && r.redecided.length) {
            toast(`Circuit CLOSED. Re-decision job processed ${r.redecided.length} application(s): ${r.redecided.join(', ')}.`, 'ok')
          } else {
            toast('Bureau restored. Circuit CLOSED.', 'ok')
          }
        },
      },
    )
  }

  return (
    <>
      <PageHeader
        eyebrow="System"
        title="Health, resilience and plumbing"
        description="Service health, the bureau circuit breaker, automatic re-decisions, idempotency keys and bureau report reuse."
        right={<LiveChips />}
      />
      <div className="g g3">
        <Card>
          <h3>Services</h3>
          {services.map((s) => (
            <div className="svc" key={s.name}>
              <span>
                <i className="dot" style={{ background: s.status === 'UP' ? 'var(--ok)' : 'var(--bad)' }} />
                {s.name}
              </span>
              <span className="t-muted mono">{s.status === 'DOWN' ? 'timeout' : s.latencyMs != null ? `${s.latencyMs} ms` : '—'}</span>
            </div>
          ))}
          <p className="note">From Spring Actuator /health on each service.</p>
        </Card>

        <Card>
          <h3>
            Bureau circuit breaker <span className={`st ${circuit === 'OPEN' ? 'DRAFT' : 'LIVE'}`}>{circuit}</span>
          </h3>
          <p className="t-muted" style={{ lineHeight: 1.6, marginBottom: 16 }}>
            Resilience4j wraps the SOAP call with a timeout and circuit breaker. When OPEN, new applications go to REFER
            (B01) instead of failing, and queue for re-decision.
          </p>
          {circuit === 'CLOSED' ? (
            <button className="btn bad" onClick={() => setBureau('DOWN')}>
              Simulate bureau outage
            </button>
          ) : (
            <button className="btn p" onClick={() => setBureau('NONE')}>
              Restore bureau &amp; run re-decision job
            </button>
          )}
        </Card>

        <Card>
          <h3>
            Re-decision queue <span className="chipbox">{redecide.length}</span>
          </h3>
          {redecide.length ? (
            redecide.map((r) => (
              <div className="svc" key={r.applicationId}>
                <b className="mono">{r.applicationId}</b>
                <span className="t-muted">{r.displayName}</span>
              </div>
            ))
          ) : (
            <div className="empty" style={{ padding: 14 }}>
              Empty
            </div>
          )}
          <p className="note">Bureau-outage REFERs are re-decided automatically once the circuit closes, so they are never a dead end.</p>
        </Card>
      </div>

      <div className="g g2">
        <Card>
          <h3>Idempotency keys</h3>
          {keys && keys.length ? (
            <table>
              <thead>
                <tr>
                  <th>Key</th>
                  <th>State</th>
                  <th>Application</th>
                  <th>Expires</th>
                </tr>
              </thead>
              <tbody>
                {keys.map((k) => (
                  <tr key={k.key}>
                    <td className="mono">{k.key}</td>
                    <td>
                      <span className="pill">{k.state}</span>
                    </td>
                    <td className="mono">{k.applicationId || '—'}</td>
                    <td className="t-muted">{fmtTs(k.expiresAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="empty">No keys yet. Submit an application — try “Simulate double-click”, or resubmit the same form.</div>
          )}
          <p className="note">Unique (client_id, key) + request hash. Same body → stored response; different body → 422; in progress → 409.</p>
        </Card>

        <Card>
          <h3>
            Bureau pulls <span className="lbl">reuse window 30 days</span>
          </h3>
          <table>
            <thead>
              <tr>
                <th>Pull</th>
                <th>Type</th>
                <th>Profile</th>
                <th>SSN</th>
              </tr>
            </thead>
            <tbody>
              {(pulls || []).map((p) => (
                <tr key={p.pullId}>
                  <td className="mono">{p.pullId}</td>
                  <td>
                    <span className="pill">{p.pullType}</span>
                  </td>
                  <td>{p.profile ? cap(p.profile.replace('_', '-')) : '—'}</td>
                  <td className="mono">***-**-{p.ssnLast4}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="note">
            A report pulled within the window is reused and the decision records which pull it used. Soft pulls are reserved
            for prequalification.
          </p>
        </Card>
      </div>
    </>
  )
}
