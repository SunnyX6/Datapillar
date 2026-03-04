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
        throw new Error(`Ingress routing is not allowed to be configured lazy：${route.path}`)
      }
      if (!route.requireSetup || !route.requireAuth) {
        throw new Error(`Ingress routing must be enabled setup/auth guard：${route.path}`)
      }
      return
    }

    if (!route.lazy) {
      throw new Error(`Non-entry routes must be configured lazy：${route.path}`)
    }

    if (route.kind === 'app' && (!route.requireSetup || !route.requireAuth)) {
      throw new Error(`Application routing must be enabled setup/auth guard：${route.path}`)
    }

    if ((route.kind === 'setup' || route.kind === 'public') && (route.requireSetup || route.requireAuth)) {
      throw new Error(`Public routing is prohibited from being enabled setup/auth guard：${route.path}`)
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
        throw new Error(`Route is missing lazy Configuration：${route.path}`)
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
        throw new Error(`Route is missing lazy Configuration：${route.path}`)
      }
      const LazyComponent = lazy(routeLoader)
      return {
        path: route.path,
        element: withSuspense(LazyComponent),
      }
    })

  const entryRoute = routeManifest.find((route) => route.kind === 'entry' && route.requireSetup && route.requireAuth)
  if (!entryRoute) {
    throw new Error('Missing root entry route（kind=entry）Configuration')
  }

  const appRoutes: RouteObject[] = routeManifest
    .filter((route) => route.kind === 'app' && route.lazy && route.requireSetup && route.requireAuth)
    .map((route) => {
      const routeLoader = route.lazy
      if (!routeLoader) {
        throw new Error(`Route is missing lazy Configuration：${route.path}`)
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
