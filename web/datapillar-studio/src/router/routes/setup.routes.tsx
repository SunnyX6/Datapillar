import { lazy } from 'react'
import type { RouteObject } from 'react-router-dom'
import { withSuspense } from './withSuspense'

const LazySetupPage = lazy(() => import('@/pages/setup').then((m) => ({ default: m.SetupPage })))

export const setupRoutes: RouteObject[] = [
  {
    path: '/setup',
    element: withSuspense(LazySetupPage),
  },
]
