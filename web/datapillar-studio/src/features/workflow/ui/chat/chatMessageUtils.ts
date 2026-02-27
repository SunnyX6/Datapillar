import type { AgentActivity } from '@/features/workflow/state'
import type { StreamStatus } from '@/services/aiWorkflowService'

const STREAM_STATUS_LABEL: Record<StreamStatus, string> = {
  running: '进行中',
  done: '已完成',
  error: '失败',
  aborted: '已停止'
}

export const getProcessRowTitle = (row: AgentActivity): string => {
  return row.agent_cn || row.agent_en || ''
}

export const getProcessRowMessage = (row: AgentActivity): string => row.event_name

export const getProcessStatusLabel = (streamStatus: StreamStatus | undefined): string => {
  return streamStatus ? STREAM_STATUS_LABEL[streamStatus] : ''
}
