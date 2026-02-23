export const API_BASE = {
  login: '/api/login',
  auth: '/api/auth',
  studioSetup: '/api/studio/setup',
  studioBiz: '/api/studio/biz',
  studioAdmin: '/api/studio/admin',
  studioSql: '/api/studio/biz/sql',
  aiKnowledgeWiki: '/api/ai/biz/knowledge/wiki',
  aiKnowledge: '/api/ai/biz/knowledge',
  aiWorkflow: '/api/ai/biz/etl',
  aiMetric: '/api/ai/biz/governance/metric',
  aiLlmPlayground: '/api/ai/admin/llms',
  oneMeta: '/api/onemeta',
  studioActuator: '/api/studio/actuator'
} as const

export const API_PATH = {
  login: {
    root: '',
    logout: '/logout',
    sso: '/sso'
  },
  auth: {
    refresh: '/refresh',
    validate: '/validate'
  },
  setup: {
    root: '',
    status: '/status'
  },
  sql: {
    execute: '/execute'
  },
  project: {
    list: '/users/me/projects',
    create: '/users/me/projects',
    detail: (projectId: number) => `/users/me/projects/${projectId}`
  },
  workflow: {
    list: '/workflows',
    runs: (workflowId: number) => `/workflows/${workflowId}/runs`,
    dagVersions: (workflowId: number) => `/workflows/${workflowId}/dag/versions`,
    chat: '/chat',
    sse: '/sse',
    abort: '/abort'
  },
  tenantAdmin: {
    tenants: '/tenants',
    users: '/tenant/current/members',
    roles: '/tenant/current/roles',
    role: '/tenant/current/roles',
    roleDetail: (roleId: number) => `/tenant/current/roles/${roleId}`,
    rolePermissions: (roleId: number) =>
      `/tenant/current/roles/${roleId}/permissions`,
    roleMembers: (roleId: number) => `/tenant/current/roles/${roleId}/members`,
    invitations: '/tenant/current/invitations',
    featureAudits: '/tenant/current/features/audits',
    ssoIdentities: '/tenant/current/sso/identities'
  },
  llm: {
    models: '/llms/models',
    model: '/llms/model',
    modelDetail: (modelId: number) => `/llms/model/${modelId}`,
    modelConnect: (modelId: number) => `/llms/model/${modelId}/connect`,
    providers: '/llms/providers',
    provider: '/llms/provider',
    providerDetail: (providerCode: string) => `/llms/provider/${providerCode}`,
    userModels: (userId: number) => `/llms/users/${userId}/models`
  },
  knowledgeGraph: {
    initial: '/initial',
    search: '/search'
  },
  knowledgeWiki: {
    namespaces: '/namespaces',
    namespaceDocuments: (namespaceId: number) => `/namespaces/${namespaceId}/documents`,
    namespaceDocumentsUpload: (namespaceId: number) => `/namespaces/${namespaceId}/documents/upload`,
    document: (documentId: number) => `/documents/${documentId}`,
    documentChunk: (documentId: number) => `/documents/${documentId}/chunk`,
    documentChunks: (documentId: number) => `/documents/${documentId}/chunks`,
    chunk: (chunkId: string) => `/chunks/${chunkId}`,
    retrieve: '/retrieve'
  },
  metric: {
    fill: '/fill'
  },
  aiLlmPlayground: {
    chat: '/chat'
  },
  health: {
    service: '/health'
  }
} as const

export const API_ABSOLUTE_PATH = {
  setupStatus: `${API_BASE.studioSetup}${API_PATH.setup.status}`,
  authRefresh: `${API_BASE.auth}${API_PATH.auth.refresh}`,
  loginLogout: `${API_BASE.login}${API_PATH.login.logout}`
} as const
