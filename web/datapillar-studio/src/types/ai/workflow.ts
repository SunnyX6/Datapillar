export type SseEventType = 'stream'

export type ActivityEvent = 'llm' | 'tool' | 'interrupt'

export type ProcessStatus = 'running' | 'waiting' | 'done' | 'error' | 'aborted'

export interface StreamInterrupt {
  options: string[]
  interrupt_id?: string
}

export interface ProcessActivity {
  agent_cn: string
  agent_en: string
  summary: string
  event: ActivityEvent
  event_name: string
  status: ProcessStatus
  interrupt?: StreamInterrupt
  recommendations?: string[]
}

export type StreamStatus = 'running' | 'done' | 'error' | 'aborted'

export interface JobResponse {
  id: number | null
  jobName: string
  jobType: number | null
  jobTypeCode: string
  jobTypeName: string | null
  jobParams: Record<string, unknown>
  timeoutSeconds: number
  maxRetryTimes: number
  retryInterval: number
  priority: number
  positionX: number
  positionY: number
  description: string | null
}

export interface DependencyResponse {
  jobId: number
  parentJobId: number
}

export interface WorkflowResponse {
  workflowName: string
  triggerType: number
  triggerValue: string | null
  timeoutSeconds: number
  maxRetryTimes: number
  priority: number
  description: string | null
  jobs: JobResponse[]
  dependencies: DependencyResponse[]
}

export interface StreamEvent {
  ts: number
  run_id: string
  status: StreamStatus
  activity?: ProcessActivity | null
  workflow?: WorkflowResponse | null
}

export type SseEvent = StreamEvent

export interface StreamCallbacks {
  onEvent: (event: SseEvent) => void
}

export interface AbortWorkflowResult {
  success: boolean
  aborted: boolean
  message: string
}
