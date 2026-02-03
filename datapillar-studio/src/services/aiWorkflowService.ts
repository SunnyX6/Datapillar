/**
 * AI 工作流服务
 *
 * 使用 SSE/JSON 事件流进行传输（浏览器端可断线重连并基于 Last-Event-ID 重放）
 * 协议版本：v3（统一 stream 事件）
 */

import { fetchWithAuthRetry } from '@/lib/api/client'

/**
 * SSE 事件类型
 */
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

export interface StreamEvent {
  ts: number
  run_id: string
  status: StreamStatus
  activity?: ProcessActivity | null
  workflow?: WorkflowResponse | null
}

export type SseEvent = StreamEvent

/**
 * Job 响应
 */
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

/**
 * 依赖响应
 */
export interface DependencyResponse {
  jobId: number
  parentJobId: number
}

/**
 * 工作流响应
 */
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

/**
 * 消息回调
 */
export interface StreamCallbacks {
  onEvent: (event: SseEvent) => void
}

/**
 * 生成唯一会话 ID
 */
export function generateSessionId(): string {
  return `session-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

/**
 * 创建 AI 工作流消息流
 */
export function createWorkflowStream(
  userInput: string | null,
  sessionId: string,
  callbacks: StreamCallbacks,
  resumeValue?: unknown
): () => void {
  let closed = false
  let eventSource: EventSource | null = null

  const request = async () => {
    try {
      const emitLocalError = (message: string, detail?: string) => {
        callbacks.onEvent({
          ts: Date.now(),
          run_id: `local-${Date.now()}`,
          status: 'error',
          activity: {
            agent_cn: '系统',
            agent_en: 'system',
            summary: detail ? `${message}：${detail}` : message,
            event: 'llm',
            event_name: 'llm',
            status: 'error',
            interrupt: { options: [] },
            recommendations: []
          },
          workflow: null
        })
      }

      // 统一使用 /workflow/chat 端点
      const response = await fetchWithAuthRetry('/api/ai/etl/workflow/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          userInput,
          sessionId,
          resumeValue
        }),
        credentials: 'include'
      })

      if (!response.ok) {
        emitLocalError('请求失败', `HTTP ${response.status}: ${response.statusText}`)
        return
      }

      if (closed) return

      eventSource = new EventSource(`/api/ai/etl/workflow/sse?sessionId=${encodeURIComponent(sessionId)}`, {
        withCredentials: true
      })

      eventSource.onmessage = (event) => {
        if (closed) return
        try {
          const sseEvent = JSON.parse(event.data) as SseEvent
          callbacks.onEvent(sseEvent)
          if (sseEvent.status === 'done' || sseEvent.status === 'error' || sseEvent.status === 'aborted') {
            eventSource?.close()
            eventSource = null
          }
        } catch (error) {
          emitLocalError('解析 SSE 消息失败', error instanceof Error ? error.message : String(error))
          eventSource?.close()
          eventSource = null
        }
      }

      eventSource.onerror = () => {
        if (closed) return
        // 交给 EventSource 自动重连；只有当连接被显式关闭时才停止
        if (eventSource && eventSource.readyState === EventSource.CLOSED) {
          emitLocalError('SSE 连接已关闭')
        }
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      callbacks.onEvent({
        ts: Date.now(),
        run_id: `local-${Date.now()}`,
        status: 'error',
        activity: {
          agent_cn: '系统',
          agent_en: 'system',
          summary: `连接失败：${message}`,
          event: 'llm',
          event_name: 'llm',
          status: 'error',
          interrupt: { options: [] },
          recommendations: []
        },
        workflow: null
      })
    }
  }

  request()

  return () => {
    closed = true
    eventSource?.close()
    eventSource = null
  }
}

/**
 * 打断当前 run
 *
 * 打断的是 run（当前执行），不是 session（对话历史）。
 * 用户可以在打断后继续在同一个 session 发送新消息。
 */
export async function abortWorkflow(
  sessionId: string,
  interruptId?: string
): Promise<{ success: boolean; aborted: boolean; message: string }> {
  const payload: { sessionId: string; interruptId?: string } = { sessionId }
  if (interruptId) {
    payload.interruptId = interruptId
  }
  const response = await fetchWithAuthRetry('/api/ai/etl/workflow/abort', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload),
    credentials: 'include'
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`)
  }

  return response.json()
}
