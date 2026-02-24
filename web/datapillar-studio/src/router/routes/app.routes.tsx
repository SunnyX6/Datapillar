import { lazy } from 'react'
import type { RouteObject } from 'react-router-dom'
import { EntryRedirect } from '../entries/EntryRedirect'
import { AuthGuard } from '../guards/AuthGuard'
import { PermissionGuard } from '../guards/PermissionGuard'
import { SetupGuard } from '../guards/SetupGuard'
import { withSuspense } from './withSuspense'

const LazyMainLayout = lazy(() => import('@/layouts/MainLayout').then((m) => ({ default: m.MainLayout })))

const LazyDashboardPage = lazy(() => import('@/pages/dashboard').then((m) => ({ default: m.DashboardPage })))
const LazyProjectsPage = lazy(() => import('@/pages/projects').then((m) => ({ default: m.ProjectPage })))
const LazyCollaborationPage = lazy(() => import('@/pages/collaboration').then((m) => ({ default: m.CollaborationPage })))
const LazyWorkflowStudioPage = lazy(() => import('@/pages/workflow').then((m) => ({ default: m.WorkflowStudioPage })))
const LazyWikiPage = lazy(() => import('@/pages/wiki').then((m) => ({ default: m.WikiPage })))
const LazyOneIdePage = lazy(() => import('@/pages/ide/OneIdePage').then((m) => ({ default: m.OneIdePage })))
const LazySqlEditorPage = lazy(() => import('@/pages/ide/SqlEditorPage').then((m) => ({ default: m.SqlEditorPage })))
const LazyDataTrackingPage = lazy(() => import('@/pages/data_tracking').then((m) => ({ default: m.DataTrackingPage })))
const LazyProfilePage = lazy(() => import('@/pages/profile').then((m) => ({ default: m.ProfilePage })))
const LazyPermissionPage = lazy(() => import('@/pages/profile/permission').then((m) => ({ default: m.PermissionPage })))
const LazyModelManagementPage = lazy(() => import('@/pages/llm').then((m) => ({ default: m.ModelManagementPage })))
const LazyGovernanceKnowledgePage = lazy(() => import('@/pages/governance/KnowledgeGraphPage').then((m) => ({ default: m.GovernanceKnowledgePage })))
const LazyGovernanceMetadataPage = lazy(() => import('@/pages/governance/metadata').then((m) => ({ default: m.GovernanceMetadataPage })))
const LazyGovernanceSemanticPage = lazy(() => import('@/pages/governance/metasemantic/SemanticPage').then((m) => ({ default: m.GovernanceSemanticPage })))
const LazyGovernanceMetricPage = lazy(() => import('@/pages/governance/metasemantic/MetricPage').then((m) => ({ default: m.GovernanceMetricPage })))
const LazyGovernanceWordRootPage = lazy(() => import('@/pages/governance/metasemantic/WordRootPage').then((m) => ({ default: m.GovernanceWordRootPage })))
const LazyGovernanceDataTypePage = lazy(() => import('@/pages/governance/metasemantic/DataTypePage').then((m) => ({ default: m.GovernanceDataTypePage })))
const LazyGovernanceValueDomainPage = lazy(() => import('@/pages/governance/metasemantic/ValueDomainPage').then((m) => ({ default: m.GovernanceValueDomainPage })))
const LazyGovernanceClassificationPage = lazy(() => import('@/pages/governance/metasemantic/ClassificationPage').then((m) => ({ default: m.GovernanceClassificationPage })))

export const appRoutes: RouteObject[] = [
  {
    element: <SetupGuard />,
    children: [
      {
        path: '/',
        element: <EntryRedirect />,
      },
      {
        element: <AuthGuard />,
        children: [
          {
            element: <PermissionGuard />,
            children: [
              {
                element: withSuspense(LazyMainLayout),
                children: [
                  {
                    path: '/home',
                    element: withSuspense(LazyDashboardPage),
                  },
                  {
                    path: '/projects',
                    element: withSuspense(LazyProjectsPage),
                  },
                  {
                    path: '/collaboration',
                    element: withSuspense(LazyCollaborationPage),
                  },
                  {
                    path: '/workflow',
                    element: withSuspense(LazyWorkflowStudioPage),
                  },
                  {
                    path: '/wiki',
                    element: withSuspense(LazyWikiPage),
                  },
                  {
                    path: '/data-tracking',
                    element: withSuspense(LazyDataTrackingPage),
                  },
                  {
                    path: '/ide',
                    element: withSuspense(LazyOneIdePage),
                  },
                  {
                    path: '/ide/sql',
                    element: withSuspense(LazySqlEditorPage),
                  },
                  {
                    path: '/profile',
                    element: withSuspense(LazyProfilePage),
                  },
                  {
                    path: '/profile/permission',
                    element: withSuspense(LazyPermissionPage),
                  },
                  {
                    path: '/profile/llm/models',
                    element: withSuspense(LazyModelManagementPage),
                  },
                  {
                    path: '/governance/metadata',
                    element: withSuspense(LazyGovernanceMetadataPage),
                  },
                  {
                    path: '/governance/metadata/catalogs/:catalogName',
                    element: withSuspense(LazyGovernanceMetadataPage),
                  },
                  {
                    path: '/governance/metadata/catalogs/:catalogName/schemas/:schemaName',
                    element: withSuspense(LazyGovernanceMetadataPage),
                  },
                  {
                    path: '/governance/metadata/catalogs/:catalogName/schemas/:schemaName/tables/:tableName',
                    element: withSuspense(LazyGovernanceMetadataPage),
                  },
                  {
                    path: '/governance/semantic',
                    element: withSuspense(LazyGovernanceSemanticPage),
                  },
                  {
                    path: '/governance/semantic/metrics',
                    element: withSuspense(LazyGovernanceMetricPage),
                  },
                  {
                    path: '/governance/semantic/wordroots',
                    element: withSuspense(LazyGovernanceWordRootPage),
                  },
                  {
                    path: '/governance/semantic/standards/datatypes',
                    element: withSuspense(LazyGovernanceDataTypePage),
                  },
                  {
                    path: '/governance/semantic/standards/valuedomains',
                    element: withSuspense(LazyGovernanceValueDomainPage),
                  },
                  {
                    path: '/governance/semantic/standards/security',
                    element: withSuspense(LazyGovernanceClassificationPage),
                  },
                  {
                    path: '/governance/knowledge',
                    element: withSuspense(LazyGovernanceKnowledgePage),
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
]
