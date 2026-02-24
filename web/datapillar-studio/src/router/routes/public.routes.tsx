import { lazy } from 'react'
import type { RouteObject } from 'react-router-dom'
import { withSuspense } from './withSuspense'

const LazyLoginPage = lazy(() => import('@/pages/login').then((m) => ({ default: m.LoginPage })))
const LazyInvitePage = lazy(() => import('@/pages/invite').then((m) => ({ default: m.InvitePage })))
const LazyServerErrorPage = lazy(() => import('@/pages/exception').then((m) => ({ default: m.ServerErrorPage })))
const LazyNotFoundPage = lazy(() => import('@/pages/exception').then((m) => ({ default: m.NotFoundPage })))

export const publicRoutes: RouteObject[] = [
  {
    path: '/login',
    element: withSuspense(LazyLoginPage),
  },
  {
    path: '/invite',
    element: withSuspense(LazyInvitePage),
  },
  {
    path: '/500',
    element: withSuspense(LazyServerErrorPage),
  },
  {
    path: '*',
    element: withSuspense(LazyNotFoundPage),
  },
]
