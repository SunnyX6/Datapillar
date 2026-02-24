import { useEffect, useMemo, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Sidebar } from './navigation/Sidebar'
import { TopNav } from './navigation/TopNav'
import { useThemeStore, useAuthStore, useLayoutStore } from '@/stores'
import { getMyProfile, type StudioUserProfile } from '@/services/studioUserProfileService'

export function MainLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const mode = useThemeStore((state) => state.mode)
  const setMode = useThemeStore((state) => state.setMode)
  const authUser = useAuthStore((state) => state.user)
  const menus = authUser?.menus ?? []
  const isDark = mode === 'dark'
  const isSidebarCollapsed = useLayoutStore((state) => state.isSidebarCollapsed)
  const toggleSidebar = useLayoutStore((state) => state.toggleSidebar)
  const [profile, setProfile] = useState<StudioUserProfile | null>(null)

  useEffect(() => {
    if (!authUser?.userId) {
      return
    }

    let isCancelled = false

    const loadProfile = async () => {
      try {
        const response = await getMyProfile()
        if (isCancelled) {
          return
        }
        setProfile(response)
      } catch {
        if (isCancelled) {
          return
        }
        setProfile(null)
      }
    }

    void loadProfile()

    return () => {
      isCancelled = true
    }
  }, [authUser?.userId])

  const topNavUser = useMemo(() => {
    const profileNickname = profile?.nickname?.trim()
    const profileUsername = profile?.username?.trim()
    const authUsername = authUser?.username?.trim()
    const name = profileNickname || profileUsername || authUsername || 'User'
    const email = profile?.email?.trim() || authUser?.email?.trim() || undefined

    return {
      name,
      email
    }
  }, [authUser?.email, authUser?.username, profile])

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
          user={topNavUser}
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
