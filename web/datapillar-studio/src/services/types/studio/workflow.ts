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

export interface ListStudioWorkflowsParams {
  workflowName?: string
  status?: number
  limit?: number
  offset?: number
  maxLimit?: number
}

export interface ListStudioWorkflowsResult {
  items: StudioWorkflowItem[]
  total: number
  limit: number
  offset: number
}

export interface ListWorkflowRunsParams {
  state?: string
  limit?: number
  offset?: number
  maxLimit?: number
}

export interface ListDagVersionsParams {
  limit?: number
  offset?: number
  maxLimit?: number
}
