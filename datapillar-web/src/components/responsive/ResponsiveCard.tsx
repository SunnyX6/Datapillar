/**
 * 响应式卡片组件
 *
 * 自动适配不同屏幕尺寸，无需手动处理响应式
 *
 * 使用方式：
 * ```tsx
 * <ResponsiveCard size="normal">
 *   <ResponsiveCard.Header>标题</ResponsiveCard.Header>
 *   <ResponsiveCard.Body>内容</ResponsiveCard.Body>
 *   <ResponsiveCard.Footer>底部</ResponsiveCard.Footer>
 * </ResponsiveCard>
 * ```
 */

import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { cardWidthClassMap, type CardWidth } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'

type CardSize = CardWidth | 'responsive'

interface ResponsiveCardProps {
  children: ReactNode
  /** 卡片尺寸（默认 responsive，自动适配）*/
  size?: CardSize
  /** 自定义类名 */
  className?: string
  /** 是否显示阴影 */
  shadow?: boolean
  /** 是否显示边框 */
  border?: boolean
  /** 内边距大小 */
  padding?: 'none' | 'sm' | 'md' | 'lg'
}

const paddingMap = {
  none: '',
  sm: 'p-4 @md:p-5',
  md: 'p-5 @md:p-6 @lg:p-8',
  lg: 'p-6 @md:p-8 @lg:p-10'
}

const cardSizeClassMap: Record<CardSize, string> = {
  responsive: `${cardWidthClassMap.normal} lg:max-w-2xl`,
  narrow: cardWidthClassMap.narrow,
  normal: cardWidthClassMap.normal,
  medium: cardWidthClassMap.medium,
  wide: cardWidthClassMap.wide,
  extraWide: cardWidthClassMap.extraWide
}

export function ResponsiveCard({
  children,
  size = 'responsive',
  className,
  shadow = true,
  border = true,
  padding = 'md'
}: ResponsiveCardProps) {
  return (
    <div
      className={cn(
        'rounded-xl bg-white dark:bg-[#1e293b] transition-all',
        cardSizeClassMap[size],
        padding === 'none' ? '' : paddingMap[padding],
        shadow && 'shadow-sm hover:shadow-lg dark:shadow-black/20',
        border && 'border border-slate-200 dark:border-slate-700/60',
        className
      )}
    >
      {children}
    </div>
  )
}

/** 卡片头部 */
ResponsiveCard.Header = function ResponsiveCardHeader({
  children,
  className
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div
      className={cn(
        'mb-4 pb-4 border-b border-slate-200 dark:border-slate-700',
        TYPOGRAPHY.heading,
        'text-slate-900 dark:text-white',
        className
      )}
    >
      {children}
    </div>
  )
}

/** 卡片主体 */
ResponsiveCard.Body = function ResponsiveCardBody({
  children,
  className
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div className={cn(TYPOGRAPHY.body, 'text-slate-700 dark:text-slate-300', className)}>
      {children}
    </div>
  )
}

/** 卡片底部 */
ResponsiveCard.Footer = function ResponsiveCardFooter({
  children,
  className
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div
      className={cn(
        'mt-4 pt-4 border-t border-slate-200 dark:border-slate-700 flex items-center justify-end gap-2',
        className
      )}
    >
      {children}
    </div>
  )
}
