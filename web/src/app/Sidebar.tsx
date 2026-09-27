import { useLocation, useNavigate } from 'react-router-dom'
import { Icon } from '../ui/icons'
import { useAuth } from './AuthProvider'
import { useTheme, type Theme } from './ThemeProvider'
import { useToast } from './ToastProvider'
import { DEMO_USERS } from './demoUsers'
import { useReviewQueue, useVersions } from '../api/hooks'
import { cap } from '../ui/format'

interface NavItem {
  key: string
  label: string
  path: string
}
const NAV: { section: string; items: NavItem[] }[] = [
  {
    section: 'Workspace',
    items: [
      { key: 'overview', label: 'Overview', path: '/app' },
      { key: 'apply', label: 'New application', path: '/app/apply' },
      { key: 'decisions', label: 'Decisions', path: '/app/decisions' },
      { key: 'queue', label: 'Review queue', path: '/app/queue' },
    ],
  },
  {
    section: 'Strategy',
    items: [
      { key: 'lab', label: 'Strategy Lab', path: '/app/lab' },
      { key: 'drift', label: 'Drift monitor', path: '/app/drift' },
    ],
  },
  {
    section: 'Governance',
    items: [
      { key: 'ledger', label: 'Decision ledger', path: '/app/ledger' },
      { key: 'assistant', label: 'Assistant', path: '/app/assistant' },
      { key: 'system', label: 'System', path: '/app/system' },
    ],
  },
  {
    section: 'Lifecycle',
    items: [
      { key: 'accounts', label: 'Accounts', path: '/app/accounts' },
      { key: 'collections', label: 'Collections', path: '/app/collections' },
    ],
  },
]

function isActive(pathname: string, item: NavItem): boolean {
  if (item.path === '/app') return pathname === '/app' || pathname === '/app/'
  // Decision detail highlights Decisions; account detail highlights Accounts.
  return pathname === item.path || pathname.startsWith(item.path + '/')
}

export function Sidebar({ onToggleCollapse }: { onToggleCollapse: () => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  const { me, switchUser, logout } = useAuth()
  const { theme, setTheme } = useTheme()
  const { toast } = useToast()

  const { data: queue } = useReviewQueue({ refetchInterval: 30000 })
  const { data: versions } = useVersions()
  const queueCount = queue?.length ?? 0
  const hasProposed = versions?.items?.some((v) => v.status === 'PROPOSED') ?? false

  const name = me?.displayName || ''
  const role = me?.role || ''

  async function onSwitch(email: string) {
    const next = await switchUser(email)
    toast(`Now acting as ${next.displayName} · ${next.role}`)
    navigate('/app')
  }

  return (
    <aside className="side">
      <div className="side-top">
        <div className="wordmark">parallax.</div>
        <button className="icbtn" title="Collapse" onClick={onToggleCollapse}>
          <Icon name="collapse" />
        </button>
      </div>
      {NAV.map(({ section, items }) => (
        <div key={section}>
          <div className="navsec">{section.toUpperCase()}</div>
          {items.map((item) => (
            <div
              key={item.key}
              className={`navi ${isActive(location.pathname, item) ? 'on' : ''}`}
              onClick={() => navigate(item.path)}
              title={item.label}
            >
              <Icon name={item.key} />
              <span>{item.label}</span>
              {item.key === 'queue' && queueCount > 0 && <em className="badge">{queueCount}</em>}
              {item.key === 'lab' && hasProposed && <em className="badge">1</em>}
            </div>
          ))}
        </div>
      ))}
      <div className="ucard">
        <div className="urow">
          <div className="av">{name[0]}</div>
          <div className="uinfo">
            <b>{name}</b>
            <small>{cap(role)}</small>
          </div>
          <button
            className="icbtn"
            title="Sign out"
            onClick={() => {
              logout()
              navigate('/')
            }}
          >
            <Icon name="out" />
          </button>
        </div>
        <select className="usel" value="" onChange={(e) => e.target.value && onSwitch(e.target.value)}>
          <option value="">Switch user…</option>
          {DEMO_USERS.map((u) => (
            <option key={u.id} value={u.email}>
              Switch user: {u.name} ({u.role})
            </option>
          ))}
        </select>
        <div className="theme">
          {(['light', 'system', 'dark'] as Theme[]).map((t) => (
            <button key={t} className={theme === t ? 'on' : ''} onClick={() => setTheme(t)} title={t}>
              <Icon name={t === 'light' ? 'sun' : t === 'system' ? 'mon' : 'moon'} size={16} />
            </button>
          ))}
        </div>
      </div>
    </aside>
  )
}
