import { memo, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type JSX, type CSSProperties } from 'react'
import { ArrowUp, Bot, Loader2, User } from 'lucide-react'
import { cn } from '@/lib/utils'
import { messageWidthClassMap } from '@/design-tokens/dimensions'
import { useWorkflowStudioStore } from '@/stores'
import { convertAIResponseToGraph } from '@/services/workflowStudioService'
import { createWorkflowStream, generateSessionId, type CompletedData, type InterruptData } from '@/services/aiWorkflowService'

type WorkflowStudioSnapshot = ReturnType<typeof useWorkflowStudioStore.getState>

const prefetchWorkflowCanvas = (() => {
  let started = false
  return () => {
    if (started) {
      return
    }
    started = true
    void import('@/layouts/workflow/WorkflowCanvasRenderer')
  }
})()

const hasIdleCallback = typeof window !== 'undefined' && 'requestIdleCallback' in window
const scheduleIdle = (callback: () => void) => {
  if (hasIdleCallback && typeof window.requestIdleCallback === 'function') {
    return window.requestIdleCallback(() => callback())
  }
  return window.setTimeout(callback, 32)
}
const cancelIdle = (handle: number | null) => {
  if (!handle) return
  if (hasIdleCallback && typeof window.cancelIdleCallback === 'function') {
    window.cancelIdleCallback(handle)
    return
  }
  clearTimeout(handle)
}

const DEFAULT_MESSAGE_HEIGHT = 120
const VIRTUAL_OVERSCAN = 320

type VirtualizedMessageListProps = {
  messages: WorkflowStudioSnapshot['messages']
  renderMessage: (message: WorkflowStudioSnapshot['messages'][number]) => JSX.Element
  scrollRef: React.RefObject<HTMLDivElement | null>
  className?: string
}

type MeasuredItem = {
  message: WorkflowStudioSnapshot['messages'][number]
  top: number
  height: number
}

function VirtualizedMessageList({ messages, renderMessage, scrollRef, className }: VirtualizedMessageListProps) {
  const heightsRef = useRef<Map<string, number>>(new Map())
  const [heightsVersion, setHeightsVersion] = useState(0)
  const [viewport, setViewport] = useState({ top: 0, height: 0 })
  const [measurements, setMeasurements] = useState<{ items: MeasuredItem[]; totalHeight: number }>({
    items: [],
    totalHeight: 0
  })

  const updateViewport = useCallback(() => {
    const container = scrollRef.current
    if (!container) return
    setViewport({
      top: container.scrollTop,
      height: container.clientHeight
    })
  }, [scrollRef])

  useLayoutEffect(() => {
    const container = scrollRef.current
    if (!container) return
    updateViewport()
    const handleScroll = () => updateViewport()
    container.addEventListener('scroll', handleScroll, { passive: true })
    return () => container.removeEventListener('scroll', handleScroll)
  }, [scrollRef, updateViewport])

  useEffect(() => {
    const container = scrollRef.current
    if (!container) return
    const handleResize = () => updateViewport()
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [scrollRef, updateViewport])

  useEffect(() => {
    setMeasurements(() =>
      messages.reduce<{ items: MeasuredItem[]; totalHeight: number }>(
        (acc, message) => {
          const height = heightsRef.current.get(message.id) ?? DEFAULT_MESSAGE_HEIGHT
          const top = acc.totalHeight
          const nextItem: MeasuredItem = { message, top, height }
          return {
            items: [...acc.items, nextItem],
            totalHeight: top + height
          }
        },
        { items: [], totalHeight: 0 }
      )
    )
  }, [messages, heightsVersion])

  const visibleItems = useMemo(() => {
    const start = viewport.top - VIRTUAL_OVERSCAN
    const end = viewport.top + viewport.height + VIRTUAL_OVERSCAN
    return measurements.items.filter((item) => item.top + item.height >= start && item.top <= end)
  }, [measurements.items, viewport.height, viewport.top])

  const handleHeight = useCallback((id: string, height: number) => {
    const stored = heightsRef.current.get(id) ?? 0
    if (Math.abs(stored - height) < 0.5) {
      return
    }
    heightsRef.current.set(id, height)
    setHeightsVersion((version) => version + 1)
  }, [])

  return (
    <div ref={scrollRef} className={cn('relative overflow-y-auto scrollbar-invisible', className)}>
      <div
        style={{ '--virtual-height': `${measurements.totalHeight}px` } as CSSProperties}
        className="relative [height:var(--virtual-height)]"
      >
        {visibleItems.map(({ message, top }) => (
          <MeasuredMessage key={message.id} id={message.id} top={top} onHeight={handleHeight}>
            {renderMessage(message)}
          </MeasuredMessage>
        ))}
      </div>
    </div>
  )
}

function MeasuredMessage({
  id,
  top,
  onHeight,
  children
}: {
  id: string
  top: number
  onHeight: (id: string, height: number) => void
  children: JSX.Element
}) {
  const ref = useRef<HTMLDivElement | null>(null)

  useLayoutEffect(() => {
    const node = ref.current
    if (!node) return
    const measure = () => {
      const height = node.getBoundingClientRect().height
      onHeight(id, height)
    }
    measure()
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(() => measure())
      observer.observe(node)
      return () => observer.disconnect()
    }
    const handle = () => measure()
    window.addEventListener('resize', handle)
    return () => window.removeEventListener('resize', handle)
  }, [id, onHeight])

  return (
    <div ref={ref} style={{ transform: `translateY(${top}px)` }} className="absolute left-0 w-full pb-4">
      {children}
    </div>
  )
}

