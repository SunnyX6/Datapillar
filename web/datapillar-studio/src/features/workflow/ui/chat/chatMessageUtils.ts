import type { AgentActivity } from '@/features/workflow/state'
import type { StreamStatus } from '@/services/aiWorkflowService'

const STREAM_STATUS_LABEL: Record<StreamStatus, string> = {
  running: 'In progress',
  done: 'Completed',
  error: 'failed',
  aborted: 'Stopped'
}

export const getProcessRowTitle = (row: AgentActivity): string => {
  return row.agent_cn || row.agent_en || ''
}

export const getProcessRowMessage = (row: AgentActivity): string => row.event_name

export const getProcessStatusLabel = (streamStatus: StreamStatus | undefined): string => {
  return streamStatus ? STREAM_STATUS_LABEL[streamStatus] : ''
}
