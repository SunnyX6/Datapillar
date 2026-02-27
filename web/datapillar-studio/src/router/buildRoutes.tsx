import { lazy } from 'react'
import type { RouteObject } from 'react-router-dom'
import { EntryRedirect } from './entries/EntryRedirect'
import { routeManifest, type RouteManifestItem } from './routeManifest'
import { withSuspense } from './withSuspense'
import { AuthGate } from './guards/AuthGate'
import { BootstrapGate } from './guards/BootstrapGate'
import { PermissionBoundary } from './guards/PermissionBoundary'
import { SetupGate } from './guards/SetupGate'

const LazyMainLayout = lazy(() =>
  import('@/router/containers/MainLayoutContainer').then((m) => ({ default: m.MainLayoutContainer }))
)

interface RouteHandle {
  requiredMenuPath?: string
  entryPriority?: number
}

function assertRoutePolicy(routes: RouteManifestItem[]) {
  routes.forEach((route) => {
    if (route.kind === 'entry') {
      if (route.lazy) {
        throw new Error(`入口路由不允许配置 lazy：${route.path}`)
      }
      if (!route.requireSetup || !route.requireAuth) {
        throw new Error(`入口路由必须启用 setup/auth 守卫：${route.path}`)
      }
      return
    }

    if (!route.lazy) {
      throw new Error(`非入口路由必须配置 lazy：${route.path}`)
    }

    if (route.kind === 'app' && (!route.requireSetup || !route.requireAuth)) {
      throw new Error(`应用路由必须启用 setup/auth 守卫：${route.path}`)
    }

    if ((route.kind === 'setup' || route.kind === 'public') && (route.requireSetup || route.requireAuth)) {
      throw new Error(`公开路由禁止启用 setup/auth 守卫：${route.path}`)
    }
  })
}

export function buildRoutes(): RouteObject[] {
  assertRoutePolicy(routeManifest)

  const setupRoutes: RouteObject[] = routeManifest
    .filter((route) => route.kind === 'setup' && route.lazy && !route.requireSetup && !route.requireAuth)
    .map((route) => {
      const routeLoader = route.lazy
      if (!routeLoader) {
        throw new Error(`路由缺少 lazy 配置：${route.path}`)
      }
      const LazyComponent = lazy(routeLoader)
      return {
        path: route.path,
        element: withSuspense(LazyComponent),
      }
    })

  const publicRoutes: RouteObject[] = routeManifest
    .filter((route) => route.kind === 'public' && route.lazy && !route.requireSetup && !route.requireAuth)
    .map((route) => {
      const routeLoader = route.lazy
      if (!routeLoader) {
        throw new Error(`路由缺少 lazy 配置：${route.path}`)
      }
      const LazyComponent = lazy(routeLoader)
      return {
        path: route.path,
        element: withSuspense(LazyComponent),
      }
    })

  const entryRoute = routeManifest.find((route) => route.kind === 'entry' && route.requireSetup && route.requireAuth)
  if (!entryRoute) {
    throw new Error('缺少根入口路由（kind=entry）配置')
  }

  const appRoutes: RouteObject[] = routeManifest
    .filter((route) => route.kind === 'app' && route.lazy && route.requireSetup && route.requireAuth)
    .map((route) => {
      const routeLoader = route.lazy
      if (!routeLoader) {
        throw new Error(`路由缺少 lazy 配置：${route.path}`)
      }
      const LazyComponent = lazy(routeLoader)
      const handle: RouteHandle = {
        requiredMenuPath: route.requiredMenuPath,
        entryPriority: route.entryPriority,
      }
      return {
        path: route.path,
        element: withSuspense(LazyComponent),
        handle,
      }
    })

  const protectedRouteTree: RouteObject = {
    element: <BootstrapGate />,
    children: [
      {
        element: <SetupGate />,
        children: [
          {
            element: <AuthGate />,
            children: [
              {
                element: <PermissionBoundary />,
                children: [
                  {
                    path: entryRoute.path,
                    element: <EntryRedirect />,
                  },
                  {
                    element: withSuspense(LazyMainLayout),
                    children: appRoutes,
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  }

  return [...setupRoutes, protectedRouteTree, ...publicRoutes]
}
