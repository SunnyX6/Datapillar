/**
 * 通用卡片组件（以「元语义首页」卡片为基准）
 *
 * 设计目标：
 * - 统一卡片容器的背景/边框/圆角/hover 交互
 * - 通过 padding 选项覆盖不同内容密度场景
 */

import type { HTMLAttributes, ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { cardWidthClassMap, type CardWidth } from '@/design-tokens/dimensions'

type CardVariant = 'default' | 'interactive'

type CardPadding = 'none' | 'sm' | 'md' | 'lg'

const cardVariantClassMap: Record<CardVariant, string> = {
  default: 'bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800',
  // 元语义首页（HubAssetCard）同款交互：hover 阴影 + 蓝色描边
  interactive:
    'bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 hover:shadow-lg hover:border-blue-300 dark:hover:border-blue-600'
}

const cardPaddingClassMap: Record<CardPadding, string> = {
  none: '',
  sm: 'p-4',
  md: 'p-4 @md:p-6',
  lg: 'p-6 @md:p-8'
}

export interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'children'> {
  children: ReactNode
  /** 卡片最大宽度（使用 design-tokens/dimensions 中的 cardWidthClassMap） */
  size?: CardWidth
  /** 默认 interactive：与元语义首页卡片一致 */
  variant?: CardVariant
  /** 默认 md：p-4 @md:p-6（与元语义首页卡片一致） */
  padding?: CardPadding
}

export function Card({ children, className, size, variant = 'interactive', padding = 'md', ...props }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-xl @md:rounded-2xl transition-all',
        size ? cardWidthClassMap[size] : null,
        cardVariantClassMap[variant],
        cardPaddingClassMap[padding],
        className
      )}
      {...props}
    >
      {children}
    </div>
  )
}
