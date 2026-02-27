import { API_BASE, API_PATH, requestData, requestEnvelope } from '@/api'
import { pickDefinedParams, toPageResult } from './studioCommon'
import type {
  CreateStudioProjectRequest,
  ListStudioProjectsParams,
  ListStudioProjectsResult,
  StudioProjectItem
} from '@/services/types/studio/project'

export type {
  CreateStudioProjectRequest,
  ListStudioProjectsParams,
  ListStudioProjectsResult,
  StudioProjectItem,
  StudioProjectStatus
} from '@/services/types/studio/project'

export async function listProjects(
  params: ListStudioProjectsParams = {}
): Promise<ListStudioProjectsResult> {
  const response = await requestEnvelope<StudioProjectItem[]>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.project.list,
    params: pickDefinedParams({
      keyword: params.keyword,
      status: params.status,
      onlyFavorites: params.onlyFavorites,
      onlyVisible: params.onlyVisible,
      limit: params.limit,
      offset: params.offset,
      maxLimit: params.maxLimit,
      sortBy: params.sortBy,
      sortOrder: params.sortOrder
    })
  })
  return toPageResult(response)
}

export async function createProject(request: CreateStudioProjectRequest): Promise<void> {
  await requestEnvelope<void, CreateStudioProjectRequest>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.project.create,
    method: 'POST',
    data: request
  })
}

export async function getProjectDetail(projectId: number): Promise<StudioProjectItem> {
  return requestData<StudioProjectItem>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.project.detail(projectId)
  })
}
