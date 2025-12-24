/**
 * 语义组件库侧边栏
 * 包含修饰符和单位两个分类
 * 支持折叠/展开，默认折叠
 */

import { useState } from 'react'
import { Filter, Scale, Plus, ChevronLeft, ChevronRight } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'

type TabType = 'MODIFIER' | 'UNIT'

/** 单位数据 */
const UNITS = [
  { symbol: '¥', name: '人民币', code: 'CURRENCY' },
  { symbol: '%', name: '百分比', code: 'RATIO' },
  { symbol: '人', name: '人数', code: 'COUNT' },
  { symbol: 's', name: '秒', code: 'TIME' },
  { symbol: '个', name: '个数', code: 'PIECE' },
  { symbol: '次', name: '次数', code: 'TIMES' }
]

/** 修饰符数据 */
const MODIFIERS = [
  { symbol: 'Σ', name: '累计', code: 'CUM' },
  { symbol: 'Δ', name: '同比', code: 'YOY' },
  { symbol: '环', name: '环比', code: 'MOM' },
  { symbol: '均', name: '平均', code: 'AVG' },
  { symbol: '最', name: '最大', code: 'MAX' },
  { symbol: '小', name: '最小', code: 'MIN' }
]

export function ComponentLibrarySidebar() {
  const [collapsed, setCollapsed] = useState(true)
  const [activeTab, setActiveTab] = useState<TabType>('UNIT')

  const items = activeTab === 'UNIT' ? UNITS : MODIFIERS
  const listTitle = activeTab === 'UNIT' ? '可用单位' : '可用修饰符'

  // 折叠状态
  if (collapsed) {
    return (
      <div className="w-12 border-l border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex flex-col items-center py-4">
        {/* 图标区域 */}
        <div className="flex-1 flex flex-col items-center gap-3">
          <button
            onClick={() => { setActiveTab('MODIFIER'); setCollapsed(false) }}
            className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 hover:text-slate-600 transition-colors"
            title="修饰符"
          >
            <Filter size={iconSizeToken.medium} />
          </button>
          <button
            onClick={() => { setActiveTab('UNIT'); setCollapsed(false) }}
            className="p-2 hover:bg-amber-50 dark:hover:bg-amber-900/30 rounded-lg text-amber-500 hover:text-amber-600 transition-colors"
            title="单位库"
          >
            <Scale size={iconSizeToken.medium} />
          </button>
        </div>
        {/* 底部展开按钮 */}
        <button
          onClick={() => setCollapsed(false)}
          className="p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 hover:text-slate-600 transition-colors"
          title="展开组件库"
        >
          <ChevronLeft size={iconSizeToken.medium} />
        </button>
      </div>
    )
  }

  return (
    <div className="w-64 border-l border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex flex-col overflow-hidden">
      {/* 标题 */}
      <div className="px-3 pt-3 pb-1.5">
        <h3 className="text-caption font-semibold text-slate-800 dark:text-slate-200">
          语义组件库 <span className="text-slate-400 font-normal text-micro">(COMPONENTS)</span>
        </h3>
      </div>

      {/* Tab 切换 */}
      <div className="px-3 py-1.5">
        <div className="flex bg-slate-100 dark:bg-slate-800 p-0.5 rounded-full">
          <button
            onClick={() => setActiveTab('MODIFIER')}
            className={`flex-1 flex items-center justify-center gap-1 px-2 py-1.5 rounded-full text-micro font-medium transition-all ${
              activeTab === 'MODIFIER'
                ? 'bg-white dark:bg-slate-700 text-slate-800 dark:text-white shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <Filter size={12} />
            修饰符
          </button>
          <button
            onClick={() => setActiveTab('UNIT')}
            className={`flex-1 flex items-center justify-center gap-1 px-2 py-1.5 rounded-full text-micro font-medium transition-all ${
              activeTab === 'UNIT'
                ? 'bg-amber-500 text-white shadow-sm'
                : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            <Scale size={12} />
            单位库
          </button>
        </div>
      </div>

      {/* 列表标题 */}
      <div className="px-3 py-1.5 flex items-center justify-between">
        <span className="text-micro text-slate-500">{listTitle}</span>
        <button className="p-0.5 text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded transition-colors">
          <Plus size={14} />
        </button>
      </div>

      {/* 列表 */}
      <div className="flex-1 overflow-y-auto px-3 pb-3 space-y-1.5 custom-scrollbar">
        {items.map((item) => (
          <div
            key={item.code}
            className="flex items-center gap-2.5 p-2.5 bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 rounded-lg hover:border-amber-200 dark:hover:border-amber-700 hover:shadow-sm transition-all cursor-pointer"
          >
            <div className="w-8 h-8 rounded-full bg-amber-50 dark:bg-amber-900/30 flex items-center justify-center text-amber-600 dark:text-amber-400 font-bold text-caption">
              {item.symbol}
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-caption font-medium text-slate-800 dark:text-slate-200 truncate">{item.name}</div>
              <div className="text-micro text-amber-600 dark:text-amber-400 font-medium truncate">{item.code}</div>
            </div>
          </div>
        ))}
      </div>

      {/* 底部区域：提示 + 折叠按钮 */}
      <div className="px-3 py-2 border-t border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-800/50 flex items-center justify-between gap-2">
        <p className="text-micro text-slate-400 leading-relaxed flex-1">
          组装派生指标前，请确保组件库中已有所需口径片段。
        </p>
        <button
          onClick={() => setCollapsed(true)}
          className="p-1.5 hover:bg-slate-200 dark:hover:bg-slate-700 rounded-lg text-slate-400 hover:text-slate-600 transition-colors flex-shrink-0"
          title="折叠"
        >
          <ChevronRight size={iconSizeToken.medium} />
        </button>
      </div>
    </div>
  )
}
