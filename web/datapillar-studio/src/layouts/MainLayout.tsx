import { Outlet } from 'react-router-dom'
import { Sidebar } from './navigation/Sidebar'
import { TopNav } from './navigation/TopNav'
import type { Menu } from '@/services/types/auth'

interface MainLayoutProps {
  menus: Menu[]
  currentPath: string
  isDark: boolean
  isSidebarCollapsed: boolean
  topNavUser: {
    name: string
    email?: string
  }
  onNavigate: (path: string) => void
  onToggleSidebar: () => void
  onToggleTheme: () => void
  onLogout: () => void
  onProfile: () => void
}

export function MainLayout({
  menus,
  currentPath,
  isDark,
  isSidebarCollapsed,
  topNavUser,
  onNavigate,
  onToggleSidebar,
  onToggleTheme,
  onLogout,
  onProfile
}: MainLayoutProps) {

  return (
    <div className="flex h-screen w-full bg-slate-50 dark:bg-[#020617] text-slate-900 dark:text-white">
      <Sidebar
        menus={menus}
        currentPath={currentPath}
        onNavigate={onNavigate}
        collapsed={isSidebarCollapsed}
        onToggleCollapse={onToggleSidebar}
      />
      <div className="flex-1 flex flex-col overflow-y-hidden overflow-x-visible relative z-0 transition-[margin,width] duration-300">
        <TopNav
          isDark={isDark}
          toggleTheme={onToggleTheme}
          user={topNavUser}
          menus={menus}
          currentPath={currentPath}
          onNavigate={onNavigate}
          onLogout={onLogout}
          isSidebarCollapsed={isSidebarCollapsed}
          onToggleSidebar={onToggleSidebar}
          onProfile={onProfile}
        />
        <main className="flex-1 overflow-y-hidden overflow-x-visible bg-slate-50 dark:bg-[#020617] relative z-0 transition-[width] duration-300">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
