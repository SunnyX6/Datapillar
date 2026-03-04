/**
 * Editor basic skeleton component
 * Provide a unified layout structure，For reuse by various language editors
 */

import { useRef, useState, useCallback, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { Plus, FileCode, X, MoreVertical, ArrowRight, Fingerprint } from 'lucide-react'
import { motion, AnimatePresence } from 'framer-motion'
import { cn } from '@/utils'
import { menuWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'

export interface EditorTab {
  id: string
  name: string
  icon?: React.ReactNode
}

/** right click menu item */
export interface ContextMenuItem {
  id: string
  label: string
  icon?: React.ReactNode
  onClick?: () => void
  disabled?: boolean
}

/** Right-click menu grouping */
export interface ContextMenuGroup {
  id: string
  title: string
  highlight?: boolean
  items: ContextMenuItem[]
}

interface BaseEditorProps {
  /** Tab list */
  tabs: EditorTab[]
  /** currently active Tab ID */
  activeTabId: string
  /** Tab Switch callback */
  onTabChange: (id: string) => void
  /** Tab Close callback */
  onTabClose?: (id: string) => void
  /** New Tab callback */
  onAddTab?: () => void
  /** Contents on the left side of the toolbar */
  toolbarLeft?: React.ReactNode
  /** Contents on the right side of the toolbar */
  toolbarRight?: React.ReactNode
  /** Editor main content area */
  children: React.ReactNode
  /** bottom panel */
  bottomPanel?: React.ReactNode
  /** right panel */
  rightPanel?: React.ReactNode
  /** Right-click menu grouping */
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
  // Track whether animation should be disabled（New tab Disabled when）
  const [skipAnimation, setSkipAnimation] = useState(false)
  const skipAnimationTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Right-click menu status
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null)
  const contextMenuRef = useRef<HTMLDivElement>(null)

  // packaging onAddTab，Adding tab Temporarily disable animations when
  const handleAddTab = useCallback(() => {
    if (!onAddTab) return

    // Disable animation
    setSkipAnimation(true)

    // Clear previous timer
    if (skipAnimationTimerRef.current) {
      clearTimeout(skipAnimationTimerRef.current)
    }

    // call the original onAddTab
    onAddTab()

    // Delay resume animation
    skipAnimationTimerRef.current = setTimeout(() => {
      setSkipAnimation(false)
      skipAnimationTimerRef.current = null
    }, 50)
  }, [onAddTab])

  // Right-click menu processing
  const handleContextMenu = useCallback((e: React.MouseEvent) => {
    if (!contextMenuGroups || contextMenuGroups.length === 0) return
    e.preventDefault()
    setContextMenu({ x: e.clientX, y: e.clientY })
  }, [contextMenuGroups])

  // Click outside to close menu
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

  // Menu item click
  const handleMenuItemClick = useCallback((item: ContextMenuItem) => {
    if (item.disabled) return
    setContextMenu(null)
    item.onClick?.()
  }, [])

  return (
    <div className="flex-1 flex h-full overflow-hidden bg-white dark:bg-slate-900">
      {/* Core editor and results area */}
      <div className="flex-1 flex flex-col min-w-0 h-full relative border-r border-slate-100 dark:border-slate-800">

        {/* 1. TOP TAB BAR */}
        <div className="h-10 flex items-center bg-white dark:bg-slate-900 border-b border-slate-200/60 dark:border-slate-700/60 shrink-0 overflow-hidden relative">
          <div className="flex items-end h-full flex-1 overflow-x-auto overflow-y-hidden scrollbar-invisible px-2">
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
        <div className="flex-1 min-h-0 relative">
          {/* Editor main content area */}
          <div
            onContextMenu={handleContextMenu}
            className="absolute inset-0 overflow-hidden"
          >
            {children}
          </div>

          {/* 4. bottom panel（Overlay over editor，Avoid triggering layout jitter） */}
          {bottomPanel && (
            <div className="absolute inset-x-0 bottom-0 z-30">
              {bottomPanel}
            </div>
          )}
        </div>
      </div>

      {/* 5. right panel */}
      {rightPanel}

      {/* right click menu - Glass mimicry design */}
      {contextMenu && contextMenuGroups && contextMenuGroups.length > 0 && createPortal(
        <AnimatePresence>
          <motion.div
            ref={contextMenuRef}
            initial={{ opacity: 0, scale: 0.98, y: 5 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.98 }}
            style={{ top: contextMenu.y, left: contextMenu.x }}
            className={cn(
              menuWidthClassMap.xxlarge,
              'fixed z-[100000] bg-white/80 dark:bg-slate-900/90 backdrop-blur-2xl rounded-2xl border border-slate-200/60 dark:border-slate-700/60 shadow-[0_24px_50px_-12px_rgba(0,0,0,0.08)] dark:shadow-[0_24px_50px_-12px_rgba(0,0,0,0.4)] p-1.5'
            )}
          >
            {contextMenuGroups.map((group, groupIndex) => (
              <div key={group.id}>
                {groupIndex > 0 && (
                  <div className="my-1.5 border-t border-slate-100/50 dark:border-slate-700/50" />
                )}
                <div className="px-3 py-1.5 flex items-center justify-between">
                  <span className={cn(
                    TYPOGRAPHY.contextMenuTitle,
                    'uppercase tracking-[0.08em]',
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
                        <span className="text-micro">{item.label}</span>
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
