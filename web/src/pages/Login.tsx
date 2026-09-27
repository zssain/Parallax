import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Wordmark } from '../ui/brand'
import { DEMO_USERS } from '../app/demoUsers'
import { useAuth } from '../app/AuthProvider'
import { useToast } from '../app/ToastProvider'
import { ApiError } from '../api/client'
import { cap } from '../ui/format'

export function Login() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const { toast } = useToast()
  const [roleId, setRoleId] = useState('strat')
  const [email, setEmail] = useState(DEMO_USERS[0].email)
  const [password, setPassword] = useState('demo-password')
  const [busy, setBusy] = useState(false)

  const u = DEMO_USERS.find((x) => x.id === roleId) ?? DEMO_USERS[0]

  function pickRole(id: string) {
    setRoleId(id)
    const next = DEMO_USERS.find((x) => x.id === id)
    if (next) setEmail(next.email)
  }

  async function doLogin() {
    const e = email.trim()
    if (!e) {
      toast('Enter an email address', 'bad')
      return
    }
    setBusy(true)
    try {
      const me = await login(e, password)
      navigate('/app')
      toast(`Signed in as ${me.displayName} · ${me.role} · press ⌘K / Ctrl K to jump anywhere`, 'ok')
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) toast('Sign-in failed — check the email and password', 'bad')
      else toast('Sign-in failed — check the email and password', 'bad')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div id="login">
      <div className="lg-wm">parallax</div>
      <header className="lg-h">
        <Wordmark markSize={22} onClick={() => navigate('/')} />
        <button className="lg-back" onClick={() => navigate('/')}>
          ← Back to site
        </button>
      </header>
      <div className="lg-card">
        <div className="kick">Demo workspace · synthetic data</div>
        <h1>
          Sign in to
          <br />
          <em>the ledger.</em>
        </h1>
        <div className="lbl" style={{ fontFamily: 'var(--mono)', fontSize: 10.5, letterSpacing: '.14em', color: 'var(--mut)', marginBottom: 10 }}>
          CHOOSE A ROLE
        </div>
        <div className="rtabs">
          {DEMO_USERS.map((x) => (
            <button key={x.id} className={x.id === roleId ? 'on' : ''} onClick={() => pickRole(x.id)}>
              {cap(x.role)}
            </button>
          ))}
        </div>
        <div className="perm">
          <div className="pn">
            <span className="av">{u.name[0]}</span>
            <div>
              <b>{u.name}</b>
              <small>{u.desc}</small>
            </div>
          </div>
          <div className="cols">
            <div>
              <h5>CAN</h5>
              <ul>
                {u.can.map((c) => (
                  <li key={c} className="y">
                    {c}
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <h5>CANNOT</h5>
              <ul>
                {u.cannot.map((c) => (
                  <li key={c} className="n">
                    {c}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
        <div className="lf">
          <label>Email</label>
          <input placeholder="you@company.com" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="lf">
          <label>Password</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && doLogin()}
          />
        </div>
        <button className="pill lg-go" onClick={doLogin} disabled={busy}>
          <span>Enter workspace</span>
          <span>→</span>
        </button>
        <div className="lg-note">You can switch roles any time from the account menu</div>
      </div>
    </div>
  )
}
