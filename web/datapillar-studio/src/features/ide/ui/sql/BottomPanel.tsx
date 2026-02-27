/**
 * SQL编辑器 - 底部面板组件
 * 基于 BaseBottomPanel 骨架构建
 */

import { useState, useEffect, useRef } from 'react'
import {
  ChevronDown, Terminal,
  Table as TableIcon, MessageSquare,
  Activity, Cpu, Check, ArrowUpDown, Filter
} from 'lucide-react'
import { motion, AnimatePresence } from 'framer-motion'
import { BaseBottomPanel, type BottomPanelTab } from '../components'
import { menuWidthClassMap } from '@/design-tokens/dimensions'
import type { ExecuteResult } from '@/services/sqlService'

interface Dialect {
  id: string
  name: string
  color: string
}

interface ExecutionLog {
  time: string
  msg: string
  type: string
}

interface BottomPanelProps {
  cursor: { ln: number; col: number }
  activeDialect: Dialect
  dialects: Dialect[]
  onDialectChange: (dialect: Dialect) => void
  executeResult: ExecuteResult | null
  executionLogs: ExecutionLog[]
  isExecuting: boolean
  /** 外部控制折叠状态 */
  collapsed?: boolean
  /** 折叠状态变化回调 */
  onCollapsedChange?: (collapsed: boolean) => void
  /** 外部控制激活的 Tab */
  activeTab?: string
  /** Tab 切换回调 */
  onActiveTabChange?: (tab: string) => void
}

/** 底部面板 Tab 配置 */
const BOTTOM_TABS: BottomPanelTab[] = [
  { id: 'results', label: 'Results', icon: <TableIcon size={10} /> },
  { id: 'messages', label: 'Messages', icon: <MessageSquare size={10} /> }
]

