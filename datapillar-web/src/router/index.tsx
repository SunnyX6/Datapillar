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

const LazyLoginPage = lazy(() => import('@/pages/login').then(m => ({ default: m.LoginPage })))

const LazyMainLayout = lazy(() => import('@/layouts/MainLayout').then(m => ({ default: m.MainLayout })))

const LazyDashboardPage = lazy(() => import('@/pages/dashboard').then(m => ({ default: m.DashboardPage })))

const LazyProjectsPage = lazy(() => import('@/pages/projects').then(m => ({ default: m.ProjectPage })))

const LazyCollaborationPage = lazy(() => import('@/pages/collaboration').then(m => ({ default: m.CollaborationPage })))

const LazyWorkflowStudioPage = lazy(() => import('@/pages/workflow').then(m => ({ default: m.WorkflowStudioPage })))

const LazyWikiPage = lazy(() => import('@/pages/wiki').then(m => ({ default: m.WikiPage })))

const LazyOneIdePage = lazy(() => import('@/pages/ide').then(m => ({ default: m.OneIdePage })))

const LazySqlEditorPage = lazy(() => import('@/pages/ide').then(m => ({ default: m.SqlEditorPage })))

const LazyProfilePage = lazy(() => import('@/pages/profile').then(m => ({ default: m.ProfilePage })))

// Governance 页面 - 分开导入避免 HMR 问题
const LazyGovernanceKnowledgePage = lazy(() => import('@/pages/governance/KnowledgeGraphPage').then(m => ({ default: m.GovernanceKnowledgePage })))

const LazyGovernanceMetadataPage = lazy(() => import('@/pages/governance/metadata').then(m => ({ default: m.GovernanceMetadataPage })))

const LazyGovernanceSemanticPage = lazy(() => import('@/pages/governance/metasemantic/SemanticPage').then(m => ({ default: m.GovernanceSemanticPage })))

const LazyGovernanceMetricPage = lazy(() => import('@/pages/governance/metasemantic/MetricPage').then(m => ({ default: m.GovernanceMetricPage })))

const LazyGovernanceWordRootPage = lazy(() => import('@/pages/governance/metasemantic/WordRootPage').then(m => ({ default: m.GovernanceWordRootPage })))

const LazyGovernanceDataTypePage = lazy(() => import('@/pages/governance/metasemantic/DataTypePage').then(m => ({ default: m.GovernanceDataTypePage })))

const LazyGovernanceValueDomainPage = lazy(() => import('@/pages/governance/metasemantic/ValueDomainPage').then(m => ({ default: m.GovernanceValueDomainPage })))

const LazyGovernanceClassificationPage = lazy(() => import('@/pages/governance/metasemantic/ClassificationPage').then(m => ({ default: m.GovernanceClassificationPage })))

const LazyNotFoundPage = lazy(() => import('@/pages/not-found').then(m => ({ default: m.NotFoundPage })))

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
            path: '/projects',
            element: withSuspense(LazyProjectsPage)
          },
          {
            path: '/collaboration',
            element: withSuspense(LazyCollaborationPage)
          },
          {
            path: '/workflow',
            element: withSuspense(LazyWorkflowStudioPage)
          },
          {
            path: '/wiki',
            element: withSuspense(LazyWikiPage)
          },
          {
            path: '/ide',
            element: withSuspense(LazyOneIdePage)
          },
          {
            path: '/ide/sql',
            element: withSuspense(LazySqlEditorPage)
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
            path: '/governance/metadata/catalogs/:catalogName',
            element: withSuspense(LazyGovernanceMetadataPage)
          },
          {
            path: '/governance/metadata/catalogs/:catalogName/schemas/:schemaName',
            element: withSuspense(LazyGovernanceMetadataPage)
          },
          {
            path: '/governance/metadata/catalogs/:catalogName/schemas/:schemaName/tables/:tableName',
            element: withSuspense(LazyGovernanceMetadataPage)
          },
          {
            path: '/governance/semantic',
            element: withSuspense(LazyGovernanceSemanticPage)
          },
          {
            path: '/governance/semantic/metrics',
            element: withSuspense(LazyGovernanceMetricPage)
          },
          {
            path: '/governance/semantic/wordroots',
            element: withSuspense(LazyGovernanceWordRootPage)
          },
          {
            path: '/governance/semantic/standards/datatypes',
            element: withSuspense(LazyGovernanceDataTypePage)
          },
          {
            path: '/governance/semantic/standards/valuedomains',
            element: withSuspense(LazyGovernanceValueDomainPage)
          },
          {
            path: '/governance/semantic/standards/security',
            element: withSuspense(LazyGovernanceClassificationPage)
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
