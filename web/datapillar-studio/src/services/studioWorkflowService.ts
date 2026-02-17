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

export interface StudioWorkflowItem {
  id: number
  projectId: number
  projectName?: string | null
  workflowName: string
  triggerType: number
  status: number
  description?: string | null
  jobCount?: number | null
  createdAt: string
  updatedAt: string
}

export interface ListStudioWorkflowsParams extends StudioPageParams {
  workflowName?: string
  status?: number
}

export async function listWorkflows(
  projectId: number,
  params: ListStudioWorkflowsParams = {}
): Promise<StudioPageResult<StudioWorkflowItem>> {
  const response = await studioBizClient.get<ApiResponse<StudioWorkflowItem[]>>(
    `/projects/${projectId}/workflows`,
    {
      params: pickDefinedParams({
        workflowName: params.workflowName,
        status: params.status,
        limit: params.limit,
        offset: params.offset,
        maxLimit: params.maxLimit
      })
    }
  )
  return toPageResult(response.data)
}

export interface ListWorkflowRunsParams extends StudioPageParams {
  state?: string
}

export async function listWorkflowRuns(
  projectId: number,
  workflowId: number,
  params: ListWorkflowRunsParams = {}
): Promise<Record<string, unknown>> {
  const response = await studioBizClient.get<ApiResponse<Record<string, unknown>>>(
    `/projects/${projectId}/workflows/${workflowId}/runs`,
    {
      params: pickDefinedParams({
        state: params.state,
        limit: params.limit,
        offset: params.offset,
        maxLimit: params.maxLimit
      })
    }
  )
  return requireApiData(response.data)
}

export async function listDagVersions(
  projectId: number,
  workflowId: number,
  params: StudioPageParams = {}
): Promise<Record<string, unknown>> {
  const response = await studioBizClient.get<ApiResponse<Record<string, unknown>>>(
    `/projects/${projectId}/workflow/${workflowId}/dag/versions`,
    {
      params: pickDefinedParams({
        limit: params.limit,
        offset: params.offset,
        maxLimit: params.maxLimit
      })
    }
  )
  return requireApiData(response.data)
}
