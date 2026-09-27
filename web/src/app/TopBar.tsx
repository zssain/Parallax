import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Wordmark } from '../ui/brand'
import { Icon } from '../ui/icons'
import { LiveChips } from '../ui/components'
import { useAuth } from './AuthProvider'
import { useTheme, type Theme } from './ThemeProvider'
import { useToast } from './ToastProvider'
import { DEMO_USERS } from './demoUsers'
import { activeMeta } from './nav'
import { cap } from '../ui/format'

export function TopBar({ onOpenPalette }: { onOpenPalette: () => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { me, switchUser, logout } = useAuth()
  const { theme, setTheme, isDark } = useTheme()
  const { toast } = useToast()
  const [menu, setMenu] = useState(false)

  // Close the account menu on any outside click.
  useEffect(() => {
    if (!menu) return
    const close = () => setMenu(false)
    document.addEventListener('click', close)
    return () => document.removeEventListener('click', close)
  }, [menu])

  const { meta, detail } = activeMeta(location.pathname)
  const name = me?.displayName || ''
  const role = me?.role || ''

  async function onSwitch(email: string) {
    setMenu(false)
    const next = await switchUser(email)
    toast(`Now acting as ${next.displayName} · ${next.role}`)
    navigate('/app')
  }

  return (
    <header className="bar">
      <Wordmark markSize={22} onClick={() => navigate('/app')} />
      <div className="crumb">
        {meta.idx} / <b>{meta.title}{detail ? ` · ${detail}` : ''}</b>
      </div>
      <span className="sp" />
      <button className="kbtn" onClick={onOpenPalette}>
        <Icon name="search" size={15} />
        Search or jump to…
        <kbd>⌘K</kbd>
      </button>
      <LiveChips />
      <button className="icb" title="Toggle theme" onClick={() => setTheme(isDark ? 'light' : 'dark')}>
        <Icon name={isDark ? 'sun' : 'moon'} size={17} />
      </button>
      <div
        className="who"
        onClick={(e) => {
          e.stopPropagation()
          setMenu((m) => !m)
        }}
      >
        <span className="av">{name[0]}</span>
        <span className="who-t">
          <b>{name}</b>
          <small>{cap(role)}</small>
        </span>
      </div>
      {menu && (
        <div className="menu" onClick={(e) => e.stopPropagation()}>
          <div className="lbl" style={{ padding: '8px 10px 4px' }}>
            Act as
          </div>
          {DEMO_USERS.map((u) => (
            <button key={u.id} className={`mi ${u.email === me?.username ? 'on' : ''}`} onClick={() => onSwitch(u.email)}>
              <span className="av">{u.name[0]}</span>
              <span>
                <b>{u.name}</b>
                <small>{cap(u.role)}</small>
              </span>
            </button>
          ))}
          <div className="msep" />
          <div className="lbl" style={{ padding: '6px 10px' }}>
            Theme
          </div>
          <div className="seg">
            {(['light', 'system', 'dark'] as Theme[]).map((t) => (
              <button key={t} className={theme === t ? 'on' : ''} onClick={() => setTheme(t)}>
                {t}
              </button>
            ))}
          </div>
          <div className="msep" />
          <button
            className="mi"
            onClick={() => {
              setMenu(false)
              logout()
              navigate('/')
            }}
          >
            <Icon name="out" size={16} />
            <span>Sign out</span>
          </button>
        </div>
      )}
    </header>
  )
}
