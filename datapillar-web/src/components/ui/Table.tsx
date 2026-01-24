/**
 * 通用表格组件（以「指标中心」列表样式为基准）
 *
 * 设计目标：
 * - 提供统一的表格容器/表头默认样式
 * - Header/Row 由使用者完全自定义（组合式）
 */

import type { HTMLAttributes, ReactNode, ThHTMLAttributes, TdHTMLAttributes } from 'react'
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useInfiniteScroll } from '@/hooks'

type TableLayout = 'fixed' | 'auto'
type TableMinWidth = 'none' | 'wide'

const tableLayoutClassMap: Record<TableLayout, string> = {
  fixed: 'table-fixed',
  auto: 'table-auto'
}

const tableMinWidthClassMap: Record<TableMinWidth, string> = {
  none: '',
  wide: 'min-w-table-wide'
}

export interface TableInfiniteScrollProps {
  /** 是否还有更多数据 */
  hasMore: boolean
  /** 是否正在加载 */
  loading: boolean
  /** 触发加载更多 */
  onLoadMore: () => void
  /** 触发阈值：距离底部多少像素时触发（默认 300） */
  threshold?: number
  /** 自动填充：首屏不足以产生滚动条时，自动连续加载（默认 true） */
  autoFill?: boolean
  /** 动态阈值：至少按 1 屏高度预取（默认 true） */
  dynamicThreshold?: boolean
}

function TableInfiniteFooter({
  hasMore,
  loading,
  onLoadMore,
  threshold = 300,
  autoFill = true,
  dynamicThreshold = true
}: TableInfiniteScrollProps) {
  const { sentinelRef } = useInfiniteScroll({
    hasMore,
    loading,
    onLoadMore,
    threshold,
    autoFill,
    dynamicThreshold
  })

  const statusNode = loading ? (
    <>
      <Loader2 className="w-5 h-5 animate-spin text-blue-500" />
      <span>加载中...</span>
    </>
  ) : hasMore ? null : (
    <span>已加载全部</span>
  )

  return (
    <>
      <div ref={sentinelRef} className="h-1" />
      {statusNode && (
        <div className="flex justify-center items-center gap-2 py-3 border-t border-slate-100 dark:border-slate-800 text-caption text-slate-400 dark:text-slate-500 bg-white/90 dark:bg-slate-900/90 backdrop-blur-sm">
          {statusNode}
        </div>
      )}
    </>
  )
}

export interface TableProps extends Omit<HTMLAttributes<HTMLDivElement>, 'children'> {
  children: ReactNode
  /** <table> 元素的 className */
  tableClassName?: string
  /** 表格布局（默认 fixed，与指标中心一致） */
  layout?: TableLayout
  /** 最小宽度预设（默认 wide，对应 .min-w-table-wide） */
  minWidth?: TableMinWidth
  /** 表格下方附加内容（如：哨兵元素、加载更多） */
  footer?: ReactNode
  /** 无限向下滚动分页：Table 负责渲染哨兵 + loading，数据请求由业务组件负责 */
  infiniteScroll?: TableInfiniteScrollProps
}

export function Table({
  children,
  className,
  tableClassName,
  layout = 'fixed',
  minWidth = 'wide',
  footer,
  infiniteScroll,
  ...props
}: TableProps) {
  return (
    <div
      className={cn(
        'bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-sm',
        className
      )}
      {...props}
    >
      <div className="overflow-hidden rounded-xl">
        <div className="overflow-x-auto">
          <table
            className={cn(
              'w-full text-left border-collapse',
              tableLayoutClassMap[layout],
              tableMinWidthClassMap[minWidth],
              tableClassName
            )}
          >
            {children}
          </table>
        </div>
      </div>
      {footer}
      {infiniteScroll && <TableInfiniteFooter {...infiniteScroll} />}
    </div>
  )
}

export function TableHeader({
  children,
  className,
  ...props
}: HTMLAttributes<HTMLTableSectionElement> & { children: ReactNode }) {
  return (
    <thead
      className={cn(
        'bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-800 text-slate-400 font-semibold text-micro uppercase tracking-wider',
        className
      )}
      {...props}
    >
      {children}
    </thead>
  )
}

export function TableBody({
  children,
  className,
  ...props
}: HTMLAttributes<HTMLTableSectionElement> & { children: ReactNode }) {
  return (
    <tbody className={cn(className)} {...props}>
      {children}
    </tbody>
  )
}

export function TableRow({
  children,
  className,
  ...props
}: HTMLAttributes<HTMLTableRowElement> & { children: ReactNode }) {
  return (
    <tr className={cn(className)} {...props}>
      {children}
    </tr>
  )
}

export function TableHead({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return <th className={cn('px-4 py-3 align-middle', className)} {...props} />
}

export function TableCell({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('px-4 py-3 align-middle', className)} {...props} />
}
