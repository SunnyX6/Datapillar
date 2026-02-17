import { createApiClient } from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'
import { pickDefinedParams, requireApiData } from './studioCommon'

const studioAdminClient = createApiClient({
  baseURL: '/api/studio/admin',
  timeout: 30000
})

const studioBizClient = createApiClient({
  baseURL: '/api/studio/biz',
  timeout: 30000
})

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

export interface ListAdminModelsParams {
  keyword?: string
  provider?: string
  modelType?: string
}

export async function listAdminModels(
  params: ListAdminModelsParams = {}
): Promise<StudioLlmModel[]> {
  const response = await studioAdminClient.get<ApiResponse<StudioLlmModel[]>>('/llms/models', {
    params: pickDefinedParams({
      keyword: params.keyword,
      provider: params.provider,
      model_type: params.modelType
    })
  })
  return requireApiData(response.data)
}

export interface ListAdminUserModelsParams {
  onlyEnabled?: boolean
}

export async function listAdminUserModels(
  userId: number,
  params: ListAdminUserModelsParams = {}
): Promise<StudioLlmModelUsage[]> {
  const response = await studioAdminClient.get<ApiResponse<StudioLlmModelUsage[]>>(
    `/llms/users/${userId}/models`,
    {
      params: pickDefinedParams({
        onlyEnabled: params.onlyEnabled
      })
    }
  )
  return requireApiData(response.data)
}

export async function listCurrentUserModels(): Promise<StudioLlmModelUsage[]> {
  const response = await studioBizClient.get<ApiResponse<StudioLlmModelUsage[]>>('/llms/models')
  return requireApiData(response.data)
}
