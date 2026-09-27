import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArchArt } from '../ui/ArchArt'
import { DEMO_USERS } from '../app/demoUsers'
import { useAuth } from '../app/AuthProvider'
import { useToast } from '../app/ToastProvider'
import { ApiError } from '../api/client'

export function Login() {
  const navigate = useNavigate()
  const { login } = useAuth()
  const { toast } = useToast()
  const [roleId, setRoleId] = useState('strat')
  const [email, setEmail] = useState(DEMO_USERS[0].email)
  const [password, setPassword] = useState('demo-password')
  const [busy, setBusy] = useState(false)

  function pickRole(id: string) {
    setRoleId(id)
    const u = DEMO_USERS.find((x) => x.id === id)
    if (u) setEmail(u.email)
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
      toast(`Signed in as ${me.displayName} · ${me.role}`, 'ok')
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) toast('Sign-in failed — check the email and password', 'bad')
      else toast('Sign-in failed — check the email and password', 'bad')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div id="login">
      <div className="lg-l">
        <div className="lg-top">
          <div className="wordmark dark">parallax.</div>
          <button className="back" onClick={() => navigate('/')}>
            {'←  Back to website'}
          </button>
        </div>
        <div className="lg-form">
          <div className="lg-meta">
            <div className="eyeb">Your Parallax workspace</div>
            <span>🛡&nbsp; Secure workspace access</span>
          </div>
          <h1>Welcome back.</h1>
          <p>Sign in to continue into your credit decisioning workspace. Demo accounts below let you see each role's view.</p>
          <label className="fl">Demo account</label>
          <div className="roles">
            {DEMO_USERS.map((u) => (
              <button key={u.id} className={roleId === u.id ? 'on' : ''} onClick={() => pickRole(u.id)}>
                <b>{u.name}</b> · {u.role.toLowerCase()}
                <small>{u.desc}</small>
              </button>
            ))}
          </div>
          <label className="fl">Email address</label>
          <input
            className="fin"
            placeholder="you@company.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && doLogin()}
          />
          <label className="fl">Password</label>
          <input
            className="fin"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && doLogin()}
          />
          <button className="btn-teal" onClick={doLogin} disabled={busy}>
            <span>Sign in</span>
            <span>↗</span>
          </button>
        </div>
        <div className="lg-foot">
          <span>© 2026 Parallax</span>
          <span>Privacy&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Terms</span>
        </div>
      </div>
      <div className="lg-r">
        <div className="eyeb">The credit decisioning platform</div>
        <h2>
          Two views.<em>One decision.</em>
        </h2>
        <p>
          Live rules. Candidate rules.
          <br />
          The evidence between them.
        </p>
        <div className="lg-frame">
          <ArchArt uid="login" />
        </div>
        <div className="lg-bot">
          <span>Decide.</span>
          <span>Record.</span>
          <span>Replay.</span>
        </div>
      </div>
    </div>
  )
}
