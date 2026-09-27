import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { useTheme } from './ThemeProvider'

export function Shell() {
  const [collapsed, setCollapsed] = useState(false)
  const { isDark } = useTheme()
  return (
    <div className={`app ${isDark ? 'dark' : ''} ${collapsed ? 'collapsed' : ''}`}>
      <Sidebar onToggleCollapse={() => setCollapsed((c) => !c)} />
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
