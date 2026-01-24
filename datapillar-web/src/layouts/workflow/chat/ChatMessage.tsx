import { forwardRef, memo, useCallback, useEffect, useMemo, useState, type HTMLAttributes, type RefObject } from 'react'
import { Virtuoso, type StateSnapshot, type VirtuosoHandle } from 'react-virtuoso'
import { Activity, AlertTriangle, Bot, CheckCircle2, ChevronDown, Loader2, Sparkles, User, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { AgentActivity, ChatMessage } from '@/stores/workflowStudioStore'
import type { ProcessStatus } from '@/services/aiWorkflowService'

const PROCESS_STATUS_LABEL: Record<ProcessStatus, string> = {
  running: '进行中',
  waiting: '等待补充',
  done: '已完成',
  error: '失败',
  aborted: '已停止'
}

type ChatMessageListProps = {
  messages: ChatMessage[]
  forceScrollVersion: number
  autoScrollEnabled: boolean
  latestAssistantMessageId: string | null
  restoreStateFrom?: StateSnapshot | null
  virtuosoRef: RefObject<VirtuosoHandle | null>
  onAbort?: () => void
  onQuickSend?: (value: string) => void
  className?: string
}

export function ChatMessageList({
  messages,
  forceScrollVersion,
  autoScrollEnabled,
  latestAssistantMessageId,
  restoreStateFrom,
  virtuosoRef,
  onAbort,
  onQuickSend,
  className
}: ChatMessageListProps) {
  const followOutput = useCallback(
    (isAtBottom: boolean) => {
      if (!autoScrollEnabled) {
        return false
      }
      return isAtBottom ? 'smooth' : false
    },
    [autoScrollEnabled]
  )

  useEffect(() => {
    if (!forceScrollVersion || messages.length === 0) {
      return
    }
    let rafId = 0
    let rafId2 = 0
    rafId = window.requestAnimationFrame(() => {
      rafId2 = window.requestAnimationFrame(() => {
        virtuosoRef.current?.scrollToIndex({
          index: 'LAST',
          align: 'end',
          behavior: 'auto'
        })
      })
    })
    return () => {
      if (rafId) {
        window.cancelAnimationFrame(rafId)
      }
      if (rafId2) {
        window.cancelAnimationFrame(rafId2)
      }
    }
  }, [forceScrollVersion, messages.length, virtuosoRef])

  const initialIndex = messages.length > 0 ? messages.length - 1 : 0

  return (
    <Virtuoso
      ref={virtuosoRef}
      data={messages}
      followOutput={followOutput}
      restoreStateFrom={restoreStateFrom ?? undefined}
      initialTopMostItemIndex={restoreStateFrom ? undefined : initialIndex}
      computeItemKey={(_, message) => message.id}
      className={cn('flex-1 h-full', className)}
      components={{
        Scroller: ChatScroller,
        List: ChatList
      }}
      itemContent={(_, message) => (
        <div className="pb-3">
          <ChatBubble
            message={message}
            isLatestAssistant={message.id === latestAssistantMessageId}
            onAbort={onAbort}
            onQuickSend={onQuickSend}
          />
        </div>
      )}
    />
  )
}

const ChatList = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) => (
  <div ref={ref} {...props} className={cn('flex flex-col px-4 pb-4 pt-2', className)} />
))
ChatList.displayName = 'ChatList'

const ChatScroller = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(({ className, ...props }, ref) => (
  <div ref={ref} {...props} className={cn('overflow-y-auto scrollbar-invisible', className)} />
))
ChatScroller.displayName = 'ChatScroller'

