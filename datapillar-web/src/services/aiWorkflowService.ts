/**
 * AI 工作流服务
 *
 * 使用 SSE/JSON 事件流进行传输（浏览器端可断线重连并基于 Last-Event-ID 重放）
 */

/**
 * SSE 事件类型
 */
export type SseEventType =
  | 'agent.start'
  | 'agent.end'
  | 'tool.start'
  | 'tool.end'
  | 'interrupt'
  | 'result'
  | 'error'

/**
 * SSE 事件状态（用于前端渲染 icon/颜色）
 */
export type SseState = 'thinking' | 'invoking' | 'waiting' | 'done' | 'error'

/**
 * SSE 严重级别（用于前端映射颜色）
 */
export type SseLevel = 'info' | 'success' | 'warning' | 'error'

export interface SseEventAgent {
  id: string
  name: string
}

export interface SseEventMessage {
  role: 'system' | 'user' | 'assistant' | 'tool'
  content: string
}

export interface SseEventTool {
  name: string
  input?: unknown
  output?: unknown
}

export interface SseEventInterrupt {
  kind: string
  message: string
  questions?: string[]
  options?: Array<{ value: string; label: string; type?: string }>
}

export interface SseEventResult {
  workflow?: WorkflowResponse
}

export interface SseEventError {
  message: string
  detail?: string
}

export interface SseEvent {
  v: number
  ts: number
  event: SseEventType
  state: SseState
  level: SseLevel
  agent?: SseEventAgent
  message?: SseEventMessage
  tool?: SseEventTool
  interrupt?: SseEventInterrupt
  result?: SseEventResult
  error?: SseEventError
}

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
  userInput: string,
  sessionId: string,
  callbacks: StreamCallbacks,
  resumeValue?: unknown
): () => void {
  let closed = false
  let eventSource: EventSource | null = null

  const request = async () => {
    try {
      const startPath = resumeValue === undefined ? '/api/ai/etl/workflow/start' : '/api/ai/etl/workflow/continue'
      const response = await fetch(startPath, {
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
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
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
          if (sseEvent.event === 'interrupt' || sseEvent.event === 'result' || sseEvent.event === 'error') {
            eventSource?.close()
            eventSource = null
          }
        } catch (error) {
          callbacks.onEvent({
            v: 1,
            ts: Date.now(),
            event: 'error',
            state: 'error',
            level: 'error',
            error: { message: '解析 SSE 消息失败', detail: error instanceof Error ? error.message : String(error) },
            message: { role: 'system', content: '解析 SSE 消息失败' }
          })
          eventSource?.close()
          eventSource = null
        }
      }

      eventSource.onerror = () => {
        if (closed) return
        // 交给 EventSource 自动重连；只有当连接被显式关闭时才停止
        if (eventSource && eventSource.readyState === EventSource.CLOSED) {
          callbacks.onEvent({
            v: 1,
            ts: Date.now(),
            event: 'error',
            state: 'error',
            level: 'error',
            error: { message: 'SSE 连接已关闭' },
            message: { role: 'system', content: 'SSE 连接已关闭' }
          })
        }
      }
    } catch (error) {
      callbacks.onEvent({
        v: 1,
        ts: Date.now(),
        event: 'error',
        state: 'error',
        level: 'error',
        error: { message: '连接失败', detail: error instanceof Error ? error.message : String(error) },
        message: { role: 'system', content: '连接失败' }
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
