import { useEffect, useRef, useState } from 'react'
import {
  ArrowRight,
  Bot,
  ChevronDown,
  ChevronRight,
  Globe,
  Key,
  Play,
  Send,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  User,
  X
} from 'lucide-react'
import { toast } from 'sonner'
import { cardWidthClassMap, drawerWidthClassMap, menuWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { createLlmPlaygroundStream } from '@/services/aiLlmPlaygroundService'
import { resolveProviderLabel } from '../utils/modelAdapters'
import type { ModelRecord } from '../utils/types'

const getProviderLabel = (model: ModelRecord) => resolveProviderLabel(model.provider, model.providerLabel)

type ChatMessage = {
  id: string
  role: 'assistant' | 'user'
  content: string
  reasoning?: string
  thinkingEnabled?: boolean
}

type PendingIndicatorPhase = 'hidden' | 'entering' | 'visible' | 'leaving'

function buildWelcomeMessage(model: ModelRecord): ChatMessage {
  return {
    id: 'welcome',
    role: 'assistant',
    content: `你好！我是 ${model.name}，你可以在这里测试我的能力。`
  }
}

export function LLMTest({
  model,
  isConnected,
  defaultTab,
  onClose,
  onConnect
}: {
  model: ModelRecord
  isConnected: boolean
  defaultTab: 'config' | 'playground'
  onClose: () => void
  onConnect: (model: ModelRecord, request: { apiKey: string; baseUrl?: string }) => Promise<boolean>
}) {
  const [activeTab, setActiveTab] = useState<'config' | 'playground'>(isConnected ? defaultTab : 'config')
  const [isEditing, setIsEditing] = useState(false)
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState(model.baseUrl ?? '')
  const [isConnecting, setIsConnecting] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([buildWelcomeMessage(model)])
  const [collapsedReasoningMessageIds, setCollapsedReasoningMessageIds] = useState<string[]>([])
  const [draft, setDraft] = useState('')
  const [temperature, setTemperature] = useState(0.7)
  const [topP, setTopP] = useState(0.9)
  const [thinkingEnabled, setThinkingEnabled] = useState(false)
  const [systemInstruction, setSystemInstruction] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [streamingAssistantMessageId, setStreamingAssistantMessageId] = useState<string | null>(null)
  const [pendingAssistantMessageId, setPendingAssistantMessageId] = useState<string | null>(null)
  const [pendingIndicatorPhase, setPendingIndicatorPhase] = useState<PendingIndicatorPhase>('hidden')
  const streamCancelRef = useRef<(() => void) | null>(null)
  const messageEndRef = useRef<HTMLDivElement | null>(null)
  const pendingAssistantMessageIdRef = useRef<string | null>(null)
  const pendingIndicatorPhaseRef = useRef<PendingIndicatorPhase>('hidden')
  const pendingShownAtRef = useRef(0)
  const pendingEnterTimerRef = useRef<number | null>(null)
  const pendingMinDurationTimerRef = useRef<number | null>(null)
  const pendingLeaveTimerRef = useRef<number | null>(null)

  const updatePendingIndicatorPhase = (phase: PendingIndicatorPhase) => {
    pendingIndicatorPhaseRef.current = phase
    setPendingIndicatorPhase(phase)
  }

  const clearPendingIndicatorTimers = () => {
    if (pendingEnterTimerRef.current) {
      window.clearTimeout(pendingEnterTimerRef.current)
      pendingEnterTimerRef.current = null
    }
    if (pendingMinDurationTimerRef.current) {
      window.clearTimeout(pendingMinDurationTimerRef.current)
      pendingMinDurationTimerRef.current = null
    }
    if (pendingLeaveTimerRef.current) {
      window.clearTimeout(pendingLeaveTimerRef.current)
      pendingLeaveTimerRef.current = null
    }
  }

  const resetPendingIndicator = () => {
    clearPendingIndicatorTimers()
    pendingShownAtRef.current = 0
    pendingAssistantMessageIdRef.current = null
    setPendingAssistantMessageId(null)
    updatePendingIndicatorPhase('hidden')
  }

  const startPendingIndicator = (assistantMessageId: string) => {
    clearPendingIndicatorTimers()
    pendingShownAtRef.current = Date.now()
    pendingAssistantMessageIdRef.current = assistantMessageId
    setPendingAssistantMessageId(assistantMessageId)
    updatePendingIndicatorPhase('entering')
    pendingEnterTimerRef.current = window.setTimeout(() => {
      if (pendingAssistantMessageIdRef.current !== assistantMessageId) {
        return
      }
      updatePendingIndicatorPhase('visible')
      pendingEnterTimerRef.current = null
    }, 120)
  }

  const hidePendingIndicator = (assistantMessageId: string, force = false) => {
    if (pendingAssistantMessageIdRef.current !== assistantMessageId) {
      return
    }
    if (pendingIndicatorPhaseRef.current === 'hidden' || pendingIndicatorPhaseRef.current === 'leaving') {
      return
    }

    const leave = () => {
      if (pendingAssistantMessageIdRef.current !== assistantMessageId) {
        return
      }
      updatePendingIndicatorPhase('leaving')
      pendingLeaveTimerRef.current = window.setTimeout(() => {
        if (pendingAssistantMessageIdRef.current !== assistantMessageId) {
          return
        }
        pendingAssistantMessageIdRef.current = null
        setPendingAssistantMessageId(null)
        updatePendingIndicatorPhase('hidden')
        pendingShownAtRef.current = 0
        pendingLeaveTimerRef.current = null
      }, 100)
    }

    clearPendingIndicatorTimers()
    if (force) {
      leave()
      return
    }

    const elapsed = Date.now() - pendingShownAtRef.current
    const remaining = Math.max(0, 250 - elapsed)
    if (remaining <= 0) {
      leave()
      return
    }
    pendingMinDurationTimerRef.current = window.setTimeout(() => {
      leave()
      pendingMinDurationTimerRef.current = null
    }, remaining)
  }

  const cancelStreaming = () => {
    streamCancelRef.current?.()
    streamCancelRef.current = null
    resetPendingIndicator()
    setIsStreaming(false)
    setStreamingAssistantMessageId(null)
  }

  useEffect(() => {
    return () => {
      streamCancelRef.current?.()
      streamCancelRef.current = null
      clearPendingIndicatorTimers()
    }
  }, [])

  useEffect(() => {
    if (activeTab !== 'playground') {
      return
    }
    let rafId = 0
    rafId = window.requestAnimationFrame(() => {
      messageEndRef.current?.scrollIntoView({ block: 'end', behavior: 'auto' })
    })
    return () => {
      if (rafId) {
        window.cancelAnimationFrame(rafId)
      }
    }
  }, [activeTab, messages])

  const handleSend = () => {
    const trimmed = draft.trim()
    if (!trimmed || isStreaming) return
    cancelStreaming()

    const userMessageId = `user-${Date.now()}`
    const assistantMessageId = `assistant-${Date.now()}`
    const assistantThinkingEnabled = thinkingEnabled
    setMessages((prev) => [
      ...prev,
      {
        id: userMessageId,
        role: 'user',
        content: trimmed
      },
      {
        id: assistantMessageId,
        role: 'assistant',
        content: '',
        reasoning: assistantThinkingEnabled ? '' : undefined,
        thinkingEnabled: assistantThinkingEnabled
      }
    ])
    setDraft('')

    setIsStreaming(true)
    setStreamingAssistantMessageId(assistantMessageId)
    startPendingIndicator(assistantMessageId)
    streamCancelRef.current = createLlmPlaygroundStream(
      {
        aiModelId: model.aiModelId,
        message: trimmed,
        modelConfig: {
          temperature,
          topP,
          thinkingEnabled: assistantThinkingEnabled,
          systemInstruction: systemInstruction.trim()
        }
      },
      {
        onDelta: (delta) => {
          hidePendingIndicator(assistantMessageId)
          setMessages((prev) =>
            prev.map((message) =>
              message.id === assistantMessageId
                ? { ...message, content: `${message.content}${delta}` }
                : message
            )
          )
        },
        onReasoningDelta: (delta) => {
          if (!assistantThinkingEnabled) {
            return
          }
          hidePendingIndicator(assistantMessageId)
          setMessages((prev) =>
            prev.map((message) =>
              message.id === assistantMessageId
                ? { ...message, reasoning: `${message.reasoning ?? ''}${delta}` }
                : message
            )
          )
        },
        onDone: (event) => {
          hidePendingIndicator(assistantMessageId)
          setMessages((prev) =>
            prev.map((item) => {
              if (item.id !== assistantMessageId) {
                return item
              }

              const nextContent = item.content.trim()
                ? item.content
                : (event.content.trim() ? event.content : item.content)
              const nextReasoning = assistantThinkingEnabled
                ? (item.reasoning?.trim()
                  ? item.reasoning
                  : (event.reasoning?.trim() ? event.reasoning : item.reasoning))
                : item.reasoning

              if (nextContent === item.content && nextReasoning === item.reasoning) {
                return item
              }

              return {
                ...item,
                content: nextContent,
                reasoning: nextReasoning
              }
            })
          )
          streamCancelRef.current = null
          setIsStreaming(false)
          setStreamingAssistantMessageId(null)
        },
        onError: (message) => {
          hidePendingIndicator(assistantMessageId, true)
          setMessages((prev) =>
            prev.map((item) =>
              item.id === assistantMessageId && !item.content.trim()
                ? { ...item, content: '模型暂时无响应，请稍后重试。' }
                : item
            )
          )
          toast.error(`Playground 调用失败：${message}`)
          streamCancelRef.current = null
          setIsStreaming(false)
          setStreamingAssistantMessageId(null)
        }
      }
    )
  }

  const handleReset = () => {
    cancelStreaming()
    setMessages([buildWelcomeMessage(model)])
    setCollapsedReasoningMessageIds([])
    setDraft('')
  }

  const toggleReasoningCollapse = (messageId: string) => {
    setCollapsedReasoningMessageIds((prev) => (
      prev.includes(messageId)
        ? prev.filter((id) => id !== messageId)
        : [...prev, messageId]
    ))
  }

  const handleConnect = async () => {
    if (!apiKey.trim() || isConnecting) return
    setIsConnecting(true)
    const connected = await onConnect(model, {
      apiKey: apiKey.trim(),
      baseUrl: baseUrl.trim() || undefined
    })
    setIsConnecting(false)
    if (!connected) {
      return
    }
    setApiKey('')
    setIsEditing(false)
    setActiveTab('playground')
  }

  return (
    <aside className={`fixed right-0 top-14 bottom-0 z-40 ${drawerWidthClassMap.wide} bg-white dark:bg-slate-900 border-l border-slate-200 dark:border-slate-800 shadow-2xl flex flex-col animate-in slide-in-from-right duration-500`}>
      <div className="h-14 px-5 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between bg-white dark:bg-slate-900 flex-shrink-0">
        <div className="flex items-center gap-3 min-w-0">
          <div className="size-9 rounded-xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-300 flex items-center justify-center flex-shrink-0">
            <Bot size={18} />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-body-sm font-semibold text-slate-900 dark:text-slate-100 truncate">{model.name}</span>
              <span
                className={`px-2 py-0.5 text-micro font-semibold rounded-full border ${
                  isConnected
                    ? 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20'
                    : 'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-500/10 dark:text-amber-200 dark:border-amber-500/20'
                }`}
              >
                {isConnected ? 'READY' : 'SETUP REQUIRED'}
              </span>
            </div>
            <div className="text-micro text-slate-400 font-mono truncate">{model.providerModelId}</div>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex bg-slate-100 dark:bg-slate-800 p-1 rounded-lg border border-slate-200 dark:border-slate-700">
            <button
              type="button"
              onClick={() => setActiveTab('config')}
              className={`px-3 py-1.5 text-micro font-semibold rounded-md transition-all flex items-center ${
                activeTab === 'config'
                  ? 'bg-white dark:bg-slate-700 text-slate-900 dark:text-white shadow-sm'
                  : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
              }`}
            >
              <Key size={12} className="mr-1.5" />
              Connect
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('playground')}
              disabled={!isConnected}
              className={`px-3 py-1.5 text-micro font-semibold rounded-md transition-all flex items-center ${
                activeTab === 'playground'
                  ? 'bg-white dark:bg-slate-700 text-slate-900 dark:text-white shadow-sm'
                  : 'text-slate-400'
              } ${!isConnected ? 'cursor-not-allowed' : 'hover:text-slate-700 dark:hover:text-slate-200'}`}
            >
              <Play size={12} className="mr-1.5 fill-current" />
              Playground
            </button>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors"
            aria-label="关闭模型对话"
          >
            <X size={18} />
          </button>
        </div>
      </div>

      {activeTab === 'config' ? (
        <div className="flex-1 min-h-0 overflow-y-auto bg-white dark:bg-slate-900 flex items-center justify-center p-6 @md:p-8">
          <div className={`w-full ${cardWidthClassMap.half} @md:${cardWidthClassMap.medium}`}>
            <div className="text-center mb-8">
              <div className="w-14 h-14 mx-auto rounded-2xl flex items-center justify-center mb-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 shadow-sm">
                <Key size={22} className="text-slate-400" />
              </div>
              <h2 className="text-subtitle @md:text-title font-black text-slate-900 dark:text-white">
                {isConnected ? `${getProviderLabel(model)} 已连接` : `Connect to ${getProviderLabel(model)}`}
              </h2>
              <p className="text-caption @md:text-body-sm text-slate-500 dark:text-slate-400 mt-2.5 max-w-sm mx-auto">
                {isConnected
                  ? 'API Key 已连接，可直接进入 Playground 测试模型。'
                  : `输入 API Key 以连接 ${getProviderLabel(model)} 并启用 ${model.name}。`}
              </p>
            </div>

            <div className={`bg-white dark:bg-slate-900 p-3.5 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm space-y-2.5 ${cardWidthClassMap.narrow} mx-auto`}>
              {isConnected && !isEditing ? (
                <div className="space-y-2.5">
                  <div className="p-4 bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-100 dark:border-emerald-500/20 rounded-xl flex items-start">
                    <ShieldCheck size={18} className="text-emerald-600 dark:text-emerald-300 mt-0.5 mr-3 flex-shrink-0" />
                    <div>
                      <h4 className="text-body-sm font-semibold text-emerald-800 dark:text-emerald-200">连接已生效</h4>
                      <p className="text-caption text-emerald-600/90 dark:text-emerald-200/80 mt-1">
                        凭证已安全保存，可直接进入 Playground。
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => setActiveTab('playground')}
                      className="flex-1 h-9 bg-slate-900 hover:bg-slate-800 dark:bg-indigo-600 dark:hover:bg-indigo-500 text-white text-caption font-semibold rounded-xl transition-all shadow-md flex items-center justify-center"
                    >
                      <Play size={14} className="mr-2 fill-current" />
                      Playground
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setBaseUrl(model.baseUrl ?? '')
                        setApiKey('')
                        setIsEditing(true)
                      }}
                      className="h-9 w-9 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-500 hover:text-slate-700 dark:hover:text-slate-200 hover:border-indigo-300 transition-colors flex items-center justify-center"
                      aria-label="修改配置"
                    >
                      <Settings2 size={16} />
                    </button>
                  </div>
                </div>
              ) : (
                <div className="space-y-2.5">
                  <div>
                    <label className={`block ${TYPOGRAPHY.micro} font-bold text-slate-500 uppercase tracking-wide mb-1`}>
                      API Key <span className="text-rose-500">*</span>
                    </label>
                    <div className="relative group">
                      <Key size={12} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" />
                      <input
                        type="password"
                        value={apiKey}
                        onChange={(event) => setApiKey(event.target.value)}
                        placeholder={model.maskedApiKey
                          ? `${model.maskedApiKey}（输入新 Key 覆盖）`
                          : `sk-... (${getProviderLabel(model)} Key)`}
                        className="w-full h-9 pl-8 pr-3 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-caption text-slate-700 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-500 transition-all font-mono"
                        autoFocus
                      />
                    </div>
                  </div>

                  <div>
                    <label className={`block ${TYPOGRAPHY.micro} font-bold text-slate-500 uppercase tracking-wide mb-1`}>
                      Base URL <span className="text-slate-400 font-normal normal-case ml-1">(optional)</span>
                    </label>
                    <div className="relative group">
                      <Globe size={12} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" />
                      <input
                        type="text"
                        value={baseUrl}
                        onChange={(event) => setBaseUrl(event.target.value)}
                        placeholder="https://api.example.com/v1"
                        className="w-full h-9 pl-8 pr-3 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-caption text-slate-700 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-500 transition-all font-mono"
                      />
                    </div>
                  </div>

                  <div className="pt-0.5">
                    <button
                      type="button"
                      onClick={() => {
                        void handleConnect()
                      }}
                      disabled={!apiKey.trim() || isConnecting}
                      className="w-full h-9 bg-indigo-400 hover:bg-indigo-500 text-white text-caption font-semibold rounded-xl transition-all shadow-sm flex items-center justify-center disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {isConnecting ? 'Connecting...' : 'Save & Connect'}
                      <ArrowRight size={16} className="ml-2" />
                    </button>
                    <p className={`${TYPOGRAPHY.nano} text-center text-slate-400 mt-2 flex items-center justify-center`}>
                      <ShieldCheck size={10} className="mr-1" /> Keys are encrypted at rest.
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      ) : (
        <div className="flex-1 min-h-0 flex">
          <div className="flex-1 min-w-0 flex flex-col">
            <div className="flex-1 min-h-0 overflow-y-auto p-5 space-y-4">
              {messages.map((message) => {
                if (message.role === 'assistant') {
                  const allowReasoningUi = message.thinkingEnabled === true
                  const reasoningContent = (message.reasoning ?? '').trim()
                  const hasReasoning = reasoningContent.length > 0
                  const isReasoningCollapsed = hasReasoning && collapsedReasoningMessageIds.includes(message.id)
                  const isCurrentStreamingAssistant = isStreaming && message.id === streamingAssistantMessageId
                  const isReasoningStreaming = allowReasoningUi && isCurrentStreamingAssistant
                  const shouldShowReasoningBlock = allowReasoningUi && (hasReasoning || isReasoningStreaming)
                  const isPendingIndicatorVisible = pendingAssistantMessageId === message.id && pendingIndicatorPhase !== 'hidden'
                  const hasContent = Boolean(message.content)
                  const shouldHoldContentForPending = isPendingIndicatorVisible && !shouldShowReasoningBlock && hasContent
                  const shouldShowContent = hasContent && !shouldHoldContentForPending
                  const shouldShowPendingBlock = isPendingIndicatorVisible && !shouldShowReasoningBlock && !shouldShowContent
                  const pendingContainerAnimationClass = pendingIndicatorPhase === 'entering'
                    ? 'animate-llm-pending-enter'
                    : (pendingIndicatorPhase === 'leaving' ? 'animate-llm-pending-leave' : '')

                  return (
                    <div key={message.id} className="flex justify-start">
                      <div className="mr-3 mt-1 size-7 rounded-full bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-300 flex items-center justify-center flex-shrink-0">
                        <Bot size={14} />
                      </div>
                      <div className="max-w-[70%] flex flex-col">
                        {shouldShowReasoningBlock && (
                          <div className={`rounded-xl border border-slate-200/80 dark:border-slate-600/70 bg-slate-50/80 dark:bg-slate-900/60 px-3 py-2 ${
                            shouldShowContent ? 'rounded-b-none' : ''
                          }`}>
                            {hasReasoning ? (
                              <button
                                type="button"
                                onClick={() => toggleReasoningCollapse(message.id)}
                                className="w-full flex items-center justify-between gap-2 text-left text-micro font-semibold text-slate-500 dark:text-slate-300"
                              >
                                <span className="uppercase tracking-wide">思考过程</span>
                                {isReasoningCollapsed ? <ChevronRight size={12} /> : <ChevronDown size={12} />}
                              </button>
                            ) : (
                              <div className="text-micro font-semibold text-slate-500 dark:text-slate-300 uppercase tracking-wide">思考过程</div>
                            )}
                            {hasReasoning ? (
                              !isReasoningCollapsed && (
                                <div className="mt-2 pt-2 border-t border-slate-200/70 dark:border-slate-700 whitespace-pre-wrap text-caption text-slate-600 dark:text-slate-300 leading-relaxed">
                                  {reasoningContent}
                                </div>
                              )
                            ) : (
                              <div className="mt-2 pt-2 border-t border-slate-200/70 dark:border-slate-700 text-caption text-slate-500 dark:text-slate-400">
                                思考中...
                              </div>
                            )}
                          </div>
                        )}
                        {shouldShowContent && (
                          <div className={`rounded-2xl px-4 py-3 text-body-sm shadow-sm bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 text-slate-700 dark:text-slate-200 ${
                            shouldShowReasoningBlock ? 'rounded-t-none border-t-0' : ''
                          }`}>
                            {message.content}
                          </div>
                        )}
                        {shouldShowPendingBlock && (
                          <div
                            role="status"
                            aria-live="polite"
                            className={`rounded-2xl px-4 py-3 text-body-sm shadow-sm bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 ${pendingContainerAnimationClass}`}
                          >
                            <div className="flex items-center gap-3">
                              <div className="flex space-x-1">
                                <div
                                  className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-llm-pending-dot"
                                  style={{ animationDelay: '0ms' }}
                                />
                                <div
                                  className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-llm-pending-dot"
                                  style={{ animationDelay: '150ms' }}
                                />
                                <div
                                  className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-llm-pending-dot"
                                  style={{ animationDelay: '300ms' }}
                                />
                              </div>
                              <span className="text-micro font-semibold text-indigo-500 tracking-tight">处理中...</span>
                              <span className="sr-only">AI 正在生成响应</span>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  )
                }

                return (
                  <div key={message.id} className="flex justify-end">
                    <div className="max-w-[70%] rounded-2xl px-4 py-3 text-body-sm shadow-sm bg-indigo-600 text-white">
                      {message.content}
                    </div>
                    <div className="ml-3 mt-1 size-7 rounded-full bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-200 flex items-center justify-center flex-shrink-0">
                      <User size={14} />
                    </div>
                  </div>
                )
              })}
              <div ref={messageEndRef} />
            </div>

            <div className="border-t border-slate-200 dark:border-slate-800 p-4">
              <div className="group flex items-center gap-3 bg-white/80 dark:bg-slate-900/80 border border-slate-200 dark:border-slate-700 rounded-xl px-3 py-2 transition-all shadow-sm focus-within:ring-2 focus-within:ring-indigo-500/20 focus-within:border-indigo-500/40 focus-within:shadow-md">
                <input
                  type="text"
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.nativeEvent.isComposing) {
                      return
                    }
                    if (event.key === 'Enter' && !isStreaming) {
                      event.preventDefault()
                      handleSend()
                    }
                  }}
                  placeholder={`Message ${model.name}...`}
                  className="flex-1 bg-transparent outline-none text-body-sm text-slate-700 dark:text-slate-200 placeholder:text-slate-400 group-focus-within:placeholder:text-slate-500 transition-colors"
                  disabled={isStreaming}
                />
                <button
                  type="button"
                  onClick={handleSend}
                  className={`size-8 rounded-lg flex items-center justify-center transition-all active:scale-95 ${
                    draft.trim() && !isStreaming
                      ? 'bg-indigo-600 text-white hover:bg-indigo-500 shadow-md shadow-indigo-500/20'
                      : 'bg-slate-200 text-slate-400 cursor-not-allowed dark:bg-slate-700'
                  }`}
                  disabled={!draft.trim() || isStreaming}
                  aria-label="发送消息"
                >
                  <Send size={14} />
                </button>
              </div>
              <p className={`${TYPOGRAPHY.micro} text-slate-400 text-center mt-2`}>AI 输出可能不准确，请谨慎判断。</p>
            </div>
          </div>

          <div className={`${menuWidthClassMap.extraWide} flex-shrink-0 border-l border-slate-200 dark:border-slate-800 p-5 bg-slate-50/60 dark:bg-slate-900/60`}>
            <div className="flex items-center gap-2 text-micro font-bold text-slate-500 uppercase tracking-wider">
              <span className="size-6 rounded-lg bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 flex items-center justify-center text-indigo-500">
                <SlidersHorizontal size={12} />
              </span>
              MODEL CONFIG
            </div>

            <div className="mt-5 space-y-5">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-caption font-semibold text-slate-600 dark:text-slate-300">
                    System Instruction
                  </label>
                  <span className="text-micro font-mono text-slate-500">{systemInstruction.length}/2000</span>
                </div>
                <textarea
                  value={systemInstruction}
                  onChange={(event) => setSystemInstruction(event.target.value.slice(0, 2000))}
                  placeholder="可选：输入系统级指令（例如回复风格、约束）"
                  rows={4}
                  className="w-full resize-y min-h-[84px] rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-caption text-slate-700 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-500 transition-all"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-caption font-semibold text-slate-600 dark:text-slate-300">Temperature</label>
                  <span className="text-micro font-mono text-slate-500">{temperature.toFixed(1)}</span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.1"
                  value={temperature}
                  onChange={(event) => setTemperature(parseFloat(event.target.value))}
                  className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg cursor-pointer accent-indigo-500 focus:outline-none"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-caption font-semibold text-slate-600 dark:text-slate-300">Top P</label>
                  <span className="text-micro font-mono text-slate-500">{topP.toFixed(2)}</span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.05"
                  value={topP}
                  onChange={(event) => setTopP(parseFloat(event.target.value))}
                  className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg cursor-pointer accent-indigo-500 focus:outline-none"
                />
              </div>

              <div className="flex items-center justify-between rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2">
                <div>
                  <p className="text-caption font-semibold text-slate-600 dark:text-slate-300">开启思考</p>
                  <p className="text-micro text-slate-400">仅在模型支持时生效</p>
                </div>
                <button
                  type="button"
                  onClick={() => setThinkingEnabled((prev) => !prev)}
                  className={`h-6 w-11 rounded-full border transition-colors ${
                    thinkingEnabled
                      ? 'border-indigo-500 bg-indigo-500'
                      : 'border-slate-300 bg-slate-200 dark:border-slate-600 dark:bg-slate-700'
                  }`}
                  aria-label="切换思考模式"
                >
                  <span
                    className={`block h-5 w-5 rounded-full bg-white transition-transform ${
                      thinkingEnabled ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>

              <button
                type="button"
                onClick={handleReset}
                className="w-full px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-body-sm font-semibold text-slate-600 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
              >
                重置对话
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  )
}