const ChatBubble = memo(
  function ChatBubble({
    message,
    onQuickSend,
    onAbort: _onAbort,
    isLatestAssistant
  }: {
    message: ChatMessage
    onQuickSend?: (value: string) => void
    onAbort?: () => void
    isLatestAssistant?: boolean
  }) {
    const isUser = message.role === 'user'
    const formattedTime = useMemo(
      () => new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      [message.timestamp]
    )
    const avatarIconSize = 14
    const processRows = useMemo(
      () => message.processRows ?? message.agentRows ?? [],
      [message.processRows, message.agentRows]
    )
    const hasProcessRows = !isUser && processRows.length > 0
    const isStreaming = message.isStreaming === true
    const [isExpanded, setIsExpanded] = useState(false)

    const latestProcessRow = useMemo(() => {
      if (processRows.length === 0) return null
      return processRows.reduce((latest, row) => (row.timestamp > latest.timestamp ? row : latest), processRows[0])
    }, [processRows])

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

    const recommendations = useMemo(() => message.recommendations ?? [], [message.recommendations])
    const interrupt = !isUser ? message.interrupt : undefined
    const hasInterrupt = Boolean(interrupt?.text?.trim()) || (interrupt?.options?.length ?? 0) > 0
    const shouldShowAiGuide = !isUser && Boolean(isLatestAssistant) && recommendations.length > 0

    return (
      <div className={cn('flex items-start gap-2', isUser && 'flex-row-reverse')}>
        <div
          className={cn(
            'flex items-center justify-center flex-shrink-0',
            isUser
              ? 'w-8 h-8 rounded-full bg-slate-200 text-slate-600'
              : 'w-7 h-7 xl:w-8 xl:h-8 rounded-xl bg-gradient-to-br from-indigo-500 to-indigo-600 text-white shadow-lg shadow-indigo-500/30 mt-1'
          )}
        >
          {isUser ? <User size={avatarIconSize} /> : <Bot size={avatarIconSize} className="xl:scale-110" />}
        </div>
        <div
          className={cn(
            'flex flex-col gap-1',
            isUser ? 'items-end max-w-[80%]' : 'items-start max-w-[90%]'
          )}
        >
          {!isUser && (
            <div className="flex w-full items-center justify-between text-nano text-gray-400 dark:text-slate-400 font-medium ml-1 mb-1">
              <span>Datapillar AI • {formattedTime}</span>
            </div>
          )}
          <div
            className={cn(
              isUser
                ? 'rounded-2xl leading-relaxed border whitespace-pre-wrap bg-indigo-600 text-white rounded-tr-none border-indigo-600/60 mt-1.5 px-3 py-2 text-caption xl:text-body-sm font-normal'
                : 'bg-gray-100/80 dark:bg-slate-800/70 rounded-2xl rounded-tl-sm px-3 py-2 text-caption xl:text-body-sm font-normal text-gray-700 dark:text-slate-100 leading-relaxed border border-gray-200/50 dark:border-slate-700/60 mb-2 shadow-sm'
            )}
          >
            {/* 流式状态：直接渲染过程行 */}
            {isStreaming && hasProcessRows && (
              <div className="space-y-2 mb-2 pb-2 border-b border-slate-100 dark:border-slate-700/50">
                <div className="px-1 flex items-center gap-2">
                  <Loader2 size={10} className="text-indigo-500" />
                  <span className="text-tiny font-black text-indigo-500 uppercase tracking-widest">过程动态</span>
                </div>
                {processRows.map((row) => (
                  <div key={row.id} className="flex items-center gap-3">
                    <div className={cn('w-5 h-5 rounded border flex items-center justify-center shrink-0', getRowBadgeClass(row))}>
                      <RowIcon row={row} />
                    </div>
                    <span className="text-nano font-black text-slate-900 dark:text-slate-100 uppercase tracking-tighter shrink-0 w-24">
                      {row.title}
                    </span>
                    <span className="text-micro font-medium text-slate-400 truncate flex-1">
                      {buildRowMessage(row)}
                    </span>
                  </div>
                ))}
                <div className="flex items-center gap-3 pt-1">
                  <div className="flex space-x-1">
                    <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full" />
                    <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full" />
                    <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full" />
                  </div>
                  <span className="text-nano font-bold text-indigo-500 tracking-tight italic">
                    {latestProcessLabel}
                  </span>
                </div>
              </div>
            )}

            {/* 流式状态但没有过程行：显示加载中 */}
            {isStreaming && !hasProcessRows && (
              <div className="flex items-center gap-3 py-2">
                <div className="flex space-x-1">
                  <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full" />
                  <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full" />
                  <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full" />
                </div>
                <span className="text-nano font-bold text-indigo-500 tracking-tight italic">处理中</span>
              </div>
            )}

            {/* 非流式状态且有过程行：可折叠 */}
            {!isStreaming && hasProcessRows && (
              <div className="mb-2 pb-2 border-b border-slate-100 dark:border-slate-700/50">
                <button
                  type="button"
                  onClick={() => setIsExpanded(!isExpanded)}
                  className="w-full flex items-center justify-between py-1 hover:opacity-80 transition-opacity"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-tiny font-bold text-slate-400 uppercase tracking-widest">过程记录</span>
                    <span className="text-tiny font-normal text-slate-300">({processRows.length})</span>
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
                        <span className="text-nano font-black text-slate-900 dark:text-slate-100 uppercase tracking-tighter shrink-0 w-24">
                          {row.title}
                        </span>
                        <span className="text-micro font-medium text-slate-400 truncate flex-1">
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

            {hasInterrupt && (
              <div className="mt-2 pt-2 border-t border-slate-100 dark:border-slate-700/50">
                <div className="rounded-lg border border-amber-200 bg-amber-50/70 px-3 py-2 text-micro xl:text-legal font-normal text-amber-700 shadow-sm shadow-amber-100 dark:border-amber-900/40 dark:bg-amber-950/30 dark:text-amber-200 whitespace-pre-wrap">
                  {interrupt?.text}
                </div>
                {interrupt?.options && interrupt.options.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {interrupt.options.map((option) => (
                      <button
                        key={option}
                        type="button"
                        onClick={() => onQuickSend?.(option)}
                        className="inline-flex items-center gap-1.5 rounded-full border border-amber-200 bg-white px-2.5 py-1 text-micro xl:text-legal font-medium text-amber-700 hover:bg-amber-50 hover:text-amber-800 dark:border-amber-800/60 dark:bg-slate-900 dark:text-amber-200 dark:hover:bg-amber-900/30"
                      >
                        <span className="text-tiny xl:text-nano font-normal uppercase tracking-wider text-amber-400 dark:text-amber-300">确认</span>
                        <span>{option}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
          {shouldShowAiGuide && recommendations.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mb-1.5">
              {recommendations.map((item) => (
                <button
                  key={item}
                  type="button"
                  onClick={() => onQuickSend?.(item)}
                  className="flex items-center px-2.5 py-1 bg-white border border-gray-200 dark:bg-slate-800/80 dark:border-slate-700/70 rounded-full text-micro xl:text-legal font-medium text-gray-600 dark:text-slate-200 hover:border-brand-300 hover:text-brand-600 hover:bg-brand-50/50 hover:shadow-sm hover:shadow-brand-100 dark:hover:border-brand-400/50 dark:hover:text-brand-300 dark:hover:bg-brand-900/20 dark:hover:shadow-brand-500/20 transition-all group"
                >
                  <Sparkles size={9} className="mr-1.5 text-amber-400 group-hover:text-amber-500 group-hover:animate-pulse" />
                  <span>{item}</span>
                </button>
              ))}
            </div>
          )}
          {isUser && <span className="text-slate-400 dark:text-slate-500 px-1 text-nano font-normal">{formattedTime}</span>}
        </div>
      </div>
    )
  },
  (previous, next) => {
    const prevRows = previous.message.processRows ?? previous.message.agentRows ?? []
    const nextRows = next.message.processRows ?? next.message.agentRows ?? []
    const prevRecommendations = previous.message.recommendations ?? []
    const nextRecommendations = next.message.recommendations ?? []
    const recommendationsEqual =
      prevRecommendations.length === nextRecommendations.length &&
      prevRecommendations.every((value, index) => value === nextRecommendations[index])
    const prevInterrupt = previous.message.interrupt
    const nextInterrupt = next.message.interrupt
    const interruptEqual =
      (prevInterrupt?.text ?? '') === (nextInterrupt?.text ?? '') &&
      (prevInterrupt?.options ?? []).length === (nextInterrupt?.options ?? []).length &&
      (prevInterrupt?.options ?? []).every((value, index) => value === nextInterrupt?.options?.[index])
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
      recommendationsEqual &&
      interruptEqual &&
      previous.isLatestAssistant === next.isLatestAssistant
    )
  }
)
