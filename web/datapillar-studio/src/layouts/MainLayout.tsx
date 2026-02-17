import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Sidebar } from './navigation/Sidebar'
import { TopNav } from './navigation/TopNav'
import { useThemeStore, useAuthStore, useLayoutStore } from '@/stores'

const MOCK_USER = {
  name: 'S. Engineer',
  role: 'Data Engineer',
  email: 'engineer@datapillar.ai'
}

export function MainLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const mode = useThemeStore((state) => state.mode)
  const setMode = useThemeStore((state) => state.setMode)
  const menus = useAuthStore((state) => state.user?.menus ?? [])
  const isDark = mode === 'dark'
  const isSidebarCollapsed = useLayoutStore((state) => state.isSidebarCollapsed)
  const toggleSidebar = useLayoutStore((state) => state.toggleSidebar)

  const handleToggleSidebar = () => {
    toggleSidebar()
  }

  const handleToggleTheme = () => {
    setMode(isDark ? 'light' : 'dark')
  }

  const handleLogout = async () => {
    await useAuthStore.getState().logout()
    navigate('/login')
  }

  const handleNavigate = (targetPath: string) => {
    if (!targetPath || location.pathname === targetPath) {
      return
    }
    navigate(targetPath)
  }

  const handleProfileNavigation = () => {
    if (location.pathname === '/profile') {
      return
    }
    navigate('/profile')
  }

  return (
    <div className="flex h-screen w-full bg-slate-50 dark:bg-[#020617] text-slate-900 dark:text-white">
      <Sidebar
        menus={menus}
        currentPath={location.pathname}
        onNavigate={handleNavigate}
        collapsed={isSidebarCollapsed}
        onToggleCollapse={handleToggleSidebar}
      />
      <div className="flex-1 flex flex-col overflow-y-hidden overflow-x-visible relative z-0 transition-[margin,width] duration-300">
        <TopNav
          isDark={isDark}
          toggleTheme={handleToggleTheme}
          user={MOCK_USER}
          menus={menus}
          currentPath={location.pathname}
          onNavigate={handleNavigate}
          onLogout={handleLogout}
          isSidebarCollapsed={isSidebarCollapsed}
          onToggleSidebar={handleToggleSidebar}
          onProfile={handleProfileNavigation}
        />
        <main className="flex-1 overflow-y-hidden overflow-x-visible bg-slate-50 dark:bg-[#020617] relative z-0 transition-[width] duration-300">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
