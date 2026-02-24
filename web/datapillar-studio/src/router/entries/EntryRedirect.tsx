import { Navigate } from 'react-router-dom'
import { useAuthStore } from '@/stores'
import type { Menu } from '@/types/auth'
import { resolveFirstAccessibleRoute } from '../access/routeAccess'

const LOGIN_PATH = '/login'
const EMPTY_MENUS: Menu[] = []

/**
 * 根路由分流：只按登录态与菜单权限跳转目标页。
 */
export function EntryRedirect() {
  const loading = useAuthStore((state) => state.loading)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const user = useAuthStore((state) => state.user)
  const menus = user?.menus ?? EMPTY_MENUS

  if (loading) {
    return null
  }

  if (!isAuthenticated) {
    return <Navigate to={LOGIN_PATH} replace />
  }

  const targetPath = resolveFirstAccessibleRoute(menus)
  if (!targetPath) {
    return <Navigate to={LOGIN_PATH} replace />
  }

  return <Navigate to={targetPath} replace />
}
