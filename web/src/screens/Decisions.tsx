import { useEffect, useState } from 'react'
import { PageHeader, LiveChips, Card, DecisionTable, OCM } from '../ui/components'
import { useDecisions } from '../api/hooks'
import { useAuth } from '../app/AuthProvider'

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
  const label = (o: string) => (o === 'ALL' ? 'All' : OCM[o][1])
  const count = (o: string) => (counts ? counts[o as keyof typeof counts] : 0)

  return (
    <>
      <PageHeader
        title="Decisions"
        description="Every application and where it landed. Open one to see exactly how the engine decided."
        right={<LiveChips />}
      />
      <Card>
        <div className="row" style={{ marginBottom: 12 }}>
          <div className="utabs">
            {FILTERS.map((o) => (
              <button key={o} className={outcome === o ? 'on' : ''} onClick={() => setOutcome(o)}>
                {label(o)}
                <span>{count(o)}</span>
              </button>
            ))}
          </div>
          <span className="sp" />
          <input
            className="search"
            placeholder="Search name or APP-ID"
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
