/**
 * 路由配置
 */

import { Suspense, lazy, type LazyExoticComponent, type ComponentType } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { PrivateRoute } from './PrivateRoute'

const loadingFallback = (
  <div className="flex min-h-dvh items-center justify-center bg-slate-50 text-slate-500 dark:bg-[#020617]">
    <span className="text-xs tracking-[0.3em] uppercase">Loading...</span>
  </div>
)

const withSuspense = (Component: LazyExoticComponent<ComponentType>) => (
  <Suspense fallback={loadingFallback}>
    <Component />
  </Suspense>
)

const LazyLoginPage = lazy(async () => {
  const module = await import('@/pages/login')
  return { default: module.LoginPage }
})

const LazyMainLayout = lazy(async () => {
  const module = await import('@/layouts/MainLayout')
  return { default: module.MainLayout }
})

const LazyDashboardPage = lazy(async () => {
  const module = await import('@/pages/dashboard')
  return { default: module.DashboardPage }
})

const LazyWorkflowStudioPage = lazy(async () => {
  const module = await import('@/pages/workflow')
  return { default: module.WorkflowStudioPage }
})

const LazyProfilePage = lazy(async () => {
  const module = await import('@/pages/profile')
  return { default: module.ProfilePage }
})

const LazyGovernanceKnowledgePage = lazy(async () => {
  const module = await import('@/pages/governance')
  return { default: module.GovernanceKnowledgePage }
})

const LazyGovernanceMetadataPage = lazy(async () => {
  const module = await import('@/pages/governance')
  return { default: module.GovernanceMetadataPage }
})

const LazyGovernanceSemanticPage = lazy(async () => {
  const module = await import('@/pages/governance')
  return { default: module.GovernanceSemanticPage }
})

const LazyNotFoundPage = lazy(async () => {
  const module = await import('@/pages/not-found')
  return { default: module.NotFoundPage }
})

export const router = createBrowserRouter([
  {
    path: '/',
    element: withSuspense(LazyLoginPage)
  },
  {
    element: <PrivateRoute />,
    children: [
      {
        element: withSuspense(LazyMainLayout),
        children: [
          {
            path: '/home',
            element: withSuspense(LazyDashboardPage)
          },
          {
            path: '/workflow',
            element: withSuspense(LazyWorkflowStudioPage)
          },
          {
            path: '/profile',
            element: withSuspense(LazyProfilePage)
          },
          {
            path: '/governance/metadata',
            element: withSuspense(LazyGovernanceMetadataPage)
          },
          {
            path: '/governance/semantic',
            element: withSuspense(LazyGovernanceSemanticPage)
          },
          {
            path: '/governance/knowledge',
            element: withSuspense(LazyGovernanceKnowledgePage)
          }
        ]
      }
    ]
  },
  {
    path: '*',
    element: withSuspense(LazyNotFoundPage)
  }
])
