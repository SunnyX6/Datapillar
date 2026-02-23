import { API_BASE, API_PATH, requestData, requestEnvelope } from '@/lib/api'
import { pickDefinedParams } from './studioCommon'
import type {
  ConnectAdminLlmModelRequest,
  ConnectAdminLlmModelResponse,
  CreateAdminLlmModelRequest,
  CreateAdminLlmProviderRequest,
  ListAdminModelsParams,
  ListAdminUserModelsParams,
  StudioLlmModel,
  StudioLlmModelUsage,
  StudioLlmProvider,
  UpdateAdminLlmProviderRequest
} from '@/types/studio/llm'

export type {
  ConnectAdminLlmModelRequest,
  ConnectAdminLlmModelResponse,
  CreateAdminLlmModelRequest,
  CreateAdminLlmProviderRequest,
  ListAdminModelsParams,
  ListAdminUserModelsParams,
  StudioLlmModel,
  StudioLlmModelUsage,
  StudioLlmProvider,
  UpdateAdminLlmProviderRequest
} from '@/types/studio/llm'

export async function listAdminModels(
  params: ListAdminModelsParams = {}
): Promise<StudioLlmModel[]> {
  return requestData<StudioLlmModel[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.models,
    params: pickDefinedParams({
      keyword: params.keyword,
      provider: params.provider,
      model_type: params.modelType
    })
  })
}

export async function createAdminModel(request: CreateAdminLlmModelRequest): Promise<StudioLlmModel> {
  return requestData<StudioLlmModel, CreateAdminLlmModelRequest>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.model,
    method: 'POST',
    data: request
  })
}

export async function connectAdminModel(
  modelPk: number,
  request: ConnectAdminLlmModelRequest
): Promise<ConnectAdminLlmModelResponse> {
  return requestData<ConnectAdminLlmModelResponse, ConnectAdminLlmModelRequest>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.modelConnect(modelPk),
    method: 'POST',
    data: request
  })
}

export async function listAdminProviders(): Promise<StudioLlmProvider[]> {
  return requestData<StudioLlmProvider[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.providers
  })
}

export async function createAdminProvider(request: CreateAdminLlmProviderRequest): Promise<void> {
  await requestEnvelope<void, CreateAdminLlmProviderRequest>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.provider,
    method: 'POST',
    data: request
  })
}

export async function updateAdminProvider(
  providerCode: string,
  request: UpdateAdminLlmProviderRequest
): Promise<void> {
  await requestEnvelope<void, UpdateAdminLlmProviderRequest>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.providerDetail(encodeURIComponent(providerCode)),
    method: 'PATCH',
    data: request
  })
}

export async function deleteAdminProvider(providerCode: string): Promise<void> {
  await requestEnvelope<void>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.providerDetail(encodeURIComponent(providerCode)),
    method: 'DELETE'
  })
}

export async function listAdminUserModels(
  userId: number,
  params: ListAdminUserModelsParams = {}
): Promise<StudioLlmModelUsage[]> {
  return requestData<StudioLlmModelUsage[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.userModels(userId),
    params: pickDefinedParams({
      onlyEnabled: params.onlyEnabled
    })
  })
}

export async function listCurrentUserModels(): Promise<StudioLlmModelUsage[]> {
  return requestData<StudioLlmModelUsage[]>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.llm.models
  })
}
