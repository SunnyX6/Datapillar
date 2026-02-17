import { createApiClient } from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'
import {
  pickDefinedParams,
  requireApiData,
  type StudioPageParams,
  type StudioPageResult,
  toPageResult
} from './studioCommon'

const studioBizClient = createApiClient({
  baseURL: '/api/studio/biz',
  timeout: 30000
})

export type StudioProjectStatus = 'active' | 'archived' | 'paused' | 'deleted'

export interface StudioProjectItem {
  id: number
  name: string
  description?: string | null
  ownerId: number
  ownerName?: string | null
  status: StudioProjectStatus
  tags?: string[] | null
  isFavorite?: boolean | null
  isVisible?: boolean | null
  memberCount?: number | null
  lastAccessedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface ListStudioProjectsParams extends StudioPageParams {
  keyword?: string
  status?: StudioProjectStatus
  onlyFavorites?: boolean
  onlyVisible?: boolean
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

export async function listProjects(
  params: ListStudioProjectsParams = {}
): Promise<StudioPageResult<StudioProjectItem>> {
  const response = await studioBizClient.get<ApiResponse<StudioProjectItem[]>>('/projects', {
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
  return toPageResult(response.data)
}

export interface CreateStudioProjectRequest {
  name: string
  description?: string
  tags?: string[]
  isVisible?: boolean
}

export async function createProject(request: CreateStudioProjectRequest): Promise<void> {
  await studioBizClient.post<ApiResponse<void>>('/project', request)
}

export async function getProjectDetail(projectId: number): Promise<StudioProjectItem> {
  const response = await studioBizClient.get<ApiResponse<StudioProjectItem>>(`/projects/${projectId}`)
  return requireApiData(response.data)
}