export function ChatPanel() {
  const messages = useWorkflowStudioStore((state) => state.messages)
  const isGenerating = useWorkflowStudioStore((state) => state.isGenerating)
  const addMessage = useWorkflowStudioStore((state) => state.addMessage)
  const setGenerating = useWorkflowStudioStore((state) => state.setGenerating)
  const setWorkflow = useWorkflowStudioStore((state) => state.setWorkflow)
  const setLastPrompt = useWorkflowStudioStore((state) => state.setLastPrompt)
  const [input, setInput] = useState('')
  const [statusText, setStatusText] = useState('构建编排图中...')
  const scrollRef = useRef<HTMLDivElement | null>(null)
  const cancelStreamRef = useRef<(() => void) | null>(null)
  const sessionIdRef = useRef<string>(generateSessionId())

  useEffect(() => {
    const handle = scheduleIdle(() => {
      prefetchWorkflowCanvas()
    })
    return () => cancelIdle(handle)
  }, [])

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [messages, isGenerating])

  // 清理 SSE 连接
  useEffect(() => {
    return () => {
      if (cancelStreamRef.current) {
        cancelStreamRef.current()
      }
    }
  }, [])

  const typingIconSize = 14
  const sendIconSize = 15

  const nextMessageId = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`

  const handleSend = async () => {
    if (!input.trim() || isGenerating) return
    prefetchWorkflowCanvas()
    const prompt = input.trim()
    const now = Date.now()
    addMessage({
      id: nextMessageId(),
      role: 'user',
      content: prompt,
      timestamp: now
    })
    setInput('')
    setLastPrompt(prompt)
    setGenerating(true)
    setStatusText('正在连接 AI 服务...')

    // 取消之前的流
    if (cancelStreamRef.current) {
      cancelStreamRef.current()
    }

    // 创建新的 SSE 连接
    cancelStreamRef.current = createWorkflowStream(prompt, sessionIdRef.current, {
      onAgentStarted: (_agent: string, name: string) => {
        setStatusText(`${name} 正在工作...`)
      },
      onToolCalled: (_agent: string, tool: string) => {
        setStatusText(`正在调用 ${tool}...`)
      },
      onInterrupted: (data: InterruptData) => {
        setGenerating(false)
        addMessage({
          id: nextMessageId(),
          role: 'assistant',
          content: `${data.message}\n${data.questions?.join('\n') || ''}`,
          timestamp: Date.now()
        })
      },
      onCompleted: (data: CompletedData) => {
        setGenerating(false)
        if (data.dag_output) {
          const workflow = convertAIResponseToGraph(data.dag_output)
          setWorkflow(workflow)
          addMessage({
            id: nextMessageId(),
            role: 'assistant',
            content: `生成完成：${workflow.name}，共 ${workflow.stats.nodes} 个节点、${workflow.stats.edges} 条连线。${workflow.summary}`,
            timestamp: Date.now()
          })
          // 生成新的 sessionId 用于下次对话
          sessionIdRef.current = generateSessionId()
        } else {
          addMessage({
            id: nextMessageId(),
            role: 'assistant',
            content: '工作流生成完成，但未返回结果。',
            timestamp: Date.now()
          })
        }
      },
      onError: (error: string) => {
        setGenerating(false)
        addMessage({
          id: nextMessageId(),
          role: 'assistant',
          content: `生成工作流失败：${error}`,
          timestamp: Date.now()
        })
      }
    })
  }

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      handleSend()
    }
  }

  return (
    <aside className="w-full h-full flex-shrink-0 bg-white/90 dark:bg-slate-900/95 flex flex-col overflow-hidden">
      <VirtualizedMessageList
        messages={messages}
        scrollRef={scrollRef}
        className="flex-1 p-5 text-body-sm"
        renderMessage={(message) => <ChatBubble key={message.id} message={message} />}
      />
      {isGenerating && (
        <div className="flex items-center gap-2.5 px-5 pb-4">
          <div className="rounded-lg bg-gradient-to-br from-indigo-600 to-indigo-500 flex items-center justify-center shadow-sm w-8 h-8">
            <Bot size={typingIconSize} className="text-white" />
          </div>
          <div className="bg-slate-100 dark:bg-slate-800/70 rounded-lg border border-slate-200/50 dark:border-slate-700/50 flex items-center gap-2 text-slate-600 dark:text-slate-300 px-3 py-2 text-body-sm">
            <Loader2 size={typingIconSize} className="animate-spin text-indigo-500" />
            {statusText}
          </div>
        </div>
      )}

      <div className="border-t border-slate-200/80 dark:border-white/10 p-3">
        <div className="relative">
          <textarea
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={prefetchWorkflowCanvas}
            className={cn(
              'w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white/90 dark:bg-slate-900/60 text-slate-700 dark:text-slate-100 resize-none focus:outline-none focus:ring-2 focus:ring-indigo-400 dark:focus:ring-indigo-500 scrollbar-invisible',
              'text-body-sm min-h-12 pl-4 pr-11 py-1.5 leading-snug'
            )}
            placeholder="描述你的数据工作流需求..."
          />
          <button
            type="button"
            onClick={handleSend}
            disabled={isGenerating || !input.trim()}
            className="absolute right-1.5 top-1/2 -translate-y-[65%] h-7 w-7 flex items-center justify-center border border-indigo-200 dark:border-indigo-800 rounded-full text-indigo-600 hover:text-white hover:bg-indigo-500 disabled:text-slate-400 disabled:border-slate-300 dark:disabled:border-slate-700 transition-colors"
          >
            <ArrowUp size={sendIconSize} />
          </button>
        </div>
        <p className="text-slate-500 dark:text-slate-400 text-center mt-1 text-micro">AI 可能出错，请务必验证逻辑。</p>
      </div>
    </aside>
  )
}

const ChatBubble = memo(
  function ChatBubble({ message }: { message: WorkflowStudioSnapshot['messages'][number] }) {
    const isUser = message.role === 'user'
    const formattedTime = useMemo(() => new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), [message.timestamp])
    const avatarIconSize = 16

    return (
      <div
        className={cn('flex items-start gap-3', isUser && 'flex-row-reverse')}
        style={{ contentVisibility: 'auto', containIntrinsicSize: '0 120px' }}
      >
        <div
          className={cn(
            'rounded-full flex items-center justify-center w-9 h-9',
            isUser ? 'bg-slate-200 text-slate-600' : 'bg-indigo-600 text-white'
          )}
        >
          {isUser ? <User size={avatarIconSize} /> : <Bot size={avatarIconSize} />}
        </div>
        <div className={cn('flex flex-col gap-1', messageWidthClassMap.default, isUser ? 'items-end' : 'items-start')}>
          <div
            className={cn(
              'rounded-2xl leading-relaxed border whitespace-pre-wrap',
              isUser
                ? 'bg-indigo-600 text-white rounded-tr-none border-indigo-600/60'
                : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-100 rounded-tl-none border-slate-200 dark:border-slate-700',
              'px-4 py-2.5 text-body-sm'
            )}
          >
            {message.content}
          </div>
          <span className="text-slate-400 px-1 text-micro">{formattedTime}</span>
        </div>
      </div>
    )
  },
  (previous, next) =>
    previous.message.id === next.message.id &&
    previous.message.content === next.message.content &&
    previous.message.timestamp === next.message.timestamp
)
