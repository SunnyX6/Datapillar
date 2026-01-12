import { memo, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type JSX, type CSSProperties } from 'react'
import { Activity, AlertTriangle, ArrowUp, Bot, CheckCircle2, ChevronDown, Loader2, Square, User, Wrench, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useWorkflowStudioStore, type AgentActivity, type ChatMessageOption } from '@/stores'
import { upsertAgentActivityByAgent } from '@/layouts/workflow/utils'
import { convertAIResponseToGraph } from '@/services/workflowStudioService'
import { abortWorkflow, createWorkflowStream, generateSessionId, type SseEvent } from '@/services/aiWorkflowService'

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

type VirtualizedMessageListProps = {
  messages: WorkflowStudioSnapshot['messages']
  renderMessage: (message: WorkflowStudioSnapshot['messages'][number]) => JSX.Element
  scrollRef: React.RefObject<HTMLDivElement | null>
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
      agentRows: [],
      isStreaming: false
    })
    store.setInitialized(true)
  }, [])

  const sendIconSize = 15
  const MAX_AGENT_ROWS = 200

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

    // 创建流式 assistant 消息
    const assistantMsgId = nextMessageId()
    streamingMessageIdRef.current = assistantMsgId
    addMessage({
      id: assistantMsgId,
      role: 'assistant',
      content: '',
      timestamp: Date.now(),
      agentRows: [],
      isStreaming: true
    })

    // 取消之前的流
    if (cancelStreamRef.current) {
      cancelStreamRef.current()
    }

    // 创建消息流
    const shouldResume = waitingResumeRef.current
    waitingResumeRef.current = false

    cancelStreamRef.current = createWorkflowStream(shouldResume ? '' : prompt, sessionIdRef.current, {
      onEvent: (evt: SseEvent) => {
        const msgId = streamingMessageIdRef.current
        if (!msgId) return

	        const timestamp = evt.ts ?? Date.now()
	        const agentName = evt.agent?.name ?? evt.agent?.id ?? ''

	        const pushActivity = (activity: AgentActivity) => {
	          updateMessage(msgId, (currentMsg) => {
	            return {
	              ...currentMsg,
	              agentRows: upsertAgentActivityByAgent(currentMsg.agentRows, activity, MAX_AGENT_ROWS)
	            }
	          })
	        }

        if (evt.event === 'agent.start') {
	          if (!evt.agent) return
	          pushActivity({
	            id: `agent:${agentName || 'unknown'}`,
	            type: 'thought',
	            state: evt.state,
	            level: evt.level,
	            agent: agentName,
            message: evt.state,
            timestamp
          })
          return
        }

        if (evt.event === 'tool.start') {
	          if (!evt.agent) return
	          const toolName = evt.tool?.name || 'unknown'
	          pushActivity({
	            id: `agent:${agentName || 'unknown'}`,
	            type: 'tool',
	            state: evt.state,
	            level: evt.level,
	            agent: agentName,
            message: `${evt.state} ${toolName}`,
            timestamp
          })
          return
        }

        if (evt.event === 'tool.end') {
	          if (!evt.agent) return
	          const toolName = evt.tool?.name || 'unknown'
	          pushActivity({
	            id: `agent:${agentName || 'unknown'}`,
	            type: 'tool',
	            state: evt.state,
	            level: evt.level,
	            agent: agentName,
            message: `${evt.state} ${toolName}`,
            timestamp
          })
          return
        }

        if (evt.event === 'agent.end') {
	          if (!evt.agent) return
	          pushActivity({
	            id: `agent:${agentName || 'unknown'}`,
	            type: 'result',
	            state: evt.state,
	            level: evt.level,
	            agent: agentName,
            message: evt.message?.content || evt.state,
            timestamp
          })
          return
        }

        // interrupt: 更新内容，停止流式，options 存到消息中
        if (evt.event === 'interrupt') {
          setGenerating(false)
          const questions = evt.interrupt?.questions?.join('\n') || ''
          const interruptMessage = evt.interrupt?.message || evt.message?.content || '需要你补充信息才能继续'
          const rawOptions = evt.interrupt?.options ?? []
          // 转换 options 格式（兼容新旧格式）
          const normalizedOptions: ChatMessageOption[] = rawOptions.map((opt) => ({
            type: opt.type ?? 'option',
            name: opt.name ?? opt.label ?? opt.value ?? '',
            path: opt.path ?? opt.value ?? '',
            description: opt.description,
            tools: opt.tools,
            extra: opt.extra,
            value: opt.value,
            label: opt.label
          }))
          updateMessage(msgId, {
            content: `${interruptMessage}${questions ? '\n' + questions : ''}`.trim(),
            isStreaming: false,
            options: normalizedOptions
          })
          streamingMessageIdRef.current = null
          waitingResumeRef.current = true
          return
        }

        // result: 更新内容，添加最终状态，停止流式
        if (evt.event === 'result') {
          setGenerating(false)
          waitingResumeRef.current = false
          const deliverable = evt.result?.deliverable
          const deliverableType = evt.result?.deliverable_type

          // 根据 deliverable_type 决定如何渲染
          if (deliverableType === 'workflow' || deliverableType === 'plan') {
            // 工作流类型：渲染到画布
            const graph = convertAIResponseToGraph(deliverable)
            setWorkflow(graph)
            updateMessage(msgId, {
              content: `${evt.message?.content || '生成完成'}：${graph.name}，共 ${graph.stats.nodes} 个节点、${graph.stats.edges} 条连线。`,
              isStreaming: false
            })
            sessionIdRef.current = generateSessionId()
          } else {
            // 其他类型（chat_response 等）：直接显示文本
            updateMessage(msgId, {
              content: evt.message?.content || '完成',
              isStreaming: false
            })
          }
          streamingMessageIdRef.current = null
          return
        }

        // error: 更新内容，添加错误状态，停止流式
        if (evt.event === 'error') {
          setGenerating(false)
          waitingResumeRef.current = false
          const errorText = evt.error?.detail ? `${evt.error.message}：${evt.error.detail}` : evt.error?.message || '未知错误'
          updateMessage(msgId, {
            content: errorText,
            isStreaming: false
          })
          streamingMessageIdRef.current = null
          return
        }

        // aborted: 用户主动打断
        if (evt.event === 'aborted') {
          setGenerating(false)
          waitingResumeRef.current = false
          updateMessage(msgId, {
            content: evt.message?.content || '已停止',
            isStreaming: false
          })
          streamingMessageIdRef.current = null
          return
        }

        // 兜底：未知消息类型，记录警告
        console.warn('[Chat] 未知 SSE 事件:', evt)
      }
    }, shouldResume ? prompt : undefined)
  }

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
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
    onOptionClick
  }: {
    message: WorkflowStudioSnapshot['messages'][number]
    onOptionClick?: (option: ChatMessageOption) => void
  }) {
    const isUser = message.role === 'user'
    const formattedTime = useMemo(() => new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), [message.timestamp])
	    const avatarIconSize = 16
	    const hasAgentRows = !isUser && message.agentRows && message.agentRows.length > 0
	    const isStreaming = message.isStreaming === true
	    const hasOptions = !isUser && message.options && message.options.length > 0
	    const [isExpanded, setIsExpanded] = useState(false)
	    const latestAgentRow = useMemo(() => {
	      const rows = message.agentRows
	      if (!rows || rows.length === 0) return null
	      return rows.reduce((latest, row) => (row.timestamp > latest.timestamp ? row : latest), rows[0])
	    }, [message.agentRows])

    const getRowBadgeClass = useCallback((row: AgentActivity) => {
      if (row.level === 'success') return 'bg-emerald-50 border-emerald-100 text-emerald-600 dark:bg-emerald-950/40 dark:border-emerald-900/50 dark:text-emerald-400'
      if (row.level === 'warning') return 'bg-amber-50 border-amber-100 text-amber-600 dark:bg-amber-950/30 dark:border-amber-900/50 dark:text-amber-400'
      if (row.level === 'error') return 'bg-red-50 border-red-100 text-red-600 dark:bg-red-950/30 dark:border-red-900/50 dark:text-red-400'
      if (row.type === 'tool') return 'bg-indigo-50 border-indigo-100 text-indigo-600 dark:bg-indigo-950/30 dark:border-indigo-900/50 dark:text-indigo-400'
      return 'bg-slate-50 border-slate-100 text-slate-500 dark:bg-slate-800/40 dark:border-slate-700/60 dark:text-slate-300'
    }, [])

    const RowIcon = useCallback(({ row }: { row: AgentActivity }) => {
      if (row.type === 'error' || row.level === 'error') return <XCircle size={10} strokeWidth={2.75} />
      if (row.state === 'waiting' || row.level === 'warning') return <AlertTriangle size={10} strokeWidth={2.75} />
      if (row.type === 'tool') return <Wrench size={10} strokeWidth={2.75} />
      if (row.type === 'result' || row.state === 'done') return <CheckCircle2 size={10} strokeWidth={2.75} />
      return <Activity size={10} strokeWidth={2.75} />
    }, [])

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
            {/* 流式状态：直接渲染 agentRows */}
            {isStreaming && hasAgentRows && (
              <div className="space-y-2 mb-3 pb-3 border-b border-slate-100 dark:border-slate-700/50">
	                <div className="px-1 flex items-center gap-2">
	                  <Loader2 size={10} className="animate-spin text-indigo-500" />
	                  <span className="text-nano font-black text-indigo-500 uppercase tracking-widest">智能体动态</span>
	                </div>
	                {message.agentRows!.map((row) => (
	                  <div key={row.id} className="flex items-center gap-3">
                    <div className={cn('w-5 h-5 rounded border flex items-center justify-center shrink-0', getRowBadgeClass(row))}>
                      <RowIcon row={row} />
                    </div>
		                    <span className="text-micro font-black text-slate-900 dark:text-slate-100 uppercase tracking-tighter shrink-0 w-24">
		                      {row.agent}
		                    </span>
		                    <span className="text-legal font-medium text-slate-400 truncate flex-1">
		                      {row.message}
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
	                        {latestAgentRow?.state ?? 'thinking'}
	                      </span>
			                </div>
			              </div>
			            )}

            {/* 流式状态但没有 agentRows：显示加载中 */}
            {isStreaming && !hasAgentRows && (
              <div className="flex items-center gap-3 py-2">
                <div className="flex space-x-1">
                  <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                  <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '200ms' }} />
                  <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-bounce" style={{ animationDelay: '400ms' }} />
                </div>
		                <span className="text-micro font-bold text-indigo-500 tracking-tight italic">thinking</span>
		              </div>
		            )}

            {/* 非流式状态且有 agentRows：可折叠 */}
            {!isStreaming && hasAgentRows && (
              <div className="mb-3 pb-3 border-b border-slate-100 dark:border-slate-700/50">
                <button
                  type="button"
                  onClick={() => setIsExpanded(!isExpanded)}
                  className="w-full flex items-center justify-between py-1 hover:opacity-80 transition-opacity"
                >
	                  <div className="flex items-center gap-2">
	                    <span className="text-nano font-bold text-slate-400 uppercase tracking-widest">过程记录</span>
	                    <span className="text-nano text-slate-300">({message.agentRows!.length})</span>
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
                    {message.agentRows!.map((row) => (
                      <div key={row.id} className="flex items-center gap-3">
                        <div
                          className={cn('w-5 h-5 rounded border flex items-center justify-center shrink-0', getRowBadgeClass(row))}
                        >
                          <RowIcon row={row} />
                        </div>
		                        <span className="text-micro font-black text-slate-900 dark:text-slate-100 uppercase tracking-tighter shrink-0 w-24">
		                          {row.agent}
		                        </span>
	                        <span className="text-legal font-medium text-slate-400 truncate flex-1">
	                          {row.message}
	                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 消息内容 */}
            {message.content}

            {/* Options 选项（卡片内部整齐排列） */}
            {hasOptions && (
              <div className="mt-3 pt-3 border-t border-slate-100 dark:border-slate-700/50">
                <div className="flex flex-wrap gap-2">
                  {message.options!.map((opt, idx) => (
                    <button
                      key={`${opt.path || opt.value || idx}`}
                      type="button"
                      onClick={() => onOptionClick?.(opt)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-indigo-200 dark:border-indigo-800 bg-indigo-50/50 dark:bg-indigo-950/30 text-indigo-700 dark:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors text-legal font-medium"
                    >
                      <span className="text-nano uppercase tracking-wider text-indigo-400 dark:text-indigo-500">{opt.type}</span>
                      <span>{opt.name || opt.label}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
          <span className="text-slate-400 px-1 text-micro">{formattedTime}</span>
        </div>
      </div>
    )
  },
  (previous, next) =>
    previous.message.id === next.message.id &&
    previous.message.content === next.message.content &&
    previous.message.timestamp === next.message.timestamp &&
    previous.message.agentRows?.length === next.message.agentRows?.length &&
    previous.message.agentRows?.every((r, i) =>
      r.id === next.message.agentRows?.[i]?.id &&
      r.type === next.message.agentRows?.[i]?.type &&
      r.state === next.message.agentRows?.[i]?.state &&
      r.level === next.message.agentRows?.[i]?.level &&
      r.agent === next.message.agentRows?.[i]?.agent &&
      r.message === next.message.agentRows?.[i]?.message
    ) &&
    previous.message.isStreaming === next.message.isStreaming &&
    previous.message.options?.length === next.message.options?.length
)
