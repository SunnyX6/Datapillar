import { memo, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type JSX, type CSSProperties, type FormEvent, type KeyboardEvent as ReactKeyboardEvent, type RefObject } from 'react'
import { Activity, AlertTriangle, ArrowUp, Bot, CheckCircle2, ChevronDown, Loader2, Square, User, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useWorkflowStudioStore, type AgentActivity, type ChatMessageOption } from '@/stores'
import { upsertAgentActivityByAgent } from '@/layouts/workflow/utils'
import { convertAIResponseToGraph } from '@/services/workflowStudioService'
import {
  abortWorkflow,
  createWorkflowStream,
  generateSessionId,
  type ProcessStatus,
  type SseEvent,
  type UiAction,
  type UiPayload,
  type WorkflowResponse
} from '@/services/aiWorkflowService'

type WorkflowStudioSnapshot = ReturnType<typeof useWorkflowStudioStore.getState>

const DEFAULT_WORKFLOW_INTRO_MESSAGE =
  '你好呀！我是 Sunny，Datapillar 数仓团队的负责人 👋。我的团队专精于 ETL 任务，比如数据清洗、工作流设计和 SQL 开发。有什么数据开发需求想要我帮忙吗？'

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

const PROCESS_STATUS_LABEL: Record<ProcessStatus, string> = {
  running: '进行中',
  waiting: '等待补充',
  done: '已完成',
  error: '失败',
  aborted: '已停止'
}

type VirtualizedMessageListProps = {
  messages: WorkflowStudioSnapshot['messages']
  renderMessage: (message: WorkflowStudioSnapshot['messages'][number]) => JSX.Element
  scrollRef: RefObject<HTMLDivElement | null>
  className?: string
  footer?: JSX.Element | null
}

type MeasuredItem = {
  message: WorkflowStudioSnapshot['messages'][number]
  top: number
  height: number
}

