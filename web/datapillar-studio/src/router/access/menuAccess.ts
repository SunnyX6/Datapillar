import { matchPath } from 'react-router-dom'
import type { Menu } from '@/services/types/auth'
import {
  hasMenuPathAccess,
  normalizePath,
} from '@/services/menuPermissionService'
import {
  entryRouteManifest,
  routeManifest,
} from '../routeManifest'

const guardedRouteManifest = routeManifest.filter(
  (route) => route.requireAuth && Boolean(route.requiredMenuPath) && route.path !== '*',
)

function resolveRequiredMenuPath(pathname: string): string | undefined {
  const normalizedPath = normalizePath(pathname)
  const matchedRoute = guardedRouteManifest.find((route) =>
    Boolean(matchPath({ path: route.path, end: true }, normalizedPath)),
  )
  return matchedRoute?.requiredMenuPath
}

export function canAccessRouteByMenus(menus: Menu[], pathname: string): boolean {
  const requiredMenuPath = resolveRequiredMenuPath(pathname)
  if (!requiredMenuPath) {
    return true
  }
  return hasMenuPathAccess(menus, requiredMenuPath)
}

export function resolveFirstAccessibleRoute(menus: Menu[]): string | null {
  for (const route of entryRouteManifest) {
    if (!route.requiredMenuPath || hasMenuPathAccess(menus, route.requiredMenuPath)) {
      return route.path
    }
  }
  return null
}
