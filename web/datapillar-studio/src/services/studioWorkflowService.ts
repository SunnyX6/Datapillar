import { API_BASE, API_PATH, requestData, requestEnvelope } from '@/api'
import { pickDefinedParams, toPageResult } from './studioCommon'
import type {
  ListDagVersionsParams,
  ListStudioWorkflowsResult,
  ListStudioWorkflowsParams,
  ListWorkflowRunsParams,
  StudioWorkflowItem
} from '@/services/types/studio/workflow'

export type {
  ListDagVersionsParams,
  ListStudioWorkflowsResult,
  ListStudioWorkflowsParams,
  ListWorkflowRunsParams,
  StudioWorkflowItem
} from '@/services/types/studio/workflow'

export async function listWorkflows(
  projectId: number,
  params: ListStudioWorkflowsParams = {}
): Promise<ListStudioWorkflowsResult> {
  const response = await requestEnvelope<StudioWorkflowItem[]>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.workflow.list,
    params: pickDefinedParams({
      projectId,
      workflowName: params.workflowName,
      status: params.status,
      limit: params.limit,
      offset: params.offset,
      maxLimit: params.maxLimit
    })
  })
  return toPageResult(response)
}

export async function listWorkflowRuns(
  _projectId: number,
  workflowId: number,
  params: ListWorkflowRunsParams = {}
): Promise<Record<string, unknown>> {
  return requestData<Record<string, unknown>>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.workflow.runs(workflowId),
    params: pickDefinedParams({
      state: params.state,
      limit: params.limit,
      offset: params.offset,
      maxLimit: params.maxLimit
    })
  })
}

export async function listDagVersions(
  _projectId: number,
  workflowId: number,
  params: ListDagVersionsParams = {}
): Promise<Record<string, unknown>> {
  return requestData<Record<string, unknown>>({
    baseURL: API_BASE.studioBiz,
    url: API_PATH.workflow.dagVersions(workflowId),
    params: pickDefinedParams({
      limit: params.limit,
      offset: params.offset,
      maxLimit: params.maxLimit
    })
  })
}
