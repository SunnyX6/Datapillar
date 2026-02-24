import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores'
import type { Menu } from '@/types/auth'
import { canAccessRouteByMenus, resolveFirstAccessibleRoute } from '../access/routeAccess'

const LOGIN_PATH = '/login'
const EMPTY_MENUS: Menu[] = []

/**
 * 权限守卫：仅根据菜单权限决定是否允许访问当前路由。
 */
export function PermissionGuard() {
  const location = useLocation()
  const user = useAuthStore((state) => state.user)
  const menus = user?.menus ?? EMPTY_MENUS

  if (!canAccessRouteByMenus(menus, location.pathname)) {
    const redirectPath = resolveFirstAccessibleRoute(menus)
    if (!redirectPath || redirectPath === location.pathname) {
      return <Navigate to={LOGIN_PATH} replace />
    }
    return <Navigate to={redirectPath} replace />
  }

  return <Outlet />
}
