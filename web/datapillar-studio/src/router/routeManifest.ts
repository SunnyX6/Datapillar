import type { ComponentType } from 'react'

export type RouteKind = 'entry' | 'app' | 'setup' | 'public'

export type LazyRouteLoader = () => Promise<{ default: ComponentType }>

export interface RouteManifestItem {
  kind: RouteKind
  path: string
  lazy?: LazyRouteLoader
  requireSetup: boolean
  requireAuth: boolean
  requiredMenuPath?: string
  entryPriority?: number
}

export const routeManifest: RouteManifestItem[] = [
  {
    kind: 'entry',
    path: '/',
    requireSetup: true,
    requireAuth: true,
  },

  {
    kind: 'setup',
    path: '/setup',
    lazy: () => import('@/pages/setup').then((m) => ({ default: m.SetupPage })),
    requireSetup: false,
    requireAuth: false,
  },

  {
    kind: 'app',
    path: '/home',
    lazy: () => import('@/pages/dashboard').then((m) => ({ default: m.DashboardPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/home',
    entryPriority: 10,
  },
  {
    kind: 'app',
    path: '/governance/metadata',
    lazy: () => import('@/pages/governance/metadata').then((m) => ({ default: m.GovernanceMetadataPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/metadata',
    entryPriority: 20,
  },
  {
    kind: 'app',
    path: '/governance/metadata/catalogs/:catalogName',
    lazy: () => import('@/pages/governance/metadata').then((m) => ({ default: m.GovernanceMetadataPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/metadata',
  },
  {
    kind: 'app',
    path: '/governance/metadata/catalogs/:catalogName/schemas/:schemaName',
    lazy: () => import('@/pages/governance/metadata').then((m) => ({ default: m.GovernanceMetadataPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/metadata',
  },
  {
    kind: 'app',
    path: '/governance/metadata/catalogs/:catalogName/schemas/:schemaName/tables/:tableName',
    lazy: () => import('@/pages/governance/metadata').then((m) => ({ default: m.GovernanceMetadataPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/metadata',
  },
  {
    kind: 'app',
    path: '/governance/semantic',
    lazy: () => import('@/pages/governance/metasemantic/SemanticPage').then((m) => ({ default: m.GovernanceSemanticPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/semantic',
    entryPriority: 30,
  },
  {
    kind: 'app',
    path: '/governance/semantic/metrics',
    lazy: () => import('@/pages/governance/metasemantic/MetricPage').then((m) => ({ default: m.GovernanceMetricPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/semantic',
  },
  {
    kind: 'app',
    path: '/governance/semantic/wordroots',
    lazy: () => import('@/pages/governance/metasemantic/WordRootPage').then((m) => ({ default: m.GovernanceWordRootPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/semantic',
  },
  {
    kind: 'app',
    path: '/governance/semantic/standards/datatypes',
    lazy: () => import('@/pages/governance/metasemantic/DataTypePage').then((m) => ({ default: m.GovernanceDataTypePage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/semantic',
  },
  {
    kind: 'app',
    path: '/governance/semantic/standards/valuedomains',
    lazy: () => import('@/pages/governance/metasemantic/ValueDomainPage').then((m) => ({ default: m.GovernanceValueDomainPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/semantic',
  },
  {
    kind: 'app',
    path: '/governance/semantic/standards/security',
    lazy: () => import('@/pages/governance/metasemantic/ClassificationPage').then((m) => ({ default: m.GovernanceClassificationPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/semantic',
  },
  {
    kind: 'app',
    path: '/governance/knowledge',
    lazy: () => import('@/pages/governance/KnowledgeGraphPage').then((m) => ({ default: m.GovernanceKnowledgePage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/governance/knowledge',
    entryPriority: 40,
  },
  {
    kind: 'app',
    path: '/projects',
    lazy: () => import('@/pages/projects').then((m) => ({ default: m.ProjectPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/projects',
    entryPriority: 50,
  },
  {
    kind: 'app',
    path: '/collaboration',
    lazy: () => import('@/pages/collaboration').then((m) => ({ default: m.CollaborationPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/collaboration',
    entryPriority: 60,
  },
  {
    kind: 'app',
    path: '/workflow',
    lazy: () => import('@/pages/workflow').then((m) => ({ default: m.WorkflowStudioPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/workflow',
    entryPriority: 70,
  },
  {
    kind: 'app',
    path: '/wiki',
    lazy: () => import('@/pages/wiki').then((m) => ({ default: m.WikiPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/wiki',
    entryPriority: 80,
  },
  {
    kind: 'app',
    path: '/data-tracking',
    lazy: () => import('@/pages/data_tracking').then((m) => ({ default: m.DataTrackingPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/data-tracking',
    entryPriority: 90,
  },
  {
    kind: 'app',
    path: '/ide',
    lazy: () => import('@/pages/ide/OneIdePage').then((m) => ({ default: m.OneIdePage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/ide',
    entryPriority: 100,
  },
  {
    kind: 'app',
    path: '/ide/sql',
    lazy: () => import('@/pages/ide/SqlEditorPage').then((m) => ({ default: m.SqlEditorPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/ide',
  },
  {
    kind: 'app',
    path: '/profile',
    lazy: () => import('@/pages/profile').then((m) => ({ default: m.ProfilePage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/profile',
    entryPriority: 110,
  },
  {
    kind: 'app',
    path: '/profile/permission',
    lazy: () => import('@/pages/profile/permission').then((m) => ({ default: m.PermissionPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/profile/permission',
  },
  {
    kind: 'app',
    path: '/profile/llm/models',
    lazy: () => import('@/pages/llm').then((m) => ({ default: m.ModelManagementPage })),
    requireSetup: true,
    requireAuth: true,
    requiredMenuPath: '/profile/llm/models',
  },

  {
    kind: 'public',
    path: '/login',
    lazy: () => import('@/pages/login').then((m) => ({ default: m.LoginPage })),
    requireSetup: false,
    requireAuth: false,
  },
  {
    kind: 'public',
    path: '/invite',
    lazy: () => import('@/pages/invite').then((m) => ({ default: m.InvitePage })),
    requireSetup: false,
    requireAuth: false,
  },
  {
    kind: 'public',
    path: '/403',
    lazy: () => import('@/pages/exception').then((m) => ({ default: m.ForbiddenPage })),
    requireSetup: false,
    requireAuth: false,
  },
  {
    kind: 'public',
    path: '/500',
    lazy: () => import('@/pages/exception').then((m) => ({ default: m.ServerErrorPage })),
    requireSetup: false,
    requireAuth: false,
  },
  {
    kind: 'public',
    path: '*',
    lazy: () => import('@/pages/exception').then((m) => ({ default: m.NotFoundPage })),
    requireSetup: false,
    requireAuth: false,
  },
]

export const entryRouteManifest = routeManifest
  .filter((route) => route.entryPriority !== undefined)
  .sort((a, b) => (a.entryPriority ?? Number.MAX_SAFE_INTEGER) - (b.entryPriority ?? Number.MAX_SAFE_INTEGER))
