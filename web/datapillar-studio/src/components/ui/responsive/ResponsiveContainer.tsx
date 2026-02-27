/**
 * 响应式容器组件
 *
 * 提供自动的内边距、最大宽度、响应式布局
 * 替代手动处理 max-w-xxx、px-4 sm:px-6 等类名
 *
 * 使用方式：
 * ```tsx
 * <ResponsiveContainer>
 *   <h1>页面标题</h1>
 *   <p>页面内容自动居中对齐，左右自动适配内边距</p>
 * </ResponsiveContainer>
 * ```
 */

import type { ReactNode, HTMLAttributes } from 'react'
import { cn } from '@/utils'
import {
  contentMaxWidthClassMap,
  paddingClassMap,
  type ContentMaxWidth,
  type Padding
} from '@/design-tokens/dimensions'

type ResponsiveBreakpoint = 'md' | 'lg' | 'xl' | '2xl' | '3xl'

interface ResponsiveContainerProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
  /** 容器最大宽度（默认 normal，1024px）*/
  maxWidth?: ContentMaxWidth
  /** 是否居中对齐（默认 true）*/
  center?: boolean
  /** 是否添加响应式内边距（默认 true）*/
  padding?: boolean
  /** 内边距尺寸（默认 md）*/
  paddingScale?: Exclude<Padding, 'none'>
  /** 自定义类名 */
  className?: string
}

/**
 * 响应式容器
 * 自动提供最大宽度、居中对齐、响应式内边距
 */
export function ResponsiveContainer({
  children,
  maxWidth = 'normal',
  center = true,
  padding = true,
  paddingScale = 'md',
  className,
  ...props
}: ResponsiveContainerProps) {
  return (
    <div
      className={cn(
        'w-full',
        contentMaxWidthClassMap[maxWidth],
        center && 'mx-auto',
        padding && paddingClassMap[paddingScale],
        className
      )}
      {...props}
    >
      {children}
    </div>
  )
}

/**
 * 响应式页面容器
 * 适合作为页面根容器，提供全屏高度 + 滚动
 */
export function ResponsivePageContainer({
  children,
  className
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div
      className={cn(
        'min-h-dvh w-full overflow-y-auto custom-scrollbar',
        'bg-slate-50 dark:bg-[#020617]',
        className
      )}
    >
      {children}
    </div>
  )
}

/**
 * 响应式网格容器
 * 自动适配列数
 */
export function ResponsiveGrid({
  children,
  columns = { md: 2, lg: 3, xl: 4 },
  gap = 'md',
  className
}: {
  children: ReactNode
  /** 不同断点的列数 */
  columns?: Partial<Record<ResponsiveBreakpoint, number>>
  /** 间距大小 */
  gap?: 'sm' | 'md' | 'lg'
  className?: string
}) {
  const gapMap = {
    sm: 'gap-4',
    md: 'gap-5 lg:gap-6',
    lg: 'gap-6 lg:gap-8 xl:gap-10'
  }

  // 构建响应式列数类名
  const gridCols = [
    'grid-cols-1',
    ...Object.entries(columns).map(([bp, cols]) => `${bp}:grid-cols-${cols}`)
  ].join(' ')

  return (
    <div className={cn('grid w-full', gridCols, gapMap[gap], className)}>
      {children}
    </div>
  )
}

/**
 * 响应式 Flex 容器
 * 自动切换方向
 */
export function ResponsiveFlex({
  children,
  direction = { md: 'row' },
  gap = 'md',
  className
}: {
  children: ReactNode
  /** 不同断点的方向 */
  direction?: Partial<Record<ResponsiveBreakpoint, 'row' | 'col'>>
  /** 间距大小 */
  gap?: 'sm' | 'md' | 'lg'
  className?: string
}) {
  const gapMap = {
    sm: 'gap-3',
    md: 'gap-4 lg:gap-5',
    lg: 'gap-6 lg:gap-7 xl:gap-8'
  }

  // 构建响应式方向类名
  const flexDir = [
    'flex-col',
    ...Object.entries(direction).map(([bp, dir]) => `${bp}:${dir === 'row' ? 'flex-row' : 'flex-col'}`)
  ].join(' ')

  return <div className={cn('flex', flexDir, gapMap[gap], className)}>{children}</div>
}
