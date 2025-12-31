/**
 * AI 工作流服务
 *
 * 通过 SSE 事件流与 AI 服务交互
 */

/**
 * SSE 事件类型
 */
export type SSEEventType =
  | 'session_started'
  | 'agent_started'
  | 'agent_completed'
  | 'tool_called'
  | 'session_interrupted'
  | 'session_completed'
  | 'session_error'

/**
 * SSE 事件数据
 */
export interface SSEEvent {
  eventType: SSEEventType
  agent: string | null
  tool: string | null
  data: Record<string, unknown>
}

/**
 * 中断类型
 */
export type InterruptType = 'clarification' | 'component_selection' | 'feedback_request'

/**
 * 中断数据
 */
export interface InterruptData {
  type: InterruptType
  message: string
  questions?: string[]
  options?: Array<{ value: string; label: string; type?: string }>
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
 * 完成数据
 */
export interface CompletedData {
  dag_output: WorkflowResponse | null
  is_completed: boolean
}

/**
 * 事件回调
 */
export interface SSECallbacks {
  onSessionStarted?: (sessionId: string) => void
  onAgentStarted?: (agent: string, name: string) => void
  onAgentCompleted?: (agent: string, data: Record<string, unknown>) => void
  onToolCalled?: (agent: string, tool: string, data: Record<string, unknown>) => void
  onInterrupted?: (data: InterruptData) => void
  onCompleted?: (data: CompletedData) => void
  onError?: (error: string) => void
}

/**
 * 生成唯一会话 ID
 */
export function generateSessionId(): string {
  return `session-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

/**
 * 创建 AI 工作流 SSE 连接
 */
export function createWorkflowStream(
  userInput: string,
  sessionId: string,
  callbacks: SSECallbacks,
  resumeValue?: unknown
): () => void {
  const controller = new AbortController()

  const request = async () => {
    try {
      const response = await fetch('/api/ai/agent/workflow/sse', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream'
        },
        body: JSON.stringify({
          userInput,
          sessionId,
          resumeValue
        }),
        signal: controller.signal
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('无法获取响应流')
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })

        // 解析 SSE 事件
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        let eventData = ''

        for (const line of lines) {
          if (line.startsWith('event:')) {
            // event 行跳过，使用 data 中的 eventType
            continue
          } else if (line.startsWith('data:')) {
            eventData = line.slice(5).trim()
          } else if (line === '' && eventData) {
            // 空行表示事件结束
            try {
              const parsed = JSON.parse(eventData) as SSEEvent
              handleEvent(parsed, callbacks)
            } catch {
              console.error('解析 SSE 事件失败:', eventData)
            }
            eventData = ''
          }
        }
      }
    } catch (error) {
      if ((error as Error).name !== 'AbortError') {
        callbacks.onError?.((error as Error).message)
      }
    }
  }

  request()

  // 返回取消函数
  return () => controller.abort()
}

/**
 * 处理 SSE 事件
 */
function handleEvent(event: SSEEvent, callbacks: SSECallbacks): void {
  switch (event.eventType) {
    case 'session_started':
      callbacks.onSessionStarted?.(event.data.session_id as string)
      break

    case 'agent_started':
      callbacks.onAgentStarted?.(event.agent || '', (event.data.name as string) || '')
      break

    case 'agent_completed':
      callbacks.onAgentCompleted?.(event.agent || '', event.data)
      break

    case 'tool_called':
      callbacks.onToolCalled?.(event.agent || '', event.tool || '', event.data)
      break

    case 'session_interrupted':
      callbacks.onInterrupted?.(event.data as unknown as InterruptData)
      break

    case 'session_completed':
      callbacks.onCompleted?.(event.data as unknown as CompletedData)
      break

    case 'session_error':
      callbacks.onError?.(event.data.error as string)
      break
  }
}
