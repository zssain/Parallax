import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../api/client'
import { authStore } from '../auth/authStore'
import type { Me } from '../api/types'
import { DEMO_PASSWORD } from './demoUsers'

interface AuthValue {
  me: Me | null
  login: (email: string, password: string) => Promise<Me>
  switchUser: (email: string) => Promise<Me>
  logout: () => void
}

const Ctx = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const navigate = useNavigate()
  const qc = useQueryClient()

  // Global 401 handling: a stored identity that the server rejects is signed out.
  useEffect(() => {
    authStore.setUnauthorizedHandler(() => {
      setMe(null)
      qc.clear()
      navigate('/login')
    })
  }, [navigate, qc])

  const login = useCallback(
    async (email: string, password: string): Promise<Me> => {
      // Validate the identity against GET /me with these explicit credentials.
      const result = await apiFetch<Me>('/api/v1/me', { auth: { username: email, password } })
      authStore.set({ username: email, password })
      setMe(result)
      qc.clear()
      return result
    },
    [qc],
  )

  const switchUser = useCallback((email: string) => login(email, DEMO_PASSWORD), [login])

  const logout = useCallback(() => {
    authStore.clear()
    setMe(null)
    qc.clear()
  }, [qc])

  return <Ctx.Provider value={{ me, login, switchUser, logout }}>{children}</Ctx.Provider>
}

export function useAuth(): AuthValue {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth must be used within AuthProvider')
  return v
}