export function BottomPanel({
  cursor,
  activeDialect,
  dialects,
  onDialectChange,
  executeResult,
  executionLogs,
  isExecuting: _isExecuting,
  collapsed: controlledCollapsed,
  onCollapsedChange,
  activeTab: controlledActiveTab,
  onActiveTabChange
}: BottomPanelProps) {
  const [internalActiveTab, setInternalActiveTab] = useState<string>('results')
  const [showDialects, setShowDialects] = useState(false)
  const [internalCollapsed, setInternalCollapsed] = useState(true)
  const dialectButtonRef = useRef<HTMLButtonElement>(null)
  const dialectDropdownRef = useRef<HTMLDivElement>(null)

  // 支持受控和非受控模式
  const isCollapsed = controlledCollapsed ?? internalCollapsed
  const activeTab = controlledActiveTab ?? internalActiveTab

  const handleCollapsedChange = (collapsed: boolean) => {
    setInternalCollapsed(collapsed)
    onCollapsedChange?.(collapsed)
  }

  const handleTabChange = (tab: string) => {
    setInternalActiveTab(tab)
    onActiveTabChange?.(tab)
  }

  // 点击外部关闭语言选择框
  useEffect(() => {
    if (!showDialects) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (dialectButtonRef.current?.contains(target)) return
      if (dialectDropdownRef.current?.contains(target)) return
      setShowDialects(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [showDialects])

  // 状态栏左侧 - 光标位置
  const statusLeft = (
    <div className="flex items-center gap-1.5 text-tiny font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider border-r border-slate-200 dark:border-slate-700 pr-2 h-3 select-none">
      <Terminal size={9} /> {cursor.ln}:{cursor.col}
    </div>
  )

  // 状态栏右侧 - 方言选择器 + 在线状态
  const statusRight = (
    <>
      <div className="relative">
        <button
          ref={dialectButtonRef}
          onClick={() => setShowDialects(!showDialects)}
          className="flex items-center gap-1.5 text-tiny font-bold text-slate-500 dark:text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 uppercase tracking-wider transition-colors"
        >
          <Cpu size={9} /> {activeDialect.name} <ChevronDown size={8} />
        </button>
        <AnimatePresence>
          {showDialects && (
            <motion.div
              ref={dialectDropdownRef}
              initial={{ opacity: 0, y: 5 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 5 }}
              className={`absolute bottom-full right-0 mb-1.5 ${menuWidthClassMap.compact} bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg shadow-xl dark:shadow-2xl z-[100] p-0.5 overflow-hidden`}
            >
              {dialects.map(d => (
                <button
                  key={d.id}
                  onClick={() => { onDialectChange(d); setShowDialects(false) }}
                  className="flex items-center gap-2 w-full p-2 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded text-left transition-all group"
                >
                  <span className={`w-1 h-1 rounded-full ${d.color} bg-current transition-transform group-hover:scale-125`} />
                  <span className="text-micro font-semibold text-slate-600 dark:text-slate-300 uppercase tracking-tight">{d.name}</span>
                  {activeDialect.id === d.id && <Check size={8} className="ml-auto text-indigo-500 dark:text-indigo-400" />}
                </button>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
      <div className="flex items-center gap-1 text-tiny font-bold text-emerald-500 uppercase tracking-wider select-none">
        <Activity size={9} className="animate-pulse" /> Online
      </div>
    </>
  )

  return (
    <BaseBottomPanel
      tabs={BOTTOM_TABS}
      activeTabId={activeTab}
      onTabChange={handleTabChange}
      statusLeft={statusLeft}
      statusRight={statusRight}
      defaultCollapsed={true}
      collapsed={isCollapsed}
      onCollapsedChange={handleCollapsedChange}
    >
      {activeTab === 'results' ? (
        <ResultsTable executeResult={executeResult} />
      ) : (
        <MessagesLog logs={executionLogs} />
      )}
    </BaseBottomPanel>
  )
}

/** 结果表格组件 */
function ResultsTable({ executeResult }: { executeResult: ExecuteResult | null }) {
  if (!executeResult || !executeResult.columns || executeResult.columns.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-slate-400 dark:text-slate-500 text-micro">
        暂无数据
      </div>
    )
  }

  const { columns, rows = [] } = executeResult

  return (
    <div className="w-full">
      <table className="w-auto text-left border-collapse">
        <thead className="sticky top-0 bg-slate-50 dark:bg-slate-800 z-10 shadow-[0_1px_0_0_#e2e8f0] dark:shadow-[0_1px_0_0_#334155]">
          <tr>
            {columns.map((col) => (
              <th key={col.name} className="px-3 py-2 text-tiny font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider border-r border-slate-100 dark:border-slate-700 last:border-0 whitespace-nowrap">
                <div className="flex items-center justify-between gap-2">
                  <span>{col.name}</span>
                  <div className="flex items-center gap-0.5">
                    <button className="p-0.5 hover:bg-slate-200 dark:hover:bg-slate-700 rounded transition-colors opacity-50 hover:opacity-100">
                      <ArrowUpDown size={10} />
                    </button>
                    <button className="p-0.5 hover:bg-slate-200 dark:hover:bg-slate-700 rounded transition-colors opacity-50 hover:opacity-100">
                      <Filter size={10} />
                    </button>
                  </div>
                </div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} className={`hover:bg-slate-50 dark:hover:bg-slate-800 border-b border-slate-50 dark:border-slate-800 transition-colors ${i % 2 === 0 ? 'bg-transparent' : 'bg-slate-50/20 dark:bg-slate-800/30'}`}>
              {row.map((cell, j) => (
                <td key={j} className="px-3 py-1 text-micro font-medium text-slate-600 dark:text-slate-300 border-r border-slate-50/50 dark:border-slate-800/50 last:border-0 whitespace-nowrap">{cell == null ? '' : String(cell)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** 消息日志组件 */
function MessagesLog({ logs }: { logs: { time: string; msg: string; type: string }[] }) {
  if (logs.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-slate-400 dark:text-slate-500 text-micro">
        暂无日志
      </div>
    )
  }

  // 根据日志类型返回对应的样式类
  const getLogStyle = (type: string) => {
    switch (type) {
      case 'success':
        return 'text-emerald-600 dark:text-emerald-400'
      case 'error':
        return 'text-rose-600 dark:text-rose-400'
      case 'warning':
        return 'text-amber-600 dark:text-amber-400'
      case 'info':
      default:
        return 'text-sky-600 dark:text-sky-400'
    }
  }

  // 根据日志类型返回时间戳的样式
  const getTimeStyle = (type: string) => {
    switch (type) {
      case 'success':
        return 'text-emerald-400 dark:text-emerald-600'
      case 'error':
        return 'text-rose-400 dark:text-rose-600'
      case 'warning':
        return 'text-amber-400 dark:text-amber-600'
      case 'info':
      default:
        return 'text-sky-400 dark:text-sky-600'
    }
  }

  return (
    <div className="p-3 font-mono text-micro space-y-1">
      {logs.map((l, i) => (
        <div key={i} className="flex gap-2 hover:bg-slate-50 dark:hover:bg-slate-800 -mx-3 px-3 py-px transition-colors">
          <span className={`shrink-0 select-none ${getTimeStyle(l.type)}`}>[{l.time}]</span>
          <span className={`flex-1 break-all ${getLogStyle(l.type)}`}>{l.msg}</span>
        </div>
      ))}
    </div>
  )
}
