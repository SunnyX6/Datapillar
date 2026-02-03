import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

type GridVariant = 'dashboard' | 'two-column' | 'three-column'
type GapScale = 'none' | 'xs' | 'sm' | 'md' | 'lg'

const variantClassMap: Record<GridVariant, string> = {
  dashboard: 'grid-cols-1 @md:grid-cols-2 @lg:grid-cols-4',
  'two-column': 'grid-cols-1 @md:grid-cols-2 @lg:grid-cols-[1.3fr_0.7fr]',
  'three-column': 'grid-cols-1 @lg:grid-cols-3'
}

const gapClassMap: Record<GapScale, string> = {
  none: 'gap-0',
  xs: 'gap-3',
  sm: 'gap-4',
  md: 'gap-6',
  lg: 'gap-8'
}

interface AdaptiveGridProps extends HTMLAttributes<HTMLDivElement> {
  variant?: GridVariant
  gap?: GapScale
}

/**
 * AdaptiveGrid：Tailwind 原子拼出的参数化栅格
 *
 * - variant 定义断点组合
 * - gap 可独立控制
 */
export function AdaptiveGrid({
  variant = 'dashboard',
  gap = 'md',
  className,
  children,
  ...rest
}: AdaptiveGridProps) {
  return (
    <div
      {...rest}
      className={cn(
        'grid w-full @container',
        variantClassMap[variant],
        gapClassMap[gap],
        className
      )}
    >
      {children}
    </div>
  )
}
