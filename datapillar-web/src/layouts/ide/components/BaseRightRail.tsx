/**
 * 右侧工具栏骨架组件
 * 提供可展开/收起的侧边面板结构
 */

import { type ReactNode } from 'react'
import { X } from 'lucide-react'
import { motion, AnimatePresence } from 'framer-motion'

export interface RightRailButton {
  id: string
  icon: ReactNode
  title: string
  /** 激活时的颜色样式 */
  activeClassName?: string
  /** 非激活时的颜色样式 */
  inactiveClassName?: string
}

interface BaseRightRailProps {
  /** 按钮列表 */
  buttons: RightRailButton[]
  /** 当前激活的面板 ID，null 表示无面板展开 */
  activePanel: string | null
  /** 面板切换回调 */
  onPanelChange: (id: string | null) => void
  /** 面板标题（根据 activePanel 动态渲染） */
  panelTitle?: ReactNode
  /** 面板内容 */
  children?: ReactNode
  /** 面板宽度 */
  panelWidth?: number
  /** 底部固定按钮 */
  bottomButtons?: RightRailButton[]
}

export function BaseRightRail({
  buttons,
  activePanel,
  onPanelChange,
  panelTitle,
  children,
  panelWidth = 320,
  bottomButtons,
}: BaseRightRailProps) {
  const togglePanel = (id: string) => {
    onPanelChange(activePanel === id ? null : id)
  }

  return (
    <div className="relative flex shrink-0 z-[60]">
      {/* 展开的面板 */}
      <AnimatePresence mode="wait">
        {activePanel && (
          <motion.div
            key={activePanel}
            initial={{ x: 20, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: 20, opacity: 0 }}
            transition={{ type: 'spring', damping: 25, stiffness: 200 }}
            className="absolute right-10 top-0 bottom-0 bg-white dark:bg-slate-900 border-l border-slate-200 dark:border-slate-700 shadow-[-8px_0_24px_rgba(0,0,0,0.06)] dark:shadow-[-8px_0_24px_rgba(0,0,0,0.3)] flex flex-col w-[var(--panel-width)]"
            style={{ '--panel-width': `${panelWidth}px` } as React.CSSProperties}
          >
            {/* 面板头部 */}
            <div className="h-10 px-3 flex items-center justify-between border-b border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900">
              <div className="flex items-center gap-2">
                {panelTitle}
              </div>
              <button
                onClick={() => onPanelChange(null)}
                className="p-0.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded transition-all text-slate-400 dark:text-slate-500"
              >
                <X size={12} />
              </button>
            </div>

            {/* 面板内容 */}
            <div className="flex-1 overflow-y-auto p-3 scrollbar-hide">
              {children}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 右侧按钮栏 */}
      <div className="w-10 flex flex-col items-center pt-1.5 pb-4 gap-1.5 bg-white dark:bg-slate-900 border-l border-slate-100 dark:border-slate-800 shrink-0 z-30 select-none">
        {/* 主要按钮 */}
        {buttons.map((button) => {
          const isActive = activePanel === button.id
          return (
            <button
              key={button.id}
              onClick={() => togglePanel(button.id)}
              className={`p-2 rounded-lg transition-all hover:scale-110 active:scale-95 ${
                isActive
                  ? button.activeClassName || 'bg-indigo-600 text-white shadow-indigo-600/30 shadow-md'
                  : button.inactiveClassName || 'text-slate-300 dark:text-slate-600 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-slate-50 dark:hover:bg-slate-800'
              }`}
              title={button.title}
            >
              {button.icon}
            </button>
          )
        })}

        {/* 弹性空间 */}
        <div className="flex-1" />

        {/* 底部按钮 */}
        {bottomButtons?.map((button) => {
          const isActive = activePanel === button.id
          return (
            <button
              key={button.id}
              onClick={() => togglePanel(button.id)}
              className={`p-2 rounded-lg transition-all ${
                isActive
                  ? button.activeClassName || 'bg-indigo-600 text-white shadow-indigo-600/30 shadow-md'
                  : button.inactiveClassName || 'text-slate-300 dark:text-slate-600 hover:text-slate-900 dark:hover:text-slate-200 hover:rotate-45'
              }`}
              title={button.title}
            >
              {button.icon}
            </button>
          )
        })}
      </div>
    </div>
  )
}
