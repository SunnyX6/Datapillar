import type { RefObject } from 'react'
import { ChevronDown, History, MessageSquare, SquarePen, Trash2 } from 'lucide-react'
import { cn } from '@/lib/utils'

type HistorySessionGroup = {
  group: string
  items: string[]
}

type ChatHeaderProps = {
  showHistory: boolean
  onToggleHistory: () => void
  historySessions: HistorySessionGroup[]
  historyButtonRef: RefObject<HTMLButtonElement | null>
  historyCardRef: RefObject<HTMLDivElement | null>
  latestUserMessage: string
  onNewSession: () => void
}

export function ChatHeader({
  showHistory,
  onToggleHistory,
  historySessions,
  historyButtonRef,
  historyCardRef,
  latestUserMessage,
  onNewSession
}: ChatHeaderProps) {
  return (
    <div className="h-14 border-b border-slate-200/60 dark:border-white/10 flex items-center gap-3 px-4 flex-shrink-0 bg-white/80 dark:bg-slate-900/80 backdrop-blur-md relative z-40">
      {/* 三段式布局：左右固定，中间自适应并截断标题 */}
      <div className="relative flex items-center shrink-0">
        <button
          type="button"
          onClick={onToggleHistory}
          ref={historyButtonRef}
          className={cn(
            'flex items-center space-x-2 px-2 py-1.5 rounded-lg transition-all text-xs font-medium group whitespace-nowrap',
            showHistory
              ? 'bg-slate-100 text-slate-900 dark:bg-slate-800/70 dark:text-slate-100'
              : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50 dark:text-slate-400 dark:hover:text-slate-100 dark:hover:bg-slate-800/50'
          )}
        >
          <History size={14} className="text-slate-400 group-hover:text-slate-600 dark:text-slate-500 dark:group-hover:text-slate-300" />
          <span>历史会话</span>
          <ChevronDown
            size={10}
            className={cn('text-slate-400 transition-transform duration-200', showHistory && 'rotate-180')}
          />
        </button>

        {showHistory && (
          <div
            ref={historyCardRef}
            className="absolute top-full left-0 mt-2 w-72 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl shadow-slate-200/50 dark:shadow-slate-950/40 p-2 animate-in fade-in zoom-in-95 duration-100 origin-top-left flex flex-col max-h-[400px]"
          >
            <div className="flex-1 overflow-y-auto custom-scrollbar p-1">
              {historySessions.map((group) => (
                <div key={group.group} className="mb-3 last:mb-0">
                  <div className="px-2 py-1 text-micro font-bold text-slate-400 uppercase tracking-wider">
                    {group.group}
                  </div>
                  {group.items.map((item) => (
                    <button
                      key={`${group.group}-${item}`}
                      type="button"
                      className="w-full text-left px-2 py-2 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800/60 text-xs text-slate-700 dark:text-slate-200 hover:text-indigo-600 dark:hover:text-indigo-300 transition-colors flex items-center group/item"
                    >
                      <MessageSquare size={12} className="mr-2 text-slate-300 group-hover/item:text-indigo-400" />
                      <span className="truncate flex-1">{item}</span>
                    </button>
                  ))}
                </div>
              ))}
            </div>
            <div className="border-t border-slate-100 dark:border-slate-800 mt-1 pt-2 px-1">
              <button className="w-full flex items-center justify-center px-3 py-2 text-micro font-normal text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-950/30 rounded-lg transition-colors">
                <Trash2 size={12} className="mr-1.5" />
                清空历史记录
              </button>
            </div>
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0 flex items-center justify-center">
        {latestUserMessage && (
          <span className="pointer-events-none block max-w-[24rem] -translate-x-1 truncate text-legal font-semibold text-slate-600 dark:text-slate-300 whitespace-nowrap">
            {latestUserMessage}
          </span>
        )}
      </div>

      <div className="shrink-0">
        <button
          type="button"
          onClick={onNewSession}
          className="group relative flex items-center justify-center size-8 rounded-lg text-indigo-700 dark:text-indigo-300 hover:text-indigo-800 dark:hover:text-indigo-200"
          aria-label="新会话"
          title="新会话"
        >
          <SquarePen size={14} />
          <span className="sr-only">新会话</span>
          <span className="pointer-events-none absolute right-0 top-full mt-2 whitespace-nowrap rounded-md border border-slate-200 bg-white px-2 py-1 text-micro font-semibold text-slate-600 opacity-0 shadow-sm group-hover:opacity-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300">
            新会话
          </span>
        </button>
      </div>
    </div>
  )
}
