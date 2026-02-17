import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { StateSnapshot, VirtuosoHandle } from 'react-virtuoso'
import { useWorkflowStudioCacheStore, useWorkflowStudioStore, type AgentActivity } from '@/stores'
import { upsertAgentActivityByAgent } from '@/layouts/workflow/utils'
import { convertAIResponseToGraph } from '@/services/workflowStudioService'
import {
  abortWorkflow,
  createWorkflowStream,
  generateSessionId,
  type SseEvent,
  type WorkflowResponse
} from '@/services/aiWorkflowService'
import { DEFAULT_WORKFLOW_MODEL_ID, WORKFLOW_MODEL_OPTIONS } from '@/config/workflowModels'
import { ChatHeader } from './ChatHeader'
import { ChatMessageList } from './ChatMessage'
import { ChatInput } from '@/components/ui/ChatInput'
import { CHAT_COMMAND_OPTIONS, type ChatCommandId } from './commandLibrary'

const DEFAULT_WORKFLOW_INTRO_MESSAGE =
  '你好！我是 Datapillar 智能助手，可以帮你查询元数据或分析 ETL 需求。请问有什么我可以帮你的？'
const DEFAULT_WORKFLOW_INTRO_RECOMMENDATIONS = ['查询元数据', '同步订单表', '将订单表和产品表关联，写入汇总表']

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

