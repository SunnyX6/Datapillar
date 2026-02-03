/**
 * 工作流 Studio 服务
 *
 * 定义工作流数据结构，支持 AI 生成和手动创建
 */

import type { JobResponse, DependencyResponse, WorkflowResponse } from './aiWorkflowService'

/**
 * 工作流节点定义
 */
export interface WorkflowNodeDefinition {
  id: number | string
  label: string
  componentCode: string
  componentType?: string
  description: string
  positionX: number
  positionY: number
  jobParams?: Record<string, unknown>
}

/**
 * 工作流边定义
 */
export interface WorkflowEdgeDefinition {
  id: string
  source: string
  target: string
}

/**
 * 工作流统计
 */
export interface WorkflowStats {
  nodes: number
  edges: number
  runtimeMinutes: number
  qualityScore: number
}

/**
 * 工作流图
 */
export interface WorkflowGraph {
  name: string
  summary: string
  lastUpdated: number
  nodes: WorkflowNodeDefinition[]
  edges: WorkflowEdgeDefinition[]
  stats: WorkflowStats
  rawResponse?: WorkflowResponse
}

/**
 * 空工作流图
 */
export const emptyWorkflowGraph: WorkflowGraph = {
  name: 'Workflow Studio',
  summary: '描述你的数据目标，AI 会在数秒内生成编排蓝图。',
  lastUpdated: Date.now(),
  nodes: [],
  edges: [],
  stats: {
    nodes: 0,
    edges: 0,
    runtimeMinutes: 0,
    qualityScore: 0
  }
}

/**
 * 将 AI 返回的 WorkflowResponse 转换为 WorkflowGraph
 */
export function convertAIResponseToGraph(response: WorkflowResponse): WorkflowGraph {
  const nodes: WorkflowNodeDefinition[] = response.jobs.map((job: JobResponse) => ({
    id: job.id ?? 0,
    label: job.jobName,
    componentCode: job.jobTypeCode,
    description: job.description || '',
    positionX: job.positionX,
    positionY: job.positionY,
    jobParams: job.jobParams
  }))

  const edges: WorkflowEdgeDefinition[] = response.dependencies.map((dep: DependencyResponse) => ({
    id: `edge-${dep.parentJobId}-${dep.jobId}`,
    source: String(dep.parentJobId),
    target: String(dep.jobId)
  }))

  const runtimeMinutes = Math.max(5, Math.round(nodes.length * 4.2))
  const qualityScore = Math.min(99, Math.round(78 + nodes.length * 1.2))

  return {
    name: response.workflowName,
    summary: response.description || '',
    lastUpdated: Date.now(),
    nodes,
    edges,
    stats: {
      nodes: nodes.length,
      edges: edges.length,
      runtimeMinutes,
      qualityScore
    },
    rawResponse: response
  }
}
