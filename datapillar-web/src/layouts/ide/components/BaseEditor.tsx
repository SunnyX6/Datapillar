/**
 * 编辑器基础骨架组件
 * 提供统一的布局结构，供各语言编辑器复用
 */

import { useRef, useState, useCallback, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { Plus, FileCode, X, MoreVertical, ArrowRight, Fingerprint } from 'lucide-react'
import { motion, AnimatePresence } from 'framer-motion'
import { cn } from '@/lib/utils'

export interface EditorTab {
  id: string
  name: string
  icon?: React.ReactNode
}

/** 右键菜单项 */
export interface ContextMenuItem {
  id: string
  label: string
  icon?: React.ReactNode
  onClick?: () => void
}

/** 右键菜单分组 */
export interface ContextMenuGroup {
  id: string
  title: string
  highlight?: boolean
  items: ContextMenuItem[]
}

interface BaseEditorProps {
  /** Tab 列表 */
  tabs: EditorTab[]
  /** 当前激活的 Tab ID */
  activeTabId: string
  /** Tab 切换回调 */
  onTabChange: (id: string) => void
  /** Tab 关闭回调 */
  onTabClose?: (id: string) => void
  /** 新建 Tab 回调 */
  onAddTab?: () => void
  /** 工具栏左侧内容 */
  toolbarLeft?: React.ReactNode
  /** 工具栏右侧内容 */
  toolbarRight?: React.ReactNode
  /** 编辑器主内容区 */
  children: React.ReactNode
  /** 底部面板 */
  bottomPanel?: React.ReactNode
  /** 右侧面板 */
  rightPanel?: React.ReactNode
  /** 右键菜单分组 */
  contextMenuGroups?: ContextMenuGroup[]
}

export function BaseEditor({
  tabs,
  activeTabId,
  onTabChange,
  onTabClose,
  onAddTab,
  toolbarLeft,
  toolbarRight,
  children,
  bottomPanel,
  rightPanel,
  contextMenuGroups
}: BaseEditorProps) {
  // 跟踪是否应该禁用动画（新增 tab 时禁用）
  const [skipAnimation, setSkipAnimation] = useState(false)
  const skipAnimationTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 右键菜单状态
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null)
  const contextMenuRef = useRef<HTMLDivElement>(null)

  // 包装 onAddTab，在添加 tab 时临时禁用动画
  const handleAddTab = useCallback(() => {
    if (!onAddTab) return

    // 禁用动画
    setSkipAnimation(true)

    // 清除之前的定时器
    if (skipAnimationTimerRef.current) {
      clearTimeout(skipAnimationTimerRef.current)
    }

    // 调用原始的 onAddTab
    onAddTab()

    // 延迟恢复动画
    skipAnimationTimerRef.current = setTimeout(() => {
      setSkipAnimation(false)
      skipAnimationTimerRef.current = null
    }, 50)
  }, [onAddTab])

  // 右键菜单处理
  const handleContextMenu = useCallback((e: React.MouseEvent) => {
    if (!contextMenuGroups || contextMenuGroups.length === 0) return
    e.preventDefault()
    setContextMenu({ x: e.clientX, y: e.clientY })
  }, [contextMenuGroups])

  // 点击外部关闭菜单
  useEffect(() => {
    if (!contextMenu) return

    const handleClickOutside = (e: MouseEvent) => {
      if (contextMenuRef.current && !contextMenuRef.current.contains(e.target as Node)) {
        setContextMenu(null)
      }
    }

    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setContextMenu(null)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [contextMenu])

  // 菜单项点击
  const handleMenuItemClick = useCallback((item: ContextMenuItem) => {
    if (item.disabled) return
    setContextMenu(null)
    item.onClick?.()
  }, [])

  return (
    <div className="flex-1 flex h-full overflow-hidden bg-white dark:bg-slate-900">
      {/* 核心编辑器与结果区 */}
      <div className="flex-1 flex flex-col min-w-0 h-full relative border-r border-slate-100 dark:border-slate-800">

        {/* 1. TOP TAB BAR */}
        <div className="h-10 flex items-center bg-white dark:bg-slate-900 border-b border-slate-200/60 dark:border-slate-700/60 shrink-0 overflow-hidden relative">
          <div className="flex items-end h-full flex-1 overflow-x-auto scrollbar-hide px-2">
            {tabs.map((tab, index) => {
              const isActive = activeTabId === tab.id
              return (
                <div key={tab.id} className="relative flex items-end">
                  <button
                    onClick={() => onTabChange(tab.id)}
                    className={`
                      group relative px-3 h-9 flex items-center gap-1.5 text-body-sm font-semibold transition-colors select-none z-10 min-w-28
                      ${isActive ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'}
                    `}
                  >
                    {isActive && (
                      <>
                        <motion.div
                          layoutId={skipAnimation ? undefined : "active-tab-bg"}
                          className="absolute inset-0 bg-slate-50 dark:bg-slate-800 rounded-t-lg z-0"
                        />
                        <div className="absolute bottom-0 -left-2 w-2 h-2 overflow-hidden pointer-events-none">
                          <div className="absolute top-0 right-0 w-full h-full rounded-br-lg shadow-[3px_3px_0_3px_#f8fafc] dark:shadow-[3px_3px_0_3px_#1e293b]" />
                        </div>
                        <div className="absolute bottom-0 -right-2 w-2 h-2 overflow-hidden pointer-events-none">
                          <div className="absolute top-0 left-0 w-full h-full rounded-bl-lg shadow-[-3px_3px_0_3px_#f8fafc] dark:shadow-[-3px_3px_0_3px_#1e293b]" />
                        </div>
                        <div className="absolute -bottom-px left-0 right-0 h-px bg-slate-50 dark:bg-slate-800 z-[15]" />
                      </>
                    )}

                    {tab.icon || <FileCode size={11} className={`relative z-10 shrink-0 transition-colors ${isActive ? 'text-indigo-500 dark:text-indigo-400' : 'text-slate-400 dark:text-slate-500'}`} />}
                    <span className="relative z-10 truncate flex-1 text-left">{tab.name}</span>

                    <div className="relative z-10 w-3.5 h-3.5 flex items-center justify-center shrink-0">
                      {onTabClose && (
                        <button
                          onClick={(e) => { e.stopPropagation(); onTabClose(tab.id) }}
                          className={`p-0.5 rounded hover:bg-slate-100 dark:hover:bg-slate-700 transition-all ${isActive ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'}`}
                        >
                          <X size={8} className="text-slate-400 dark:text-slate-500 hover:text-rose-500" />
                        </button>
                      )}
                    </div>
                  </button>

                  {!isActive && index < tabs.length - 1 && activeTabId !== tabs[index + 1].id && (
                    <div className="absolute right-0 top-2 bottom-2 w-px bg-slate-300/40 dark:bg-slate-600/40" />
                  )}
                </div>
              )
            })}

            {onAddTab && (
              <button
                onClick={handleAddTab}
                className="ml-2 p-1.5 self-center text-slate-400 dark:text-slate-500 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-white dark:hover:bg-slate-800 rounded transition-all active:scale-90"
              >
                <Plus size={14} strokeWidth={2.5} />
              </button>
            )}
          </div>
          <button className="shrink-0 mr-3 p-1.5 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-white dark:hover:bg-slate-800 rounded transition-all">
            <MoreVertical size={14} />
          </button>
        </div>

        {/* 2. TOOLBAR */}
        {(toolbarLeft || toolbarRight) && (
          <div className="h-9 flex items-center justify-between pr-3 border-b border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900 shrink-0 z-10">
            <div className="flex items-center">
              {toolbarLeft}
            </div>
            <div className="flex items-center gap-1.5">
              {toolbarRight}
            </div>
          </div>
        )}

        {/* 3. EDITOR & RESULTS CONTAINER */}
        <div className="flex-1 flex flex-col min-h-0 relative">
          {/* 编辑器主内容区 */}
          <div
            onContextMenu={handleContextMenu}
            className="flex-1 relative overflow-hidden"
          >
            {children}
          </div>

          {/* 4. 底部面板 */}
          {bottomPanel}
        </div>
      </div>

      {/* 5. 右侧面板 */}
      {rightPanel}

      {/* 右键菜单 - 玻璃拟态设计 */}
      {contextMenu && contextMenuGroups && contextMenuGroups.length > 0 && createPortal(
        <AnimatePresence>
          <motion.div
            ref={contextMenuRef}
            initial={{ opacity: 0, scale: 0.98, y: 5 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.98 }}
            style={{ top: contextMenu.y, left: contextMenu.x }}
            className="fixed z-[100000] w-56 bg-white/80 dark:bg-slate-900/90 backdrop-blur-2xl rounded-2xl border border-slate-200/60 dark:border-slate-700/60 shadow-[0_24px_50px_-12px_rgba(0,0,0,0.08)] dark:shadow-[0_24px_50px_-12px_rgba(0,0,0,0.4)] p-1.5"
          >
            {contextMenuGroups.map((group, groupIndex) => (
              <div key={group.id}>
                {groupIndex > 0 && (
                  <div className="my-1.5 border-t border-slate-100/50 dark:border-slate-700/50" />
                )}
                <div className="px-3 py-2 flex items-center justify-between">
                  <span className={cn(
                    'text-nano font-black uppercase tracking-[0.1em]',
                    group.highlight ? 'text-indigo-500/70' : 'text-slate-400 dark:text-slate-500'
                  )}>
                    {group.title}
                  </span>
                  {!group.highlight && <Fingerprint size={10} className="text-indigo-400 dark:text-indigo-500" />}
                </div>
                <div className="space-y-0.5">
                  {group.items.map((item) => (
                    <button
                      key={item.id}
                      onClick={() => handleMenuItemClick(item)}
                      className={cn(
                        'w-full flex items-center justify-between px-3 py-2 rounded-xl text-left transition-all group',
                        group.highlight
                          ? 'hover:bg-indigo-50/50 dark:hover:bg-indigo-900/30 text-slate-700 dark:text-slate-200'
                          : 'hover:bg-slate-100/50 dark:hover:bg-slate-800/50 text-slate-600 dark:text-slate-300'
                      )}
                    >
                      <div className="flex items-center gap-2.5">
                        {item.icon && (
                          <span className="text-slate-400 dark:text-slate-500 group-hover:text-slate-700 dark:group-hover:text-slate-200 transition-colors">
                            {item.icon}
                          </span>
                        )}
                        <span className="text-xs font-medium">{item.label}</span>
                      </div>
                      {group.highlight && (
                        <ArrowRight size={10} className="opacity-0 group-hover:opacity-40 -translate-x-2 group-hover:translate-x-0 transition-all" />
                      )}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </motion.div>
        </AnimatePresence>,
        document.body
      )}
    </div>
  )
}