export function ChatPanel() {
  const messages = useWorkflowStudioStore((state) => state.messages)
  const isGenerating = useWorkflowStudioStore((state) => state.isGenerating)
  const isWaitingForResume = useWorkflowStudioStore((state) => state.isWaitingForResume)
  const addMessage = useWorkflowStudioStore((state) => state.addMessage)
  const updateMessage = useWorkflowStudioStore((state) => state.updateMessage)
  const setGenerating = useWorkflowStudioStore((state) => state.setGenerating)
  const setWaitingForResume = useWorkflowStudioStore((state) => state.setWaitingForResume)
  const setWorkflow = useWorkflowStudioStore((state) => state.setWorkflow)
  const setLastPrompt = useWorkflowStudioStore((state) => state.setLastPrompt)
  const resetStudio = useWorkflowStudioStore((state) => state.reset)
  const setInitialized = useWorkflowStudioStore((state) => state.setInitialized)
  const selectedModelId = useWorkflowStudioStore((state) => state.selectedModelId)
  const defaultModelId = useWorkflowStudioStore((state) => state.defaultModelId)
  const setSelectedModelId = useWorkflowStudioStore((state) => state.setSelectedModelId)
  const setDefaultModelId = useWorkflowStudioStore((state) => state.setDefaultModelId)
  const [input, setInput] = useState('')
  const [showHistory, setShowHistory] = useState(false)
  const [forceScrollVersion, setForceScrollVersion] = useState(0)
  const [commandNotice, setCommandNotice] = useState<string | null>(null)
  const [commandNoticeVisible, setCommandNoticeVisible] = useState(false)
  const navigate = useNavigate()
  const commandNoticeHideTimerRef = useRef<number | null>(null)
  const commandNoticeClearTimerRef = useRef<number | null>(null)
  const initialCacheSnapshot = useWorkflowStudioCacheStore.persist?.hasHydrated?.()
    ? useWorkflowStudioCacheStore.getState()
    : null
  const [initialVirtuosoState, setInitialVirtuosoState] = useState<StateSnapshot | null>(
    () => initialCacheSnapshot?.virtuosoState ?? null
  )
  const [virtuosoKey, setVirtuosoKey] = useState(() => (initialCacheSnapshot?.virtuosoState ? 1 : 0))
  const [cacheReady, setCacheReady] = useState(() => useWorkflowStudioCacheStore.persist?.hasHydrated?.() ?? true)
  const historyButtonRef = useRef<HTMLButtonElement | null>(null)
  const historyCardRef = useRef<HTMLDivElement | null>(null)
  const virtuosoRef = useRef<VirtuosoHandle | null>(null)
  const cancelStreamRef = useRef<(() => void) | null>(null)
  const sessionIdRef = useRef<string>(generateSessionId())
  const streamingMessageIdRef = useRef<string | null>(null)
  const hasHydratedFromCacheRef = useRef(false)

  useEffect(() => {
    const handle = scheduleIdle(() => {
      prefetchWorkflowCanvas()
    })
    return () => cancelIdle(handle)
  }, [])

  useEffect(() => {
    if (cacheReady) {
      return
    }
    const unsubscribe = useWorkflowStudioCacheStore.persist?.onFinishHydration?.(() => {
      const cache = useWorkflowStudioCacheStore.getState()
      setCacheReady(true)
      if (cache.virtuosoState) {
        setInitialVirtuosoState(cache.virtuosoState)
        setVirtuosoKey((key) => key + 1)
      }
    })
    return () => {
      if (unsubscribe) {
        unsubscribe()
      }
    }
  }, [cacheReady])

  useLayoutEffect(() => {
    if (!cacheReady || hasHydratedFromCacheRef.current) return
    const cache = useWorkflowStudioCacheStore.getState()
    const hasCacheState =
      cache.messages.length > 0 ||
      cache.workflow.nodes.length > 0 ||
      cache.lastPrompt.length > 0 ||
      cache.selectedModelId !== DEFAULT_WORKFLOW_MODEL_ID ||
      cache.defaultModelId !== DEFAULT_WORKFLOW_MODEL_ID
    if (hasCacheState) {
      const runtime = useWorkflowStudioStore.getState()
      const hasRuntimeState =
        runtime.messages.length > 0 || runtime.workflow.nodes.length > 0 || runtime.lastPrompt.length > 0
      if (!hasRuntimeState) {
        runtime.hydrateFromCache({
          messages: cache.messages,
          workflow: cache.workflow,
          lastPrompt: cache.lastPrompt,
          isInitialized: cache.isInitialized,
          isWaitingForResume: false,
          selectedModelId: cache.selectedModelId,
          defaultModelId: cache.defaultModelId
        })
      }
    }

    hasHydratedFromCacheRef.current = true
  }, [cacheReady])

  const requestForceScroll = useCallback(() => {
    setForceScrollVersion((version) => version + 1)
  }, [])

  const persistSnapshot = useCallback(() => {
    if (!cacheReady) return
    const runtime = useWorkflowStudioStore.getState()
    const commitSnapshot = (virtuosoState: StateSnapshot | null) => {
      useWorkflowStudioCacheStore.getState().setSnapshot({
        messages: runtime.messages,
        workflow: runtime.workflow,
        lastPrompt: runtime.lastPrompt,
        isInitialized: runtime.isInitialized,
        virtuosoState,
        selectedModelId: runtime.selectedModelId,
        defaultModelId: runtime.defaultModelId
      })
    }
    const handle = virtuosoRef.current
    if (handle?.getState) {
      handle.getState((snapshot) => commitSnapshot(snapshot))
      return
    }
    commitSnapshot(null)
  }, [cacheReady])

  useEffect(() => {
    const handlePageHide = () => {
      persistSnapshot()
    }
    window.addEventListener('pagehide', handlePageHide)
    return () => {
      window.removeEventListener('pagehide', handlePageHide)
      persistSnapshot()
    }
  }, [persistSnapshot])

  useEffect(() => {
    if (!showHistory) return
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node
      if (historyButtonRef.current?.contains(target)) return
      if (historyCardRef.current?.contains(target)) return
      setShowHistory(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [showHistory])

  useEffect(() => {
    return () => {
      if (commandNoticeHideTimerRef.current) {
        window.clearTimeout(commandNoticeHideTimerRef.current)
        commandNoticeHideTimerRef.current = null
      }
      if (commandNoticeClearTimerRef.current) {
        window.clearTimeout(commandNoticeClearTimerRef.current)
        commandNoticeClearTimerRef.current = null
      }
    }
  }, [])

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
    if (!cacheReady) return
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
      recommendations: DEFAULT_WORKFLOW_INTRO_RECOMMENDATIONS
    })
    store.setInitialized(true)
  }, [cacheReady])

  const MAX_PROCESS_ROWS = 200

  const historySessions = useMemo(
    () => [
      { group: '今天', items: ['实时数仓构建任务', '用户画像 ETL 修复'] },
      { group: '昨天', items: ['Q3 销售报表分析', 'API 接口压力测试配置'] },
      { group: '过去 7 天', items: ['日志清洗规则 V2'] }
    ],
    []
  )

  const handleSseEvent = useCallback(
    (evt: SseEvent) => {
      const msgId = streamingMessageIdRef.current
      if (!msgId) return

      if (evt.activity) {
        const activity = evt.activity
        const timestamp = evt.ts ?? Date.now()
        const eventName = activity.event_name || activity.event
        const agentKey = activity.agent_en || activity.agent_cn || 'agent'
        const activityId = `${agentKey}:${activity.event}:${eventName}`
        const nextActivity: AgentActivity = {
          ...activity,
          id: activityId,
          timestamp
        }
        updateMessage(msgId, (currentMsg) => ({
          ...currentMsg,
          processRows: upsertAgentActivityByAgent(currentMsg.processRows ?? currentMsg.agentRows, nextActivity, MAX_PROCESS_ROWS)
        }))
      }

      if (evt.workflow && typeof evt.workflow === 'object') {
        const graph = convertAIResponseToGraph(evt.workflow as WorkflowResponse)
        setWorkflow(graph)
        if (evt.status === 'done') {
          sessionIdRef.current = generateSessionId()
        }
      }

      const nextMessage = evt.activity?.summary?.trim()
      const nextRecommendations =
        evt.activity?.recommendations && evt.activity.recommendations.length > 0 ? evt.activity.recommendations : undefined
      const isInterruptEvent = evt.activity?.event === 'interrupt'
      const nextInterrupt = isInterruptEvent ? evt.activity?.interrupt : undefined

      updateMessage(msgId, (currentMsg) => ({
        ...currentMsg,
        content: nextMessage || currentMsg.content,
        streamStatus: evt.status,
        interrupt: isInterruptEvent ? (nextInterrupt ?? { options: [] }) : undefined,
        recommendations: nextRecommendations ?? currentMsg.recommendations
      }))

      if (isInterruptEvent) {
        setGenerating(false)
        setWaitingForResume(true)
        return
      }

      if (evt.status === 'done' || evt.status === 'error' || evt.status === 'aborted') {
        setGenerating(false)
        setWaitingForResume(false)
        streamingMessageIdRef.current = null
      }
    },
    [setGenerating, setWaitingForResume, setWorkflow, updateMessage]
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
        processRows: []
      })
      requestForceScroll()

      if (cancelStreamRef.current) {
        cancelStreamRef.current()
      }

      cancelStreamRef.current = createWorkflowStream(resumeValue ? null : prompt, sessionIdRef.current, {
        onEvent: handleSseEvent
      }, resumeValue)
    },
    [addMessage, handleSseEvent, isGenerating, requestForceScroll, setGenerating, setLastPrompt]
  )

  const sendPrompt = useCallback(
    (prompt: string) => {
      if (isGenerating) return
      const trimmed = prompt.trim()
      if (!trimmed) return
      const shouldResume = isWaitingForResume
      setWaitingForResume(false)
      startStream(trimmed, shouldResume ? trimmed : undefined)
    },
    [isGenerating, isWaitingForResume, setWaitingForResume, startStream]
  )

  const handleSend = () => {
    sendPrompt(input)
  }

  const canSend = input.trim().length > 0

  const handleNewSession = useCallback(() => {
    const preservedModelId = useWorkflowStudioStore.getState().selectedModelId
    if (cancelStreamRef.current) {
      cancelStreamRef.current()
      cancelStreamRef.current = null
    }
    setWaitingForResume(false)
    streamingMessageIdRef.current = null
    sessionIdRef.current = generateSessionId()
    setGenerating(false)
    resetStudio()
    setSelectedModelId(preservedModelId)
    setInitialVirtuosoState(null)
    setVirtuosoKey((key) => key + 1)
    addMessage({
      id: nextMessageId(),
      role: 'assistant',
      content: DEFAULT_WORKFLOW_INTRO_MESSAGE,
      timestamp: Date.now(),
      processRows: [],
      recommendations: DEFAULT_WORKFLOW_INTRO_RECOMMENDATIONS
    })
    setInitialized(true)
    setInput('')
  }, [addMessage, resetStudio, setGenerating, setInitialized, setSelectedModelId, setWaitingForResume])

  const handleManageModels = useCallback(() => {
    navigate('/profile/llm/models')
  }, [navigate])

  const handleSetDefaultModel = useCallback((modelId: string) => {
    setDefaultModelId(modelId)
    setSelectedModelId(modelId)
  }, [setDefaultModelId, setSelectedModelId])

  const showCommandNotice = useCallback((message: string) => {
    const totalDuration = 3000
    const fadeDuration = 200
    const hideDelay = Math.max(0, totalDuration - fadeDuration)
    setCommandNotice(message)
    setCommandNoticeVisible(true)
    if (commandNoticeHideTimerRef.current) {
      window.clearTimeout(commandNoticeHideTimerRef.current)
    }
    if (commandNoticeClearTimerRef.current) {
      window.clearTimeout(commandNoticeClearTimerRef.current)
    }
    commandNoticeHideTimerRef.current = window.setTimeout(() => {
      setCommandNoticeVisible(false)
      commandNoticeHideTimerRef.current = null
    }, hideDelay)
    commandNoticeClearTimerRef.current = window.setTimeout(() => {
      setCommandNotice(null)
      commandNoticeClearTimerRef.current = null
    }, totalDuration)
  }, [])

  const handleCommand = useCallback((commandId: ChatCommandId) => {
    const command = CHAT_COMMAND_OPTIONS.find((item) => item.id === commandId)
    const label = command?.label ?? `/${commandId}`
    if (commandId === 'clear') {
      showCommandNotice(`已触发 ${label}，待后端接入后生效`)
      return
    }
    if (commandId === 'compact') {
      showCommandNotice(`已触发 ${label}，待后端接入后生效`)
    }
  }, [showCommandNotice])

  const isComposingRef = useRef(false)

  const handleCompositionStart = () => {
    isComposingRef.current = true
  }

  const handleCompositionEnd = () => {
    isComposingRef.current = false
  }

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLTextAreaElement>) => {
    if (event.nativeEvent.isComposing || isComposingRef.current) {
      return
    }
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      handleSend()
    }
  }

  // 打断当前 run
  const latestInterrupt = useMemo(() => {
    for (let index = messages.length - 1; index >= 0; index -= 1) {
      const message = messages[index]
      if (message.role !== 'assistant') continue
      const interruptId = message.interrupt?.interrupt_id
      if (interruptId) {
        return { messageId: message.id, interruptId }
      }
    }
    return null
  }, [messages])

  const handleAbort = useCallback(async () => {
    if (!isGenerating && !isWaitingForResume) return

    const isInterruptAbort = Boolean(isWaitingForResume && latestInterrupt?.interruptId)
    try {
      if (isWaitingForResume && !latestInterrupt?.interruptId) {
        return
      }
      if (isInterruptAbort) {
        if (cancelStreamRef.current) {
          cancelStreamRef.current()
          cancelStreamRef.current = null
        }
        const response = await abortWorkflow(sessionIdRef.current, latestInterrupt?.interruptId)
        setGenerating(false)
        setWaitingForResume(false)
        const msgId = latestInterrupt?.messageId
        if (msgId) {
          updateMessage(msgId, {
            content: response.message,
            streamStatus: 'aborted',
            interrupt: undefined
          })
          streamingMessageIdRef.current = null
        }
        return
      }

      // 先关闭 SSE 连接
      if (cancelStreamRef.current) {
        cancelStreamRef.current()
        cancelStreamRef.current = null
      }

      // 调用后端 abort API
      const response = await abortWorkflow(sessionIdRef.current)

      // 更新 UI 状态
      setGenerating(false)
      setWaitingForResume(false)

      // 更新消息状态
      const msgId = streamingMessageIdRef.current
      if (msgId) {
        updateMessage(msgId, {
          content: response.message,
          streamStatus: 'aborted',
          interrupt: undefined
        })
        streamingMessageIdRef.current = null
      }
    } catch (error) {
      console.error('[Chat] Abort 失败:', error)
    }
  }, [
    isGenerating,
    isWaitingForResume,
    latestInterrupt,
    updateMessage,
    setGenerating,
    setWaitingForResume
  ])

  // ESC 键打断
  useEffect(() => {
    const handleEsc = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && (isGenerating || isWaitingForResume)) {
        event.preventDefault()
        handleAbort()
      }
    }

    document.addEventListener('keydown', handleEsc)
    return () => document.removeEventListener('keydown', handleEsc)
  }, [isGenerating, isWaitingForResume, handleAbort])

  const latestAssistantMessageId = useMemo(() => {
    for (let index = messages.length - 1; index >= 0; index -= 1) {
      const message = messages[index]
      if (message.role === 'assistant') {
        return message.id
      }
    }
    return null
  }, [messages])

  // Header 标题：只展示已发送的最新用户消息
  const latestUserMessage = (() => {
    for (let index = messages.length - 1; index >= 0; index -= 1) {
      const message = messages[index]
      if (message.role === 'user') {
        return message.content.trim()
      }
    }
    return ''
  })()

  return (
    <aside className="w-full h-full flex-shrink-0 bg-white/90 dark:bg-slate-900/95 border-r border-slate-200/60 dark:border-slate-800/80 flex flex-col overflow-hidden">
      <div className="relative">
        <ChatHeader
          showHistory={showHistory}
          onToggleHistory={() => setShowHistory((prev) => !prev)}
          historySessions={historySessions}
          historyButtonRef={historyButtonRef}
          historyCardRef={historyCardRef}
          latestUserMessage={latestUserMessage}
          onNewSession={handleNewSession}
        />
        {commandNotice && (
          <div className="pointer-events-none absolute inset-0 z-50 flex items-center justify-center px-16">
            <div
              className={`rounded-lg border border-blue-100/80 dark:border-blue-900/60 bg-blue-50/90 dark:bg-blue-900/30 px-3 py-1.5 text-xs font-medium text-blue-700 dark:text-blue-300 shadow-sm transition-all duration-200 ease-out ${
                commandNoticeVisible ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-1'
              }`}
            >
              {commandNotice}
            </div>
          </div>
        )}
      </div>
      <ChatMessageList
        key={virtuosoKey}
        messages={messages}
        forceScrollVersion={forceScrollVersion}
        autoScrollEnabled={isGenerating}
        latestAssistantMessageId={latestAssistantMessageId}
        restoreStateFrom={initialVirtuosoState}
        virtuosoRef={virtuosoRef}
        onAbort={handleAbort}
        onQuickSend={sendPrompt}
        className="flex-1 mt-2"
      />
      <ChatInput
        input={input}
        onInputChange={setInput}
        onCompositionStart={handleCompositionStart}
        onCompositionEnd={handleCompositionEnd}
        onKeyDown={handleKeyDown}
        onFocus={prefetchWorkflowCanvas}
        onSend={handleSend}
        onAbort={handleAbort}
        isGenerating={isGenerating}
        isWaitingForResume={isWaitingForResume}
        canSend={canSend}
        selectedModelId={selectedModelId}
        defaultModelId={defaultModelId}
        modelOptions={WORKFLOW_MODEL_OPTIONS}
        onModelChange={setSelectedModelId}
        onDefaultModelChange={handleSetDefaultModel}
        onManageModels={handleManageModels}
        commandOptions={CHAT_COMMAND_OPTIONS}
        onCommand={handleCommand}
      />
    </aside>
  )
}
