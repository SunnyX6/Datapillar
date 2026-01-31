import { useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import { ArrowUp, Check, ChevronDown, Command, Zap } from 'lucide-react'
import { Tooltip } from '@/components/ui'
import { cn } from '@/lib/utils'
import { DEFAULT_WORKFLOW_MODEL_ID, WORKFLOW_MODEL_OPTIONS } from '@/config/workflowModels'
import { CHAT_COMMAND_OPTIONS, type ChatCommandId } from './commandLibrary'

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
  selectedModelId: string
  onModelChange: (value: string) => void
  onManageModels?: () => void
  onCommand: (commandId: ChatCommandId) => void
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
  onFocus,
  selectedModelId,
  onModelChange,
  onManageModels,
  onCommand
}: ChatInputProps) {
  const placeholder = isWaitingForResume ? '请补充关键信息以继续...' : '描述你的数据工作流需求...'
  const sendLabel = isGenerating ? '停止' : isWaitingForResume ? '继续' : '发送'
  const [modelOpen, setModelOpen] = useState(false)
  const [commandOpen, setCommandOpen] = useState(false)
  const modelTriggerRef = useRef<HTMLButtonElement | null>(null)
  const modelDropdownRef = useRef<HTMLDivElement | null>(null)
  const [modelDropdownPos, setModelDropdownPos] = useState<{ top: number; left: number; width: number } | null>(null)
  const commandTriggerRef = useRef<HTMLButtonElement | null>(null)
  const commandDropdownRef = useRef<HTMLDivElement | null>(null)
  const [commandDropdownPos, setCommandDropdownPos] = useState<{ top: number; left: number; width: number } | null>(null)
  const resolvedModelId = selectedModelId || DEFAULT_WORKFLOW_MODEL_ID
  const selectedModel = useMemo(
    () => WORKFLOW_MODEL_OPTIONS.find((model) => model.id === resolvedModelId) ?? WORKFLOW_MODEL_OPTIONS[0],
    [resolvedModelId]
  )

  const modelToneClassMap: Record<string, string> = {
    emerald: 'bg-emerald-100 text-emerald-600 dark:bg-emerald-500/20 dark:text-emerald-300',
    violet: 'bg-violet-100 text-violet-600 dark:bg-violet-500/20 dark:text-violet-300',
    blue: 'bg-blue-100 text-blue-600 dark:bg-blue-500/20 dark:text-blue-300'
  }

  useEffect(() => {
    if (!modelOpen) return
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node
      if (modelTriggerRef.current?.contains(target)) return
      if (modelDropdownRef.current?.contains(target)) return
      setModelOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [modelOpen])

  useEffect(() => {
    if (modelOpen) return
    setModelDropdownPos(null)
  }, [modelOpen])

  useEffect(() => {
    if (!commandOpen) return
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node
      if (commandTriggerRef.current?.contains(target)) return
      if (commandDropdownRef.current?.contains(target)) return
      setCommandOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [commandOpen])

  useEffect(() => {
    if (commandOpen) return
    setCommandDropdownPos(null)
  }, [commandOpen])

  useLayoutEffect(() => {
    if (!modelOpen) return
    const updatePosition = () => {
      const trigger = modelTriggerRef.current
      if (!trigger) return
      const rect = trigger.getBoundingClientRect()
      const dropdownWidth = Math.max(rect.width, 240)
      const dropdownHeight = 248
      const spaceBelow = window.innerHeight - rect.bottom - 16
      const spaceAbove = rect.top - 16
      const left = Math.max(12, Math.min(rect.left, window.innerWidth - dropdownWidth - 12))
      const top = spaceBelow < dropdownHeight && spaceAbove > spaceBelow
        ? rect.top - dropdownHeight - 8
        : rect.bottom + 8
      setModelDropdownPos({ top, left, width: dropdownWidth })
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [modelOpen])

  useLayoutEffect(() => {
    if (!commandOpen) return
    const updatePosition = () => {
      const trigger = commandTriggerRef.current
      if (!trigger) return
      const rect = trigger.getBoundingClientRect()
      const dropdownWidth = 220
      const dropdownHeight = 156
      const spaceBelow = window.innerHeight - rect.bottom - 16
      const spaceAbove = rect.top - 16
      const left = Math.max(12, Math.min(rect.left, window.innerWidth - dropdownWidth - 12))
      const top = spaceBelow < dropdownHeight && spaceAbove > spaceBelow
        ? rect.top - dropdownHeight - 8
        : rect.bottom + 8
      setCommandDropdownPos({ top, left, width: dropdownWidth })
    }
    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [commandOpen])
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
            <div className="flex items-center gap-1 ml-1">
              <button
                ref={modelTriggerRef}
                type="button"
                aria-haspopup="listbox"
                aria-expanded={modelOpen}
                onClick={() => {
                  setModelOpen((open) => {
                    const nextOpen = !open
                    if (nextOpen) {
                      setCommandOpen(false)
                    }
                    return nextOpen
                  })
                }}
                className={cn(
                  'flex items-center gap-1.5 px-1.5 py-0.5 rounded-lg border text-[10px] font-semibold transition-colors max-w-[140px]',
                  modelOpen
                    ? 'bg-gray-100 border-gray-200 text-gray-700 dark:bg-slate-800/70 dark:border-slate-700 dark:text-slate-100'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:bg-gray-100 dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-800/70'
                )}
                title="选择模型"
              >
                <span
                  className={cn(
                    'flex size-4 items-center justify-center rounded-md text-[10px] font-bold',
                    modelToneClassMap[selectedModel?.tone ?? 'emerald']
                  )}
                >
                  {selectedModel?.badge ?? 'M'}
                </span>
                <span className="truncate">{selectedModel?.label ?? '选择模型'}</span>
                <ChevronDown size={12} className={cn('text-gray-400 transition-transform', modelOpen && 'rotate-180')} />
              </button>
              <span className="mx-1 h-4 w-px bg-slate-200 dark:bg-slate-700" aria-hidden="true" />
              <Tooltip content="指令库" side="top">
                <button
                  ref={commandTriggerRef}
                  type="button"
                  aria-label="指令库"
                  aria-haspopup="listbox"
                  aria-expanded={commandOpen}
                  onClick={() => {
                    setCommandOpen((open) => {
                      const nextOpen = !open
                      if (nextOpen) {
                        setModelOpen(false)
                      }
                      return nextOpen
                    })
                  }}
                  className="p-0.5 text-gray-400 dark:text-slate-400 hover:text-gray-600 dark:hover:text-slate-200 hover:bg-gray-100 dark:hover:bg-slate-800/70 rounded-lg transition-colors"
                >
                  <Command size={12} />
                </button>
              </Tooltip>
              <Tooltip content="智能增强" side="top">
                <button
                  type="button"
                  aria-label="智能增强"
                  className="p-0.5 text-gray-400 dark:text-slate-400 hover:text-gray-600 dark:hover:text-slate-200 hover:bg-gray-100 dark:hover:bg-slate-800/70 rounded-lg transition-colors"
                >
                  <Zap size={12} />
                </button>
              </Tooltip>
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
      {modelOpen &&
        modelDropdownPos &&
        createPortal(
          <div
            ref={modelDropdownRef}
            style={{
              top: modelDropdownPos.top,
              left: modelDropdownPos.left,
              width: modelDropdownPos.width
            }}
            className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150"
          >
            <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-700/70 flex items-center justify-between">
              <span className="text-micro font-semibold text-slate-400 uppercase tracking-wider">选择模型</span>
              <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 border border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700">
                PRO
              </span>
            </div>
            <div className="p-1.5" role="listbox">
              {WORKFLOW_MODEL_OPTIONS.map((model) => {
                const isSelected = model.id === resolvedModelId
                return (
                  <button
                    key={model.id}
                    type="button"
                    role="option"
                    aria-selected={isSelected}
                    onClick={() => {
                      onModelChange(model.id)
                      setModelOpen(false)
                    }}
                    className={cn(
                      'w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg transition-colors text-left',
                      isSelected
                        ? 'bg-slate-100/70 dark:bg-slate-800/70'
                        : 'hover:bg-slate-50 dark:hover:bg-slate-800/60'
                    )}
                  >
                    <span
                      className={cn(
                        'flex size-7 items-center justify-center rounded-lg text-xs font-bold',
                        modelToneClassMap[model.tone] ?? modelToneClassMap.emerald
                      )}
                    >
                      {model.badge}
                    </span>
                    <span className="flex-1 min-w-0">
                      <span className="block text-xs font-semibold text-slate-800 dark:text-slate-100 truncate">
                        {model.label}
                      </span>
                      <span className="block text-[10px] text-slate-400 truncate">{model.providerLabel}</span>
                    </span>
                    {isSelected && <Check size={14} className="text-indigo-500 flex-shrink-0" />}
                  </button>
                )
              })}
            </div>
            <div className="border-t border-slate-100 dark:border-slate-700/70 px-2 py-1.5">
              <button
                type="button"
                onClick={() => {
                  onManageModels?.()
                  setModelOpen(false)
                }}
                disabled={!onManageModels}
                className={cn(
                  'w-full text-left px-2 py-1.5 text-xs font-medium rounded-lg transition-colors',
                  onManageModels
                    ? 'text-slate-500 hover:text-slate-800 hover:bg-slate-50 dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-800/60'
                    : 'text-slate-300 dark:text-slate-600 cursor-not-allowed'
                )}
              >
                Manage Models...
              </button>
            </div>
          </div>,
          document.body
        )}
      {commandOpen &&
        commandDropdownPos &&
        createPortal(
          <div
            ref={commandDropdownRef}
            style={{
              top: commandDropdownPos.top,
              left: commandDropdownPos.left,
              width: commandDropdownPos.width
            }}
            className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150"
          >
            <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-700/70">
              <span className="text-micro font-semibold text-slate-400 uppercase tracking-wider">指令库</span>
            </div>
            <div className="p-1.5 space-y-1" role="listbox">
              {CHAT_COMMAND_OPTIONS.map((command) => (
                <button
                  key={command.id}
                  type="button"
                  role="option"
                  onClick={() => {
                    onCommand(command.id)
                    setCommandOpen(false)
                  }}
                  className="w-full rounded-lg px-2.5 py-2 text-left transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/60"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-semibold text-slate-800 dark:text-slate-100 font-mono">
                      {command.label}
                    </span>
                    <span className="text-[10px] text-slate-400">{command.title}</span>
                  </div>
                  <div className="text-[10px] text-slate-400 mt-0.5">{command.description}</div>
                </button>
              ))}
            </div>
          </div>,
          document.body
        )}
    </div>
  )
}
