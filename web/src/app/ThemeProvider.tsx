import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

export type Theme = 'light' | 'system' | 'dark'

interface ThemeValue {
  theme: Theme
  setTheme: (t: Theme) => void
  isDark: boolean
}

const Ctx = createContext<ThemeValue | null>(null)

const prefersDark = () => window.matchMedia('(prefers-color-scheme: dark)').matches

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>('light')
  const [systemDark, setSystemDark] = useState(prefersDark)

  // Follow the OS preference (and its change event) while on 'system'.
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setSystemDark(mq.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  const isDark = theme === 'dark' || (theme === 'system' && systemDark)

  const setTheme = useCallback((t: Theme) => setThemeState(t), [])

  // Let the modal (which lives outside .app) re-sync its theme variables.
  useEffect(() => {
    window.dispatchEvent(new Event('parallax-theme'))
  }, [isDark])

  const value = useMemo(() => ({ theme, setTheme, isDark }), [theme, setTheme, isDark])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useTheme(): ThemeValue {
  const v = useContext(Ctx)
  if (!v) throw new Error('useTheme must be used within ThemeProvider')
  return v
}
