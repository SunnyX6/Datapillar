import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/state'
import type { Menu } from '@/services/types/auth'
import { buildForbiddenPath, buildLocationPath } from '@/utils/exceptionNavigation'
import { canAccessRouteByMenus, resolveFirstAccessibleRoute } from '../access/menuAccess'
import { rememberLastAllowedRoute, resolveLastAllowedRoute } from '../access/routeSource'

const EMPTY_MENUS: Menu[] = []
const ENTRY_PATH = '/'
const LOGIN_PATH = '/login'

/**
 * 权限边界：仅根据路由权限元数据判断是否允许访问。
 */
export function PermissionBoundary() {
  const location = useLocation()
  const user = useAuthStore((state) => state.user)
  const menus = user?.menus ?? EMPTY_MENUS
  const currentPath = buildLocationPath(location)

  if (!canAccessRouteByMenus(menus, location.pathname)) {
    const fallbackPath = resolveFirstAccessibleRoute(menus)
    if (fallbackPath && fallbackPath !== location.pathname) {
      return <Navigate to={fallbackPath} replace />
    }

    const returnTo = resolveLastAllowedRoute() ?? LOGIN_PATH
    return (
      <Navigate
        to={buildForbiddenPath({
          reason: 'permission-denied',
          from: returnTo,
          deniedPath: currentPath,
        })}
        replace
      />
    )
  }

  if (location.pathname !== ENTRY_PATH) {
    rememberLastAllowedRoute(currentPath)
  }

  return <Outlet />
}
