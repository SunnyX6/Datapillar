/**
 * 底部面板骨架组件
 * 提供可折叠、可拖拽调整高度的底部面板结构
 */

import { useState, useEffect, type ReactNode } from 'react'
import { ChevronUp } from 'lucide-react'
import { motion } from 'framer-motion'

export interface BottomPanelTab {
  id: string
  label: string
  icon?: ReactNode
}

interface BaseBottomPanelProps {
  /** Tab 列表 */
  tabs: BottomPanelTab[]
  /** 当前激活的 Tab ID */
  activeTabId: string
  /** Tab 切换回调 */
  onTabChange: (id: string) => void
  /** 状态栏左侧内容 */
  statusLeft?: ReactNode
  /** 状态栏右侧内容 */
  statusRight?: ReactNode
  /** Tab 内容区 */
  children: ReactNode
  /** 默认高度 */
  defaultHeight?: number
  /** 最小高度 */
  minHeight?: number
  /** 最大高度比例（相对于窗口高度） */
  maxHeightRatio?: number
  /** 是否可折叠 */
  collapsible?: boolean
  /** 是否默认折叠 */
  defaultCollapsed?: boolean
  /** 外部控制折叠状态 */
  collapsed?: boolean
  /** 折叠状态变化回调 */
  onCollapsedChange?: (collapsed: boolean) => void
}

export function BaseBottomPanel({
  tabs,
  activeTabId,
  onTabChange,
  statusLeft,
  statusRight,
  children,
  defaultHeight = 320,
  minHeight = 60,
  maxHeightRatio = 0.7,
  collapsible = true,
  defaultCollapsed = true,
  collapsed: controlledCollapsed,
  onCollapsedChange,
}: BaseBottomPanelProps) {
  const [internalCollapsed, setInternalCollapsed] = useState(defaultCollapsed)
  const [height, setHeight] = useState(defaultHeight)
  const [isResizing, setIsResizing] = useState(false)

  // 支持受控和非受控模式
  const isCollapsed = controlledCollapsed ?? internalCollapsed

  // 处理折叠状态变化
  const handleCollapsedChange = (collapsed: boolean) => {
    setInternalCollapsed(collapsed)
    onCollapsedChange?.(collapsed)
  }

  // 拖拽开始
  const handleResizeStart = (e: React.MouseEvent) => {
    e.preventDefault()
    if (isCollapsed) return
    setIsResizing(true)
    document.body.style.userSelect = 'none'
    document.body.style.cursor = 'row-resize'
  }

  // 拖拽过程
  useEffect(() => {
    if (!isResizing) return

    const handleMouseMove = (e: MouseEvent) => {
      const newHeight = window.innerHeight - e.clientY - 28 // 28 为状态栏高度
      const maxHeight = window.innerHeight * maxHeightRatio
      if (newHeight >= minHeight && newHeight <= maxHeight) {
        setHeight(newHeight)
      }
    }

    const handleMouseUp = () => {
      setIsResizing(false)
      document.body.style.userSelect = ''
      document.body.style.cursor = ''
    }

    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)

    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
  }, [isResizing, minHeight, maxHeightRatio])

  // Tab 点击时自动展开
  const handleTabClick = (tabId: string) => {
    onTabChange(tabId)
    if (isCollapsed) {
      handleCollapsedChange(false)
    }
  }

  return (
    <div className="flex flex-col shrink-0 relative">
      {/* 拖拽遮罩层 */}
      {isResizing && <div className="fixed inset-0 z-[9999] cursor-row-resize" />}

      {/* 内容区 */}
      <div
        style={{ '--panel-content-height': `${isCollapsed ? 0 : height}px` } as React.CSSProperties}
        className={`h-[var(--panel-content-height)] bg-white dark:bg-slate-900 overflow-hidden relative shadow-[0_-4px_12px_rgba(0,0,0,0.02)] dark:shadow-[0_-4px_12px_rgba(0,0,0,0.2)] ${
          isResizing ? '' : 'transition-[height] duration-150 ease-out'
        }`}
      >
        {/* 拖拽条 */}
        <div
          onMouseDown={handleResizeStart}
          className={`h-px w-full absolute top-0 z-50 bg-slate-200 dark:bg-slate-700 transition-colors ${
            isCollapsed ? '' : 'cursor-row-resize hover:bg-indigo-500'
          }`}
        />

        {/* Tab 栏 - 位于内容区顶部 */}
        <div className="h-7 flex items-center border-b border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900 shrink-0">
          {tabs.map((tab) => {
            const isActive = activeTabId === tab.id
            return (
              <button
                key={tab.id}
                onClick={() => handleTabClick(tab.id)}
                className={`h-full px-2 flex items-center gap-1.5 text-tiny font-bold uppercase tracking-wider relative transition-all ${
                  isActive
                    ? 'text-indigo-600 dark:text-indigo-400 bg-slate-50 dark:bg-slate-800'
                    : 'text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300'
                }`}
              >
                {tab.icon}
                {tab.label}
                {isActive && (
                  <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-indigo-500" />
                )}
              </button>
            )
          })}
        </div>

        {/* 内容 */}
        <div className="h-[calc(100%-1.75rem)] overflow-auto bg-white dark:bg-slate-900">
          {children}
        </div>
      </div>

      {/* 状态栏 */}
      <div className="h-7 flex items-center justify-between px-2 bg-white dark:bg-slate-900 border-t border-slate-200 dark:border-slate-700 shrink-0 z-50">
        <div className="flex items-center gap-2 h-full">
          {/* 折叠按钮 */}
          {collapsible && (
            <button
              onClick={() => handleCollapsedChange(!isCollapsed)}
              className="p-0.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded transition-all text-slate-500 dark:text-slate-400"
            >
              <motion.div animate={{ rotate: isCollapsed ? 0 : 180 }}>
                <ChevronUp size={12} />
              </motion.div>
            </button>
          )}

          {/* 状态栏左侧 */}
          {statusLeft}
        </div>

        {/* 状态栏右侧 */}
        <div className="flex items-center gap-3">
          {statusRight}
        </div>
      </div>
    </div>
  )
}
