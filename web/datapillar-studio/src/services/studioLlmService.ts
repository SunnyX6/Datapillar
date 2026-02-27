import { API_BASE, API_PATH, requestData, requestEnvelope } from '@/api'
import { pickDefinedParams } from './studioCommon'
import type {
  ConnectAdminLlmModelRequest,
  ConnectAdminLlmModelResponse,
  CreateAdminLlmModelRequest,
  CreateAdminLlmProviderRequest,
  ListAdminModelsParams,
  ListAdminUserModelsParams,
  StudioCurrentUserModelPermission,
  StudioAdminUserModelPermission,
  StudioLlmModel,
  StudioLlmProvider,
  UpsertAdminUserModelGrantRequest,
  UpdateAdminLlmProviderRequest
} from '@/services/types/studio/llm'

export type {
  ConnectAdminLlmModelRequest,
  ConnectAdminLlmModelResponse,
  CreateAdminLlmModelRequest,
  CreateAdminLlmProviderRequest,
  ListAdminModelsParams,
  ListAdminUserModelsParams,
  StudioCurrentUserModelPermission,
  StudioAdminUserModelPermission,
  StudioLlmModel,
  StudioLlmProvider,
  UpsertAdminUserModelGrantRequest,
  UpdateAdminLlmProviderRequest
} from '@/services/types/studio/llm'

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
    url: API_PATH.llm.models,
    method: 'POST',
    data: request
  })
}

export async function connectAdminModel(
  aiModelId: number,
  request: ConnectAdminLlmModelRequest
): Promise<ConnectAdminLlmModelResponse> {
  return requestData<ConnectAdminLlmModelResponse, ConnectAdminLlmModelRequest>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.modelConnect(aiModelId),
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
): Promise<StudioAdminUserModelPermission[]> {
  return requestData<StudioAdminUserModelPermission[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.userModels(userId),
    params: pickDefinedParams({
      onlyEnabled: params.onlyEnabled
    })
  })
}

export async function upsertAdminUserModelGrant(
  userId: number,
  aiModelId: number,
  request: UpsertAdminUserModelGrantRequest
): Promise<void> {
  await requestEnvelope<void, UpsertAdminUserModelGrantRequest>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.userModel(userId, aiModelId),
    method: 'PUT',
    data: request
  })
}

export async function deleteAdminUserModelGrant(
  userId: number,
  aiModelId: number
): Promise<void> {
  await requestEnvelope<void>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.llm.userModel(userId, aiModelId),
    method: 'DELETE'
  })
}

export async function listCurrentUserModels(): Promise<StudioCurrentUserModelPermission[]> {
  return requestData<StudioCurrentUserModelPermission[]>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.llm.models
  })
}

export async function setCurrentUserDefaultModel(aiModelId: number): Promise<void> {
  await requestEnvelope<void>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.llm.currentUserDefaultModel(aiModelId),
    method: 'PUT'
  })
}
