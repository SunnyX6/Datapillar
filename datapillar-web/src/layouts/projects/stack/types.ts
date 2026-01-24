export type StackEnv = 'PROD' | 'STAGING' | 'DEV'

export type WorkflowStatus = 'healthy' | 'running' | 'warning' | 'error' | 'paused'

export interface WorkflowDefinition {
  id: string
  name: string
  description: string
  schedule: string
  status: WorkflowStatus
  lastRun: string
  nextRun: string
  avgDuration: string
  owner: string
  tags: string[]
}

export type TaskNodeType = 'source' | 'transform' | 'join' | 'sink'

export interface TaskNode {
  id: string
  name: string
  type: TaskNodeType
  status: 'running' | 'success' | 'failed' | 'waiting' | 'idle'
  duration: string
  startTime: string
  owner: string
  description: string
  x: number
  y: number
}

export interface StackEdge {
  id: string
  source: string
  target: string
  animated?: boolean
}

export interface PipelineRun {
  id: string
  status: 'success' | 'failed' | 'running'
  startTime: string
  duration: string
  trigger: 'Schedule' | 'Manual' | 'Webhook'
}

export interface TaskRunHistory {
  runId: string
  pipelineRunId: string
  status: 'success' | 'failed' | 'running' | 'retried'
  startTime: string
  duration: string
  recordsProcessed: string
  memoryUsage: string
}
