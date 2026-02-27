export interface StudioLlmModel {
  aiModelId: number
  providerModelId: string
  name: string
  providerId: number
  providerCode: string
  providerName: string
  modelType: string
  description?: string | null
  tags?: string[] | null
  contextTokens?: number | null
  inputPriceUsd?: string | null
  outputPriceUsd?: string | null
  embeddingDimension?: number | null
  baseUrl?: string | null
  maskedApiKey?: string | null
  status: string
  hasApiKey: boolean
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface StudioLlmModelUsage {
  id: number
  userId: number
  aiModelId: number
  providerModelId: string
  modelName: string
  modelType: string
  modelStatus: string
  providerId: number
  providerCode: string
  providerName: string
  permissionId?: number | null
  permissionCode?: string | null
  permissionLevel?: number | null
  status?: number
  isDefault: boolean
  callCount?: string | null
  promptTokens?: string | null
  completionTokens?: string | null
  totalTokens?: string | null
  totalCostUsd?: string | null
  grantedBy?: number | null
  grantedAt?: string | null
  updatedBy?: number | null
  expiresAt?: string | null
  lastUsedAt?: string | null
  updatedAt?: string | null
}

export interface StudioAdminUserModelPermission {
  aiModelId: number
  providerModelId: string
  modelName: string
  modelType?: string | null
  modelStatus?: string | null
  providerId?: number | null
  providerCode?: string | null
  providerName?: string | null
  permissionCode?: string | null
  isDefault?: boolean
}

export type StudioCurrentUserModelPermission = StudioAdminUserModelPermission

export interface StudioLlmProvider {
  id: number
  code: string
  name: string
  baseUrl?: string | null
  modelIds?: string[] | null
}

export interface CreateAdminLlmModelRequest {
  providerModelId: string
  name: string
  providerCode: string
  modelType: 'chat' | 'embeddings' | 'reranking' | 'code'
  description?: string
  tags?: string[]
  contextTokens?: number
  inputPriceUsd?: string
  outputPriceUsd?: string
  embeddingDimension?: number
  baseUrl?: string
  apiKey?: string
}

export interface ConnectAdminLlmModelRequest {
  apiKey: string
  baseUrl?: string
}

export interface ConnectAdminLlmModelResponse {
  connected: boolean
  hasApiKey: boolean
}

export interface CreateAdminLlmProviderRequest {
  code: string
  name?: string
  baseUrl?: string
}

export interface UpdateAdminLlmProviderRequest {
  name?: string
  baseUrl?: string
  addModelIds?: string[]
  removeModelIds?: string[]
}

export interface ListAdminModelsParams {
  keyword?: string
  provider?: string
  modelType?: string
}

export interface ListAdminUserModelsParams {
  onlyEnabled?: boolean
}

export interface UpsertAdminUserModelGrantRequest {
  permissionCode: 'DISABLE' | 'READ' | 'ADMIN'
  isDefault?: boolean
  expiresAt?: string | null
}
