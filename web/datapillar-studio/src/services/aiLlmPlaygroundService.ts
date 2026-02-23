import { API_BASE, API_PATH } from '@/lib/api'
import type {
  LlmPlaygroundChatRequest,
  LlmPlaygroundDoneEvent,
  LlmPlaygroundStreamCallbacks
} from '@/types/ai/llm'

export type {
  LlmPlaygroundChatRequest,
  LlmPlaygroundDoneEvent,
  LlmPlaygroundModelConfig,
  LlmPlaygroundStreamCallbacks
} from '@/types/ai/llm'

interface ParsedSseEvent {
  event: string
  data: string
}

function buildPlaygroundUrl(): string {
  const base = API_BASE.aiLlmPlayground.endsWith('/')
    ? API_BASE.aiLlmPlayground.slice(0, -1)
    : API_BASE.aiLlmPlayground
  return `${base}${API_PATH.aiLlmPlayground.chat}`
}

function parseSseEvent(rawBlock: string): ParsedSseEvent | null {
  const lines = rawBlock
    .split('\n')
    .map((line) => line.trimEnd())
    .filter((line) => line.length > 0)

  if (lines.length === 0) {
    return null
  }

  let event = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith(':')) {
      continue
    }
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  return {
    event,
    data: dataLines.join('\n')
  }
}

function safeParseJson(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function extractErrorMessage(raw: string): string {
  const parsed = safeParseJson(raw)
  if (parsed && typeof parsed === 'object') {
    const record = parsed as Record<string, unknown>
    if (typeof record.message === 'string' && record.message.trim().length > 0) {
      return record.message
    }
    if (typeof record.detail === 'string' && record.detail.trim().length > 0) {
      return record.detail
    }
  }
  return raw || '请求失败'
}

function resolveDelta(rawData: string): string {
  const parsed = safeParseJson(rawData)
  if (parsed && typeof parsed === 'object') {
    const record = parsed as Record<string, unknown>
    const delta = record.delta
    if (typeof delta === 'string') {
      return delta
    }
  }
  return ''
}

function resolveReasoningDelta(rawData: string): string {
  const parsed = safeParseJson(rawData)
  if (parsed && typeof parsed === 'object') {
    const record = parsed as Record<string, unknown>
    const delta = record.delta
    if (typeof delta === 'string') {
      return delta
    }
  }
  return ''
}

function resolveDone(rawData: string, fallback: string): LlmPlaygroundDoneEvent {
  const parsed = safeParseJson(rawData)
  if (parsed && typeof parsed === 'object') {
    const record = parsed as Record<string, unknown>
    const content = record.content
    const reasoning = record.reasoning
    if (typeof content === 'string') {
      return {
        content,
        reasoning: typeof reasoning === 'string' ? reasoning : undefined
      }
    }
  }
  return { content: fallback }
}

export function createLlmPlaygroundStream(
  request: LlmPlaygroundChatRequest,
  callbacks: LlmPlaygroundStreamCallbacks
): () => void {
  const controller = new AbortController()
  let closed = false

  const run = async () => {
    let bufferedContent = ''
    let doneReceived = false

    try {
      const response = await fetch(buildPlaygroundUrl(), {
        method: 'POST',
        headers: {
          Accept: 'text/event-stream',
          'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify(request),
        signal: controller.signal
      })

      if (!response.ok) {
        const errorText = await response.text()
        callbacks.onError(extractErrorMessage(errorText))
        return
      }

      if (!response.body) {
        callbacks.onError('SSE 响应流不可用')
        return
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (!closed) {
        const { value, done } = await reader.read()
        if (done) {
          break
        }

        buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')

        let separatorIndex = buffer.indexOf('\n\n')
        while (separatorIndex >= 0) {
          const rawBlock = buffer.slice(0, separatorIndex)
          buffer = buffer.slice(separatorIndex + 2)

          const parsedEvent = parseSseEvent(rawBlock)
          if (!parsedEvent) {
            separatorIndex = buffer.indexOf('\n\n')
            continue
          }

          if (parsedEvent.event === 'delta') {
            const delta = resolveDelta(parsedEvent.data)
            if (delta) {
              bufferedContent += delta
              callbacks.onDelta(delta)
            }
          } else if (parsedEvent.event === 'reasoning_delta' || parsedEvent.event === 'thinking_delta') {
            const reasoningDelta = resolveReasoningDelta(parsedEvent.data)
            if (reasoningDelta) {
              callbacks.onReasoningDelta?.(reasoningDelta)
            }
          } else if (parsedEvent.event === 'done') {
            doneReceived = true
            callbacks.onDone(resolveDone(parsedEvent.data, bufferedContent))
          } else if (parsedEvent.event === 'error') {
            callbacks.onError(extractErrorMessage(parsedEvent.data))
            return
          }

          separatorIndex = buffer.indexOf('\n\n')
        }
      }

      if (!closed && !doneReceived) {
        callbacks.onDone({ content: bufferedContent })
      }
    } catch (error) {
      if (closed || controller.signal.aborted) {
        return
      }
      callbacks.onError(error instanceof Error ? error.message : String(error))
    }
  }

  void run()

  return () => {
    closed = true
    controller.abort()
  }
}
