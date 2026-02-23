export interface StudioLlmModel {
  id: number
  modelId: string
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
  modelId: string
  modelName: string
  modelType: string
  modelStatus: string
  providerId: number
  providerCode: string
  providerName: string
  status: number
  isDefault: boolean
  totalCostUsd?: string | null
  grantedBy?: number | null
  grantedAt?: string | null
  lastUsedAt?: string | null
  updatedAt?: string | null
}

export interface StudioLlmProvider {
  id: number
  code: string
  name: string
  baseUrl?: string | null
  modelIds?: string[] | null
}

export interface CreateAdminLlmModelRequest {
  modelId: string
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
