import { useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { TopBar } from './TopBar'
import { Dock } from './Dock'
import { Palette } from './Palette'
import { useTheme } from './ThemeProvider'

export function Shell() {
  const { isDark } = useTheme()
  const [palette, setPalette] = useState(false)

  // ⌘K / Ctrl-K toggles the command palette; Esc closes it.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPalette((p) => !p)
      } else if (e.key === 'Escape') {
        setPalette(false)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  return (
    <div className={`app ${isDark ? 'dark' : ''}`}>
      <TopBar onOpenPalette={() => setPalette(true)} />
      <main className="main">
        <Outlet />
      </main>
      <Dock />
      {palette && <Palette onClose={() => setPalette(false)} />}
    </div>
  )
}
