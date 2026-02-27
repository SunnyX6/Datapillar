import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/state'
import type { Menu } from '@/services/types/auth'
import { buildForbiddenPath, buildLocationPath, normalizeReturnPath } from '@/utils/exceptionNavigation'
import { resolveFirstAccessibleRoute } from '../access/menuAccess'
import { resolveLastAllowedRoute } from '../access/routeSource'

const LOGIN_PATH = '/login'
const EMPTY_MENUS: Menu[] = []

/**
 * 根路由分流：只按登录态与菜单权限跳转目标页。
 */
export function EntryRedirect() {
  const location = useLocation()
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
    const searchParams = new URLSearchParams(location.search)
    const returnTo = normalizeReturnPath(searchParams.get('from')) ?? resolveLastAllowedRoute() ?? LOGIN_PATH

    return (
      <Navigate
        to={buildForbiddenPath({
          reason: 'no-accessible-entry',
          from: returnTo,
          deniedPath: buildLocationPath(location),
        })}
        replace
      />
    )
  }

  return <Navigate to={targetPath} replace />
}