function VirtualizedMessageList({ messages, renderMessage, scrollRef, className, footer }: VirtualizedMessageListProps) {
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
      {/* Footer 在消息末尾，不是绝对定位 */}
      {footer}
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
  const updateMessage = useWorkflowStudioStore((state) => state.updateMessage)
  const setGenerating = useWorkflowStudioStore((state) => state.setGenerating)
  const setWorkflow = useWorkflowStudioStore((state) => state.setWorkflow)
  const setLastPrompt = useWorkflowStudioStore((state) => state.setLastPrompt)
  const [input, setInput] = useState('')
  const scrollRef = useRef<HTMLDivElement | null>(null)
  const cancelStreamRef = useRef<(() => void) | null>(null)
  const sessionIdRef = useRef<string>(generateSessionId())
  const streamingMessageIdRef = useRef<string | null>(null)
  const waitingResumeRef = useRef(false)

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

  const nextMessageId = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`

  // 进入页面时给一个静态引导语：不触发后端请求，避免 LLM 卡住导致无限加载
  useEffect(() => {
    const store = useWorkflowStudioStore.getState()
    if (store.isInitialized) return
    if (store.messages.length > 0) {
      store.setInitialized(true)
      return
    }

    store.addMessage({
      id: nextMessageId(),
      role: 'assistant',
      content: DEFAULT_WORKFLOW_INTRO_MESSAGE,
      timestamp: Date.now(),
      processRows: [],
      isStreaming: false
    })
    store.setInitialized(true)
  }, [])

  const sendIconSize = 15
  const MAX_PROCESS_ROWS = 200

  const handleSseEvent = useCallback(
    (evt: SseEvent) => {
      const msgId = streamingMessageIdRef.current
      if (!msgId) return

      if (evt.event === 'process') {
        const activity = evt.activity
        const timestamp = evt.ts ?? Date.now()
        const activityId = activity.id || `phase:${activity.phase}`
        const nextActivity: AgentActivity = {
          ...activity,
          id: activityId,
          title: activity.title || activity.phase,
          timestamp
        }
        updateMessage(msgId, (currentMsg) => ({
          ...currentMsg,
          processRows: upsertAgentActivityByAgent(currentMsg.processRows ?? currentMsg.agentRows, nextActivity, MAX_PROCESS_ROWS)
        }))
        return
      }

      if (evt.event !== 'reply') {
        return
      }

      setGenerating(false)
      const reply = evt.reply
      const uiPayload = reply.render.type === 'ui' ? (reply.payload as UiPayload | null) : null
      waitingResumeRef.current = reply.status === 'waiting'

      if (reply.status === 'done' && reply.render.type === 'workflow' && reply.payload && typeof reply.payload === 'object') {
        const graph = convertAIResponseToGraph(reply.payload as WorkflowResponse)
        setWorkflow(graph)
        sessionIdRef.current = generateSessionId()
      }

      updateMessage(msgId, {
        content: reply.message,
        isStreaming: false,
        uiPayload: uiPayload ?? undefined,
        options: undefined
      })
      streamingMessageIdRef.current = null
    },
    [setGenerating, setWorkflow, updateMessage]
  )

  const startStream = useCallback(
    (prompt: string, resumeValue?: unknown) => {
      if (isGenerating) return
      prefetchWorkflowCanvas()
      const now = Date.now()
      if (prompt.trim()) {
        addMessage({
          id: nextMessageId(),
          role: 'user',
          content: prompt.trim(),
          timestamp: now
        })
      }
      setInput('')
      setLastPrompt(prompt)
      setGenerating(true)

      const assistantMsgId = nextMessageId()
      streamingMessageIdRef.current = assistantMsgId
      addMessage({
        id: assistantMsgId,
        role: 'assistant',
        content: '',
        timestamp: Date.now(),
        processRows: [],
        isStreaming: true
      })

      if (cancelStreamRef.current) {
        cancelStreamRef.current()
      }

      cancelStreamRef.current = createWorkflowStream(resumeValue ? null : prompt, sessionIdRef.current, {
        onEvent: handleSseEvent
      }, resumeValue)
    },
    [addMessage, handleSseEvent, isGenerating, setGenerating, setLastPrompt]
  )

  const handleSend = () => {
    if (!input.trim() || isGenerating) return
    const prompt = input.trim()
    const shouldResume = waitingResumeRef.current
    waitingResumeRef.current = false
    startStream(prompt, shouldResume ? prompt : undefined)
  }

  const handleUiAction = useCallback(
    (action: UiAction) => {
      if (isGenerating) return
      const label = action.label || action.value || '已选择'
      waitingResumeRef.current = false
      startStream(label, {
        kind: 'actions',
        action: {
          type: action.type,
          label: action.label,
          value: action.value
        }
      })
    },
    [isGenerating, startStream]
  )

  const handleUiFormSubmit = useCallback(
    (payload: UiPayload, values: Record<string, string>) => {
      if (isGenerating) return
      if (payload.kind !== 'form') return
      const summaries = payload.fields
        .map((field) => {
          const value = values[field.id]
          if (!value) return null
          return `${field.label || field.id}：${value}`
        })
        .filter(Boolean) as string[]
      const displayText = summaries.length > 0 ? `已提交：${summaries.join('，')}` : '已提交'
      waitingResumeRef.current = false
      startStream(displayText, {
        kind: 'form',
        values
      })
    },
    [isGenerating, startStream]
  )

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      handleSend()
    }
  }

  // 打断当前 run
  const handleAbort = useCallback(async () => {
    if (!isGenerating) return

    try {
      // 先关闭 SSE 连接
      if (cancelStreamRef.current) {
        cancelStreamRef.current()
        cancelStreamRef.current = null
      }

      // 调用后端 abort API
      await abortWorkflow(sessionIdRef.current)

      // 更新 UI 状态
      setGenerating(false)
      waitingResumeRef.current = false

      // 更新消息状态
      const msgId = streamingMessageIdRef.current
      if (msgId) {
        updateMessage(msgId, {
          content: '已停止',
          isStreaming: false
        })
        streamingMessageIdRef.current = null
      }
    } catch (error) {
      console.error('[Chat] Abort 失败:', error)
    }
  }, [isGenerating, updateMessage, setGenerating])

  // ESC 键打断
  useEffect(() => {
    const handleEsc = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && isGenerating) {
        event.preventDefault()
        handleAbort()
      }
    }

    document.addEventListener('keydown', handleEsc)
    return () => document.removeEventListener('keydown', handleEsc)
  }, [isGenerating, handleAbort])

  return (
    <aside className="w-full h-full flex-shrink-0 bg-white/90 dark:bg-slate-900/95 flex flex-col overflow-hidden">
      <VirtualizedMessageList
        messages={messages}
        scrollRef={scrollRef}
        className="flex-1 p-5 text-body-sm"
        renderMessage={(message) => (
          <ChatBubble
            key={message.id}
            message={message}
            onActionClick={handleUiAction}
            onFormSubmit={handleUiFormSubmit}
            onOptionClick={(option) => {
              // 点击选项后，设置输入并发送
              const value = option.path || option.value || option.name
              setInput(value)
              // 自动发送
              setTimeout(() => {
                const btn = document.querySelector('[data-send-btn]') as HTMLButtonElement | null
                btn?.click()
              }, 50)
            }}
          />
        )}
      />

      <div className="border-t border-slate-200/80 dark:border-white/10 p-3">
        <div className="relative">
          <textarea
            value={input}
            rows={1}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={prefetchWorkflowCanvas}
            className={cn(
              'w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white/90 dark:bg-slate-900/60 text-slate-700 dark:text-slate-100 resize-none focus:outline-none focus:ring-2 focus:ring-indigo-400 dark:focus:ring-indigo-500 scrollbar-invisible',
              'text-body-sm pl-4 pr-11 py-2.5 leading-normal'
            )}
            placeholder="描述你的数据工作流需求..."
          />
          <button
            type="button"
            data-send-btn
            onClick={isGenerating ? handleAbort : handleSend}
            disabled={!isGenerating && !input.trim()}
            className={cn(
              'absolute right-1.5 top-1/2 -translate-y-[65%] h-7 w-7 flex items-center justify-center border rounded-full transition-colors',
              isGenerating
                ? 'border-red-200 dark:border-red-800 text-red-500 hover:text-white hover:bg-red-500'
                : 'border-indigo-200 dark:border-indigo-800 text-indigo-600 hover:text-white hover:bg-indigo-500 disabled:text-slate-400 disabled:border-slate-300 dark:disabled:border-slate-700'
            )}
            title={isGenerating ? '停止 (ESC)' : '发送'}
          >
            {isGenerating ? <Square size={sendIconSize - 2} fill="currentColor" /> : <ArrowUp size={sendIconSize} />}
          </button>
        </div>
        <p className="text-slate-500 dark:text-slate-400 text-center mt-1 text-micro">AI 可能出错，请务必验证逻辑。</p>
      </div>
    </aside>
  )
}

const ChatBubble = memo(
  function ChatBubble({
    message,
    onOptionClick,
    onActionClick,
    onFormSubmit
  }: {
    message: WorkflowStudioSnapshot['messages'][number]
    onOptionClick?: (option: ChatMessageOption) => void
    onActionClick?: (action: UiAction) => void
    onFormSubmit?: (payload: UiPayload, values: Record<string, string>) => void
  }) {
    const isUser = message.role === 'user'
    const formattedTime = useMemo(
      () => new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      [message.timestamp]
    )
    const avatarIconSize = 16
    const processRows = useMemo(
      () => message.processRows ?? message.agentRows ?? [],
      [message.processRows, message.agentRows]
    )
    const hasProcessRows = !isUser && processRows.length > 0
    const isStreaming = message.isStreaming === true
    const uiPayload = !isUser ? message.uiPayload : undefined
    const actionsPayload = uiPayload?.kind === 'actions' ? uiPayload : null
    const formPayload = uiPayload?.kind === 'form' ? uiPayload : null
    const infoPayload = uiPayload?.kind === 'info' ? uiPayload : null
    const hasOptions = !isUser && message.options && message.options.length > 0 && !actionsPayload
    const [isExpanded, setIsExpanded] = useState(false)

    const latestProcessRow = useMemo(() => {
      if (processRows.length === 0) return null
      return processRows.reduce((latest, row) => (row.timestamp > latest.timestamp ? row : latest), processRows[0])
    }, [processRows])

    const [formValues, setFormValues] = useState<Record<string, string>>({})
    const normalizedFormValues = useMemo(() => {
      if (!formPayload) return {}
      const next: Record<string, string> = {}
      formPayload.fields.forEach((field) => {
        next[field.id] = formValues[field.id] ?? ''
      })
      return next
    }, [formPayload, formValues])

    const isFormValid = useMemo(() => {
      if (!formPayload) return false
      return formPayload.fields.every((field) => {
        if (!field.required) return true
        return Boolean(normalizedFormValues[field.id]?.trim())
      })
    }, [formPayload, normalizedFormValues])

    const handleFormValueChange = useCallback((id: string, value: string) => {
      setFormValues((current) => ({ ...current, [id]: value }))
    }, [])

    const handleFormSubmit = useCallback(
      (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        if (!formPayload || !isFormValid) return
        onFormSubmit?.(formPayload, normalizedFormValues)
      },
      [formPayload, normalizedFormValues, isFormValid, onFormSubmit]
    )

    const getRowBadgeClass = useCallback((row: AgentActivity) => {
      if (row.status === 'done') return 'bg-emerald-50 border-emerald-100 text-emerald-600 dark:bg-emerald-950/40 dark:border-emerald-900/50 dark:text-emerald-400'
      if (row.status === 'waiting' || row.status === 'aborted') return 'bg-amber-50 border-amber-100 text-amber-600 dark:bg-amber-950/30 dark:border-amber-900/50 dark:text-amber-400'
      if (row.status === 'error') return 'bg-red-50 border-red-100 text-red-600 dark:bg-red-950/30 dark:border-red-900/50 dark:text-red-400'
      return 'bg-slate-50 border-slate-100 text-slate-500 dark:bg-slate-800/40 dark:border-slate-700/60 dark:text-slate-300'
    }, [])

    const RowIcon = useCallback(({ row }: { row: AgentActivity }) => {
      if (row.status === 'error') return <XCircle size={10} strokeWidth={2.75} />
      if (row.status === 'waiting' || row.status === 'aborted') return <AlertTriangle size={10} strokeWidth={2.75} />
      if (row.status === 'done') return <CheckCircle2 size={10} strokeWidth={2.75} />
      return <Activity size={10} strokeWidth={2.75} />
    }, [])

    const buildRowMessage = useCallback((row: AgentActivity) => {
      const detail = row.detail || PROCESS_STATUS_LABEL[row.status]
      const actor = row.actor ? `${row.actor} · ` : ''
      const progress = typeof row.progress === 'number' ? ` ${row.progress}%` : ''
      return `${actor}${detail}${progress}`.trim()
    }, [])

    const latestStatusLabel = latestProcessRow ? PROCESS_STATUS_LABEL[latestProcessRow.status] : '处理中'
    const latestProcessLabel = latestProcessRow ? `${latestProcessRow.title} · ${latestStatusLabel}` : '处理中'

    const infoTone = infoPayload?.level === 'error'
      ? 'border-red-100 bg-red-50 text-red-600 dark:border-red-900/40 dark:bg-red-950/30 dark:text-red-300'
      : infoPayload?.level === 'warning'
        ? 'border-amber-100 bg-amber-50 text-amber-600 dark:border-amber-900/40 dark:bg-amber-950/30 dark:text-amber-300'
        : 'border-slate-200 bg-slate-50 text-slate-600 dark:border-slate-700/60 dark:bg-slate-800/40 dark:text-slate-300'

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
        <div className={cn('flex flex-col gap-1 max-w-[80%]', isUser ? 'items-end' : 'items-start')}>
          <div
            className={cn(
              'rounded-2xl leading-relaxed border whitespace-pre-wrap',
              isUser
                ? 'bg-indigo-600 text-white rounded-tr-none border-indigo-600/60'
                : 'w-full bg-white dark:bg-slate-900 text-slate-700 dark:text-slate-100 rounded-tl-none border-slate-200 dark:border-slate-700',
              'px-4 py-2.5 text-body-sm'
            )}
          >
            {/* 流式状态：直接渲染过程行 */}
            {isStreaming && hasProcessRows && (
              <div className="space-y-2 mb-3 pb-3 border-b border-slate-100 dark:border-slate-700/50">
                <div className="px-1 flex items-center gap-2">
                  <Loader2 size={10} className="animate-spin text-indigo-500" />
                  <span className="text-nano font-black text-indigo-500 uppercase tracking-widest">过程动态</span>
                </div>
                {processRows.map((row) => (
                  <div key={row.id} className="flex items-center gap-3">
                    <div className={cn('w-5 h-5 rounded border flex items-center justify-center shrink-0', getRowBadgeClass(row))}>
                      <RowIcon row={row} />
                    </div>
                    <span className="text-micro font-black text-slate-900 dark:text-slate-100 uppercase tracking-tighter shrink-0 w-24">
                      {row.title}
                    </span>
                    <span className="text-legal font-medium text-slate-400 truncate flex-1">
                      {buildRowMessage(row)}
                    </span>
                  </div>
                ))}
                <div className="flex items-center gap-3 pt-1">
                  <div className="flex space-x-1">
                    <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                    <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '200ms' }} />
                    <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '400ms' }} />
                  </div>
                  <span className="text-micro font-bold text-indigo-500 tracking-tight italic">
                    {latestProcessLabel}
                  </span>
                </div>
              </div>
            )}

            {/* 流式状态但没有过程行：显示加载中 */}
            {isStreaming && !hasProcessRows && (
              <div className="flex items-center gap-3 py-2">
                <div className="flex space-x-1">
                  <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                  <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '200ms' }} />
                  <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '400ms' }} />
                </div>
                <span className="text-micro font-bold text-indigo-500 tracking-tight italic">处理中</span>
              </div>
            )}

            {/* 非流式状态且有过程行：可折叠 */}
            {!isStreaming && hasProcessRows && (
              <div className="mb-3 pb-3 border-b border-slate-100 dark:border-slate-700/50">
                <button
                  type="button"
                  onClick={() => setIsExpanded(!isExpanded)}
                  className="w-full flex items-center justify-between py-1 hover:opacity-80 transition-opacity"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-nano font-bold text-slate-400 uppercase tracking-widest">过程记录</span>
                    <span className="text-nano text-slate-300">({processRows.length})</span>
                  </div>
                  <div className="flex items-center gap-1">
                    <CheckCircle2 size={12} className="text-emerald-500" />
                    <ChevronDown
                      size={12}
                      className={cn('text-slate-400 transition-transform', isExpanded && 'rotate-180')}
                    />
                  </div>
                </button>
                {isExpanded && (
                  <div className="space-y-2 pt-3">
                    {processRows.map((row) => (
                      <div key={row.id} className="flex items-center gap-3">
                        <div
                          className={cn('w-5 h-5 rounded border flex items-center justify-center shrink-0', getRowBadgeClass(row))}
                        >
                          <RowIcon row={row} />
                        </div>
                        <span className="text-micro font-black text-slate-900 dark:text-slate-100 uppercase tracking-tighter shrink-0 w-24">
                          {row.title}
                        </span>
                        <span className="text-legal font-medium text-slate-400 truncate flex-1">
                          {buildRowMessage(row)}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 消息内容 */}
            {message.content}

            {/* UI 提示 */}
            {infoPayload && (
              <div className="mt-3 pt-3 border-t border-slate-100 dark:border-slate-700/50">
                <div className={cn('rounded-lg border px-3 py-2 text-legal leading-relaxed', infoTone)}>
                  {infoPayload.items.map((item, index) => (
                    <div key={`${item}-${index}`}>{item}</div>
                  ))}
                </div>
              </div>
            )}

            {/* UI 操作 */}
            {actionsPayload && (
              <div className="mt-3 pt-3 border-t border-slate-100 dark:border-slate-700/50">
                <div className="flex flex-wrap gap-2">
                  {actionsPayload.actions.map((action, index) => {
                    const label = action.label || '操作'
                    if (action.type === 'link' && action.url) {
                      return (
                        <a
                          key={`${label}-${index}`}
                          href={action.url}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors text-legal font-medium"
                        >
                          <span className="text-nano uppercase tracking-wider text-slate-400 dark:text-slate-500">链接</span>
                          <span>{label}</span>
                        </a>
                      )
                    }
                    return (
                      <button
                        key={`${label}-${index}`}
                        type="button"
                        onClick={() => onActionClick?.(action)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-indigo-200 dark:border-indigo-800 bg-indigo-50/50 dark:bg-indigo-950/30 text-indigo-700 dark:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors text-legal font-medium"
                      >
                        <span className="text-nano uppercase tracking-wider text-indigo-400 dark:text-indigo-500">操作</span>
                        <span>{label}</span>
                      </button>
                    )
                  })}
                </div>
              </div>
            )}

            {/* UI 表单 */}
            {formPayload && (
              <form className="mt-3 pt-3 border-t border-slate-100 dark:border-slate-700/50" onSubmit={handleFormSubmit}>
                <div className="space-y-2">
                  {formPayload.fields.map((field) => (
                    <label key={field.id} className="block text-legal font-medium text-slate-600 dark:text-slate-300">
                      <span className="flex items-center gap-1 mb-1">
                        <span>{field.label}</span>
                        {field.required && <span className="text-red-500">*</span>}
                      </span>
                      {field.type === 'select' ? (
                        <select
                          value={normalizedFormValues[field.id] ?? ''}
                          onChange={(event) => handleFormValueChange(field.id, event.target.value)}
                          className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-legal text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-400 dark:focus:ring-indigo-500"
                        >
                          <option value="">请选择</option>
                          {field.options?.map((option) => (
                            <option key={option.value} value={option.value}>
                              {option.label}
                            </option>
                          ))}
                        </select>
                      ) : field.type === 'textarea' ? (
                        <textarea
                          value={normalizedFormValues[field.id] ?? ''}
                          onChange={(event) => handleFormValueChange(field.id, event.target.value)}
                          placeholder={field.placeholder}
                          rows={3}
                          className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-legal text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-400 dark:focus:ring-indigo-500"
                        />
                      ) : (
                        <input
                          type="text"
                          value={normalizedFormValues[field.id] ?? ''}
                          onChange={(event) => handleFormValueChange(field.id, event.target.value)}
                          placeholder={field.placeholder}
                          className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-legal text-slate-700 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-indigo-400 dark:focus:ring-indigo-500"
                        />
                      )}
                    </label>
                  ))}
                </div>
                <div className="mt-3 flex items-center gap-2">
                  <button
                    type="submit"
                    disabled={!isFormValid}
                    className={cn(
                      'px-3 py-1.5 rounded-lg text-legal font-medium transition-colors',
                      isFormValid
                        ? 'bg-indigo-600 text-white hover:bg-indigo-500'
                        : 'bg-slate-200 text-slate-400 dark:bg-slate-800 dark:text-slate-500'
                    )}
                  >
                    {formPayload.submit.label}
                  </button>
                  {!isFormValid && <span className="text-nano text-slate-400">请补全必填项</span>}
                </div>
              </form>
            )}

            {/* Options 选项（兼容旧格式） */}
            {hasOptions && (
              <div className="mt-3 pt-3 border-t border-slate-100 dark:border-slate-700/50">
                <div className="flex flex-wrap gap-2">
                  {message.options!.map((opt, idx) => {
                    const isLink = opt.type === 'link' && (opt.path || opt.value)
                    const label = opt.name || opt.label || '选项'
                    if (isLink) {
                      const href = opt.path || opt.value || ''
                      return (
                        <a
                          key={`${href}-${idx}`}
                          href={href}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors text-legal font-medium"
                        >
                          <span className="text-nano uppercase tracking-wider text-slate-400 dark:text-slate-500">链接</span>
                          <span>{label}</span>
                        </a>
                      )
                    }
                    return (
                      <button
                        key={`${opt.path || opt.value || idx}`}
                        type="button"
                        onClick={() => onOptionClick?.(opt)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-indigo-200 dark:border-indigo-800 bg-indigo-50/50 dark:bg-indigo-950/30 text-indigo-700 dark:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors text-legal font-medium"
                      >
                        <span className="text-nano uppercase tracking-wider text-indigo-400 dark:text-indigo-500">{opt.type}</span>
                        <span>{label}</span>
                      </button>
                    )
                  })}
                </div>
              </div>
            )}
          </div>
          <span className="text-slate-400 px-1 text-micro">{formattedTime}</span>
        </div>
      </div>
    )
  },
  (previous, next) => {
    const prevRows = previous.message.processRows ?? previous.message.agentRows ?? []
    const nextRows = next.message.processRows ?? next.message.agentRows ?? []
    const rowsEqual =
      prevRows.length === nextRows.length &&
      prevRows.every((row, index) => {
        const nextRow = nextRows[index]
        return (
          row.id === nextRow?.id &&
          row.phase === nextRow?.phase &&
          row.status === nextRow?.status &&
          row.title === nextRow?.title &&
          row.detail === nextRow?.detail &&
          row.actor === nextRow?.actor &&
          row.progress === nextRow?.progress &&
          row.timestamp === nextRow?.timestamp
        )
      })

    return (
      previous.message.id === next.message.id &&
      previous.message.content === next.message.content &&
      previous.message.timestamp === next.message.timestamp &&
      rowsEqual &&
      previous.message.isStreaming === next.message.isStreaming &&
      previous.message.options?.length === next.message.options?.length &&
      previous.message.uiPayload === next.message.uiPayload
    )
  }
)
