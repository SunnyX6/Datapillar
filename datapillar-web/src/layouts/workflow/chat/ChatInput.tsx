import type { KeyboardEvent as ReactKeyboardEvent } from 'react'
import { ArrowUp, Command, Zap } from 'lucide-react'
import { cn } from '@/lib/utils'

type ChatInputProps = {
  input: string
  onInputChange: (value: string) => void
  onSend: () => void
  onAbort: () => void
  canSend: boolean
  isGenerating: boolean
  isWaitingForResume: boolean
  onCompositionStart: () => void
  onCompositionEnd: () => void
  onKeyDown: (event: ReactKeyboardEvent<HTMLTextAreaElement>) => void
  onFocus?: () => void
}

export function ChatInput({
  input,
  onInputChange,
  onSend,
  onAbort,
  canSend,
  isGenerating,
  isWaitingForResume,
  onCompositionStart,
  onCompositionEnd,
  onKeyDown,
  onFocus
}: ChatInputProps) {
  const placeholder = isWaitingForResume ? '请补充关键信息以继续...' : '描述你的数据工作流需求...'
  const sendLabel = isGenerating ? '停止' : isWaitingForResume ? '继续' : '发送'
  return (
    <div className="p-2 xl:p-3 border-t border-gray-100 bg-white/90 dark:border-slate-700/60 dark:bg-slate-900/80 backdrop-blur-sm relative z-30">
      <div className={cn('relative group transition-all duration-300', input ? 'shadow-lg shadow-brand-500/10' : '')}>
        <div className="absolute -inset-0.5 bg-gradient-to-r from-brand-200 to-blue-200 dark:from-brand-500/30 dark:to-blue-500/30 rounded-2xl opacity-0 group-focus-within:opacity-100 transition duration-500 blur-[2px]"></div>
        <div className="relative bg-white border border-gray-200 dark:bg-slate-900/70 dark:border-slate-700/60 rounded-2xl shadow-sm flex flex-col focus-within:border-transparent transition-all overflow-hidden">
          <textarea
            value={input}
            rows={2}
            onChange={(event) => onInputChange(event.target.value)}
            onCompositionStart={onCompositionStart}
            onCompositionEnd={onCompositionEnd}
            onKeyDown={onKeyDown}
            onFocus={onFocus}
            className="w-full pl-3 pr-3 pt-2 pb-7 xl:pl-3.5 xl:pr-3.5 xl:pt-2.5 xl:pb-8 text-legal xl:text-caption font-normal text-gray-700 dark:text-slate-100 placeholder:text-gray-400 dark:placeholder:text-slate-500 resize-none focus:outline-none bg-transparent min-h-[46px] xl:min-h-[54px] max-h-[120px] xl:max-h-[140px] custom-scrollbar"
            placeholder={placeholder}
          />
          <div className="absolute bottom-1 left-1 right-1 xl:bottom-1.5 xl:left-1.5 xl:right-1.5 flex justify-between items-center">
            <div className="flex space-x-1 ml-1">
              <button type="button" className="p-0.5 text-gray-400 dark:text-slate-400 hover:text-gray-600 dark:hover:text-slate-200 hover:bg-gray-100 dark:hover:bg-slate-800/70 rounded-lg transition-colors" title="Command Library">
                <Command size={12} />
              </button>
              <button type="button" className="p-0.5 text-gray-400 dark:text-slate-400 hover:text-gray-600 dark:hover:text-slate-200 hover:bg-gray-100 dark:hover:bg-slate-800/70 rounded-lg transition-colors" title="Magic Enhance">
                <Zap size={12} />
              </button>
            </div>
            <div className="flex items-center space-x-2.5">
              <span className="text-nano text-gray-300 dark:text-slate-500 font-medium hidden group-focus-within:inline-block animate-fade-in">
                {isWaitingForResume ? '等待补充信息以继续。' : 'AI 可能出错，请验证。'}
              </span>
              <button
                type="button"
                data-send-btn
                onClick={isGenerating ? onAbort : onSend}
                disabled={!isGenerating && !canSend}
                aria-label={sendLabel}
                title={sendLabel}
                className={cn(
                  'p-0.5 rounded-lg transition-all duration-200 flex items-center justify-center mr-1',
                  isGenerating
                    ? 'bg-gray-100 text-slate-700 shadow-sm hover:bg-gray-200 dark:bg-slate-800 dark:text-slate-100 dark:hover:bg-slate-700 dark:shadow-md'
                    : canSend
                    ? 'bg-indigo-600 text-white shadow-md hover:bg-indigo-700 dark:bg-slate-800 dark:text-slate-100 dark:hover:bg-slate-700'
                    : 'bg-gray-100 text-gray-300 cursor-not-allowed dark:bg-slate-800 dark:text-slate-600'
                )}
              >
                {isGenerating ? (
                  <span className="relative inline-flex items-center justify-center size-4">
                    <span className="absolute inset-0 rounded-full border-2 border-slate-900/70 border-t-transparent animate-spin dark:border-slate-600/60 dark:border-t-indigo-400/90 dark:border-r-indigo-400/90" />
                    <span className="size-1.5 rounded-[2px] bg-slate-900 dark:bg-indigo-400" />
                  </span>
                ) : (
                  <span className="inline-flex items-center justify-center size-4">
                    <ArrowUp size={16} />
                  </span>
                )}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
