/**
 * AI 工作流服务
 *
 * 使用 SSE/JSON 事件流进行传输（浏览器端可断线重连并基于 Last-Event-ID 重放）
 * 协议版本：v3（统一 stream 事件）
 */

import { API_BASE, API_PATH, openSse, requestData, requestEnvelope } from '@/api'
import type {
  AbortWorkflowResult,
  SseEvent,
  StreamCallbacks,
  WorkflowChatModel
} from '@/services/types/ai/workflow'

export type {
  ActivityEvent,
  ProcessActivity,
  ProcessStatus,
  SseEvent,
  SseEventType,
  StreamCallbacks,
  WorkflowChatModel,
  StreamInterrupt,
  StreamStatus,
  WorkflowResponse,
  JobResponse,
  DependencyResponse,
  AbortWorkflowResult
} from '@/services/types/ai/workflow'

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
  model: WorkflowChatModel,
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
      await requestEnvelope<{ success: boolean }, {
        userInput: string | null
        sessionId: string
        model: WorkflowChatModel
        resumeValue?: unknown
      }>({
        baseURL: API_BASE.aiWorkflow,
        url: API_PATH.workflow.chat,
        method: 'POST',
        data: {
          userInput,
          sessionId,
          model,
          resumeValue
        }
      })

      if (closed) return

      eventSource = openSse({
        baseURL: API_BASE.aiWorkflow,
        url: API_PATH.workflow.sse,
        params: { sessionId },
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
): Promise<AbortWorkflowResult> {
  const payload: { sessionId: string; interruptId?: string } = { sessionId }
  if (interruptId) {
    payload.interruptId = interruptId
  }
  return requestData<AbortWorkflowResult, { sessionId: string; interruptId?: string }>({
    baseURL: API_BASE.aiWorkflow,
    url: API_PATH.workflow.abort,
    method: 'POST',
    data: payload
  })
}
