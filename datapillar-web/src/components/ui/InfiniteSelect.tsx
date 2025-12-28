/**
 * 无限滚动下拉选择器
 * 支持懒加载、分页滚动加载、最大高度限制
 */

import { useState, useRef, useEffect, useLayoutEffect, useCallback, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { ChevronDown, Loader2 } from 'lucide-react'

/** 默认每页加载数量 */
const DEFAULT_PAGE_SIZE = 8

export interface InfiniteSelectItem {
  key: string
  code: string
  name: string
  icon?: ReactNode
}

export interface InfiniteSelectProps {
  /** 触发按钮文案 */
  placeholder: string
  /** 颜色主题 */
  variant?: 'blue' | 'purple' | 'slate' | 'emerald'
  /** 左侧图标 */
  icon?: ReactNode
  /** 已选中的值（用于过滤不显示） */
  selectedKeys?: string[]
  /** 初始数据（已缓存时可避免首屏 loading） */
  initialItems?: InfiniteSelectItem[]
  /** 初始总数 */
  initialTotal?: number
  /** 触发按钮额外 class，便于对齐底线 */
  triggerClassName?: string
  /** 获取数据函数 */
  fetchData: (offset: number, limit: number) => Promise<{ items: InfiniteSelectItem[]; total: number }>
  /** 选择回调 */
  onSelect: (item: InfiniteSelectItem) => void
  /** 每页加载数量 */
  pageSize?: number
}

/** 颜色主题映射 */
const variantStyles = {
  blue: {
    trigger: 'border-blue-400 text-blue-500',
    icon: 'text-blue-400'
  },
  purple: {
    trigger: 'border-purple-400 text-purple-500',
    icon: 'text-purple-400'
  },
  slate: {
    trigger: 'border-slate-400 text-slate-500',
    icon: 'text-slate-400'
  },
  emerald: {
    trigger: 'border-emerald-400 text-emerald-500',
    icon: 'text-emerald-400'
  }
}

export function InfiniteSelect({
  placeholder,
  variant = 'slate',
  icon: _icon,
  selectedKeys = [],
  initialItems = [],
  initialTotal,
  triggerClassName = '',
  fetchData,
  onSelect,
  pageSize = DEFAULT_PAGE_SIZE
}: InfiniteSelectProps) {
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<InfiniteSelectItem[]>(initialItems)
  const [total, setTotal] = useState(initialTotal ?? initialItems.length ?? 0)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [initialized, setInitialized] = useState(initialItems.length > 0)

  const triggerRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number } | null>(null)

  const styles = variantStyles[variant]
  const hasMore = items.length < total
  const filteredItems = items.filter((item) => !selectedKeys.includes(item.key))

  // 如果外部后来传入了缓存数据，优先用它填充，跳过首屏 loading
  useEffect(() => {
    if (initialized) return
    if (initialItems.length > 0) {
      setItems(initialItems)
      setTotal(initialTotal ?? initialItems.length)
      setInitialized(true)
    }
  }, [initialItems, initialTotal, initialized])

  // 计算下拉框位置
  const updatePosition = useCallback(() => {
    const btn = triggerRef.current
    if (!btn) return
    const rect = btn.getBoundingClientRect()
    setDropdownPos({
      top: rect.bottom + 4,
      left: rect.left
    })
  }, [])

  // 加载首页数据
  const loadInitial = useCallback(async () => {
    if (initialized || loading) return
    setLoading(true)
    try {
      const result = await fetchData(0, pageSize)
      setItems(result.items)
      setTotal(result.total)
    } catch {
      setItems([])
      setTotal(0)
    } finally {
      setInitialized(true)
      setLoading(false)
    }
  }, [fetchData, pageSize, initialized, loading])

  // 加载更多数据
  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return
    setLoadingMore(true)
    try {
      const result = await fetchData(items.length, pageSize)
      setItems((prev) => [...prev, ...result.items])
      setTotal(result.total)
    } catch {
      // 加载失败
    } finally {
      setLoadingMore(false)
    }
  }, [fetchData, pageSize, items.length, hasMore, loadingMore])

  // 打开下拉框 - 只管 UI，不管数据
  const handleOpen = () => {
    if (open) {
      setOpen(false)
    } else {
      updatePosition()
      setOpen(true)
    }
  }

  // UI 打开后再加载数据
  useEffect(() => {
    if (open && !initialized && !loading) {
      loadInitial()
    }
  }, [open, initialized, loading, loadInitial])

  // 点击外部关闭
  useEffect(() => {
    if (!open) return
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as Node
      if (triggerRef.current?.contains(target)) return
      if (dropdownRef.current?.contains(target)) return
      setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  // 监听窗口变化更新位置
  useLayoutEffect(() => {
    if (!open) return
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [open, updatePosition])

  // 滚动加载更多
  useEffect(() => {
    if (!open || !listRef.current) return
    const list = listRef.current
    const handleScroll = () => {
      if (list.scrollTop + list.clientHeight >= list.scrollHeight - 20) {
        loadMore()
      }
    }
    list.addEventListener('scroll', handleScroll)
    return () => list.removeEventListener('scroll', handleScroll)
  }, [open, loadMore])

  const handleSelect = (item: InfiniteSelectItem) => {
    onSelect(item)
    setOpen(false)
  }

  // 判断是否显示 loading
  const showLoading = !initialized || loading

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        onClick={handleOpen}
        className={`shrink-0 inline-flex items-center gap-1 bg-transparent border-b border-dashed ${styles.trigger} text-caption px-1 py-0.5 ${triggerClassName}`}
      >
        <span>{placeholder}</span>
        <ChevronDown size={12} className={open ? 'rotate-180' : ''} />
      </button>

      {open && dropdownPos && createPortal(
        <div
          ref={dropdownRef}
          style={{ top: dropdownPos.top, left: dropdownPos.left }}
          className="fixed z-[1000000] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl"
        >
          <div
            ref={listRef}
            className="overflow-y-auto p-1 max-h-60 min-w-40"
          >
            {showLoading ? (
              <div className="flex items-center justify-center py-4">
                <Loader2 size={16} className="animate-spin text-slate-400" />
              </div>
            ) : filteredItems.length === 0 ? (
              <div className="py-4 text-center text-caption text-slate-400">暂无数据</div>
            ) : (
              <>
                {filteredItems.map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    onClick={() => handleSelect(item)}
                    className="w-full flex items-center justify-between gap-2 px-2.5 py-1.5 rounded-lg text-left hover:bg-slate-50 dark:hover:bg-slate-800"
                  >
                    <span className="flex items-center gap-1.5">
                      {item.icon && <span className={styles.icon}>{item.icon}</span>}
                      <span className="text-caption text-slate-500 font-mono">{item.code}</span>
                    </span>
                    <span className="text-caption text-slate-600 dark:text-slate-400 truncate">{item.name}</span>
                  </button>
                ))}
                {loadingMore && (
                  <div className="flex items-center justify-center py-2">
                    <Loader2 size={14} className="animate-spin text-slate-400" />
                  </div>
                )}
                {!hasMore && items.length > pageSize && (
                  <div className="py-1.5 text-center text-micro text-slate-300">已加载全部</div>
                )}
              </>
            )}
          </div>
        </div>,
        document.body
      )}
    </>
  )
}
