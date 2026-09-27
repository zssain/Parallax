import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './app/AuthProvider'
import { Shell } from './app/Shell'
import { Marketing } from './pages/Marketing'
import { Login } from './pages/Login'
import { Overview } from './screens/Overview'
import { Apply } from './screens/Apply'
import { Decisions } from './screens/Decisions'
import { DecisionDetail } from './screens/DecisionDetail'
import { Queue } from './screens/Queue'
import { Lab } from './screens/Lab'
import { Drift } from './screens/Drift'
import { Ledger } from './screens/Ledger'
import { Assistant } from './screens/Assistant'
import { System } from './screens/System'
import { Accounts } from './screens/Accounts'
import { AccountDetail } from './screens/AccountDetail'
import { Collections } from './screens/Collections'
import type { ReactNode } from 'react'

function RequireAuth({ children }: { children: ReactNode }) {
  const { me } = useAuth()
  if (!me) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Marketing />} />
      <Route path="/login" element={<Login />} />
      <Route
        path="/app"
        element={
          <RequireAuth>
            <Shell />
          </RequireAuth>
        }
      >
        <Route index element={<Overview />} />
        <Route path="apply" element={<Apply />} />
        <Route path="decisions" element={<Decisions />} />
        <Route path="decisions/:id" element={<DecisionDetail />} />
        <Route path="queue" element={<Queue />} />
        <Route path="lab" element={<Lab />} />
        <Route path="drift" element={<Drift />} />
        <Route path="ledger" element={<Ledger />} />
        <Route path="assistant" element={<Assistant />} />
        <Route path="system" element={<System />} />
        <Route path="accounts" element={<Accounts />} />
        <Route path="accounts/:id" element={<AccountDetail />} />
        <Route path="collections" element={<Collections />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
