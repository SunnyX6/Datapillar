import { useEffect, useMemo } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { MainLayout } from '@/layouts/MainLayout'
import {
  useAuthStore,
  useLayoutStore,
  useSearchStore,
  useThemeStore,
  type SearchContext
} from '@/state'

function resolveSearchContextByPath(path: string): SearchContext {
  if (path.startsWith('/governance/metadata')) {
    return 'metadata'
  }
  if (path.includes('/governance/semantic/metrics')) {
    return 'semantic-metrics'
  }
  if (path.includes('/governance/semantic/glossary')) {
    return 'semantic-glossary'
  }
  if (path.startsWith('/governance/semantic')) {
    return 'semantic'
  }
  if (path.startsWith('/governance/knowledge')) {
    return 'knowledge'
  }
  if (path === '/' || path.startsWith('/dashboard')) {
    return 'dashboard'
  }
  return 'default'
}

export function MainLayoutContainer() {
  const navigate = useNavigate()
  const location = useLocation()
  const mode = useThemeStore((state) => state.mode)
  const setMode = useThemeStore((state) => state.setMode)
  const authUser = useAuthStore((state) => state.user)
  const isSidebarCollapsed = useLayoutStore((state) => state.isSidebarCollapsed)
  const toggleSidebar = useLayoutStore((state) => state.toggleSidebar)
  const setSearchContext = useSearchStore((state) => state.setContext)
  const menus = authUser?.menus ?? []
  const isDark = mode === 'dark'

  const topNavUser = useMemo(() => {
    const authUsername = authUser?.username?.trim()
    const name = authUsername || 'User'
    const email = authUser?.email?.trim() || undefined
    return { name, email }
  }, [authUser?.email, authUser?.username])

  useEffect(() => {
    setSearchContext(resolveSearchContextByPath(location.pathname))
  }, [location.pathname, setSearchContext])

  const handleToggleSidebar = () => {
    toggleSidebar()
  }

  const handleToggleTheme = () => {
    setMode(isDark ? 'light' : 'dark')
  }

  const handleLogout = () => {
    void (async () => {
      try {
        const result = await useAuthStore.getState().logout()
        if (result.outcome === 'SUCCESS' || result.outcome === 'ALREADY_EXPIRED') {
          return
        }
        toast.error(result.message ?? '退出登录失败，请稍后重试')
      } catch {
        toast.error('退出登录失败，请稍后重试')
      }
    })()
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
    <MainLayout
      menus={menus}
      currentPath={location.pathname}
      isDark={isDark}
      isSidebarCollapsed={isSidebarCollapsed}
      topNavUser={topNavUser}
      onNavigate={handleNavigate}
      onToggleSidebar={handleToggleSidebar}
      onToggleTheme={handleToggleTheme}
      onLogout={handleLogout}
      onProfile={handleProfileNavigation}
    />
  )
}
