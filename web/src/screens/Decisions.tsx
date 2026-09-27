import { useEffect, useState } from 'react'
import { PageHeader, LiveChips, Card, DecisionTable } from '../ui/components'
import { useDecisions } from '../api/hooks'
import { useAuth } from '../app/AuthProvider'
import { cap } from '../ui/format'

const FILTERS = ['ALL', 'APPROVED', 'REFER', 'DECLINED'] as const

export function Decisions() {
  const { me } = useAuth()
  const [outcome, setOutcome] = useState<string>('ALL')
  const [input, setInput] = useState('')
  const [q, setQ] = useState('')

  // Debounce the search box 250 ms (the API searches APP-IDs only).
  useEffect(() => {
    const t = setTimeout(() => setQ(input.trim()), 250)
    return () => clearTimeout(t)
  }, [input])

  const { data } = useDecisions({ outcome, q, size: 100 })
  const counts = data?.counts
  const label = (o: string) => (o === 'ALL' ? 'All' : cap(o))
  const count = (o: string) => (counts ? counts[o as keyof typeof counts] : 0)

  return (
    <>
      <PageHeader
        eyebrow="Decisions"
        title="Every application, every outcome"
        description="The current state of each application. Click a row to see how the engine decided, reproduce it, or read its ledger trail."
        right={<LiveChips />}
      />
      <Card>
        <div className="row" style={{ marginBottom: 14 }}>
          {FILTERS.map((o) => (
            <button key={o} className={`btn sm ${outcome === o ? 'p' : ''}`} onClick={() => setOutcome(o)}>
              {label(o)} · {count(o)}
            </button>
          ))}
          <span className="sp" />
          <input
            className="usel"
            style={{ width: 260, margin: 0, padding: '10px 12px', fontSize: 14 }}
            placeholder="Search APP-ID"
            value={input}
            onChange={(e) => setInput(e.target.value)}
          />
        </div>
        <DecisionTable rows={data?.items ?? []} />
        {me?.role === 'AUDITOR' && <div className="note">Names are masked for the AUDITOR role.</div>}
      </Card>
    </>
  )
}
