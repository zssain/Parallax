import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { sanitizeInline } from '../ui/safeMarkup'

export type ToastKind = '' | 'ok' | 'bad' | 'warn'

interface ToastValue {
  toast: (message: string, kind?: ToastKind) => void
  /** SPEC §16 denial message when the API returns 403 for a role-gated action. */
  denyToast: (role: string) => void
}

const Ctx = createContext<ToastValue | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState('')
  const [kind, setKind] = useState<ToastKind>('')
  const [on, setOn] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const toast = useCallback((message: string, k: ToastKind = '') => {
    setMsg(message)
    setKind(k)
    setOn(true)
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(() => setOn(false), 4200)
  }, [])

  const denyToast = useCallback(
    (role: string) => toast(`This action requires the <b>${role}</b> role. Switch user from the account menu.`, 'bad'),
    [toast],
  )

  return (
    <Ctx.Provider value={{ toast, denyToast }}>
      {children}
      <div className={`toast ${on ? 'on' : ''} ${kind}`} dangerouslySetInnerHTML={{ __html: sanitizeInline(msg) }} />
    </Ctx.Provider>
  )
}

export function useToast(): ToastValue {
  const v = useContext(Ctx)
  if (!v) throw new Error('useToast must be used within ToastProvider')
  return v
}
