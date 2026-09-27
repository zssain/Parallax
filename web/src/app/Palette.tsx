import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { NAV_ALL } from './nav'
import { useTheme } from './ThemeProvider'
import { useVersions } from '../api/hooks'

interface PalItem {
  group: string
  title: string
  sub?: string
  run: () => void
}

/** The prototype's ⌘K command palette: jump to a page, a rule version, an application, or an action. */
export function Palette({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate()
  const { isDark, setTheme } = useTheme()
  const { data: versions } = useVersions()
  const [q, setQ] = useState('')
  const [i, setI] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    setTimeout(() => inputRef.current?.focus(), 20)
  }, [])

  const items = useMemo<PalItem[]>(() => {
    const all: PalItem[] = [
      ...NAV_ALL.map((n) => ({ group: 'Go to', title: n.title, sub: n.idx, run: () => navigate(n.path) })),
      ...(versions?.items ?? []).map((v) => ({
        group: 'Rule versions',
        title: `${v.version} · ${v.note ?? ''}`,
        sub: v.status,
        run: () => navigate(`/app/lab?v=${v.version}`),
      })),
      { group: 'Actions', title: 'Toggle dark mode', run: () => setTheme(isDark ? 'light' : 'dark') },
      { group: 'Actions', title: 'Verify ledger chain', run: () => navigate('/app/ledger') },
      { group: 'Actions', title: 'Ask the assistant', run: () => navigate('/app/assistant') },
    ]
    const m = q.toUpperCase().match(/APP-\d+/)
    if (m) all.unshift({ group: 'Applications', title: `Open ${m[0]}`, run: () => navigate(`/app/decisions/${m[0]}`) })
    const query = q.toLowerCase()
    return all.filter((x) => (x.title + ' ' + x.group).toLowerCase().includes(query)).slice(0, 14)
  }, [q, versions, isDark, navigate, setTheme])

  const idx = Math.min(i, Math.max(0, items.length - 1))

  function run(n: number) {
    const x = items[n]
    onClose()
    x?.run()
  }
  function onKey(e: React.KeyboardEvent) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setI((v) => Math.min(items.length - 1, v + 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setI((v) => Math.max(0, v - 1))
    } else if (e.key === 'Enter') {
      run(idx)
    } else if (e.key === 'Escape') {
      onClose()
    }
  }

  let lastGroup = ''
  return (
    <div className="pal" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="pal-box">
        <input
          ref={inputRef}
          value={q}
          placeholder="Jump to a page, application or rule version…"
          onChange={(e) => {
            setQ(e.target.value)
            setI(0)
          }}
          onKeyDown={onKey}
        />
        <div id="pall">
          {items.length ? (
            items.map((x, n) => {
              const header = x.group !== lastGroup ? <div className="pg">{x.group}</div> : null
              lastGroup = x.group
              return (
                <div key={n}>
                  {header}
                  <div className={`pit ${n === idx ? 'on' : ''}`} onMouseEnter={() => setI(n)} onClick={() => run(n)}>
                    {x.title}
                    <span>{x.sub}</span>
                  </div>
                </div>
              )
            })
          ) : (
            <div className="empty">Nothing matches.</div>
          )}
        </div>
        <div className="pal-f">↑ ↓ navigate · ↵ open · esc close</div>
      </div>
    </div>
  )
}
