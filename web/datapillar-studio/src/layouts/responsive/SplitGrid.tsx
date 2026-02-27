import type { CSSProperties, ReactNode } from 'react'
import { cn } from '@/utils'

type Breakpoint = 'md' | 'lg' | 'xl' | 'never'
type GapScale = 'none' | 'xs' | 'sm' | 'md' | 'lg'

interface SplitGridProps {
  left: ReactNode
  right: ReactNode
  columns?: [string | number, string | number]
  stackAt?: Breakpoint
  minHeight?: 'none' | 'screen'
  gapX?: GapScale
  gapY?: GapScale
  className?: string
  leftClassName?: string
  rightClassName?: string
}

const breakpointTemplateClassMap: Record<Breakpoint, string> = {
  md: '@md:[grid-template-columns:var(--split-columns)]',
  lg: '@lg:[grid-template-columns:var(--split-columns)]',
  xl: '@xl:[grid-template-columns:var(--split-columns)]',
  never: '[grid-template-columns:var(--split-columns)]'
}

const breakpointRowClassMap: Record<Breakpoint, string> = {
  md: '@md:grid-rows-1',
  lg: '@lg:grid-rows-1',
  xl: '@xl:grid-rows-1',
  never: 'grid-rows-1'
}

const gapXClassMap: Record<GapScale, string> = {
  none: 'gap-x-0',
  xs: 'gap-x-3',
  sm: 'gap-x-4',
  md: 'gap-x-6',
  lg: 'gap-x-8'
}

const gapYClassMap: Record<GapScale, string> = {
  none: 'gap-y-0',
  xs: 'gap-y-3',
  sm: 'gap-y-4',
  md: 'gap-y-6',
  lg: 'gap-y-10'
}

const formatColumn = (value: string | number) => {
  if (typeof value === 'number') {
    return `${value}fr`
  }
  return value
}

/**
 * SplitGrid：可配置的双列自适应容器
 *
 * - 默认移动端堆叠，`stackAt` 控制切换断点
 * - `columns` 定义桌面端列宽，可以是百分比/`fr`/`px`
 * - `gapX`/`gapY` 统一使用 Tailwind 间距刻度
 */
export function SplitGrid({
  left,
  right,
  columns = ['60%', '40%'],
  stackAt = 'lg',
  minHeight = 'screen',
  gapX = 'md',
  gapY = 'md',
  className,
  leftClassName,
  rightClassName
}: SplitGridProps) {
  const template = columns.map(formatColumn).join(' ')

  return (
    <div
      style={{ '--split-columns': template } as CSSProperties}
      className={cn(
        'grid @container',
        stackAt === 'never' ? '' : 'grid-cols-1',
        minHeight === 'screen' ? 'min-h-dvh' : '',
        gapXClassMap[gapX],
        gapYClassMap[gapY],
        breakpointRowClassMap[stackAt],
        breakpointTemplateClassMap[stackAt],
        className
      )}
    >
      <section
        className={cn(
          'relative flex w-full flex-col',
          minHeight === 'screen' ? 'min-h-full' : '',
          leftClassName
        )}
      >
        {left}
      </section>
      <section
        className={cn(
          'relative flex w-full flex-col',
          minHeight === 'screen' ? 'min-h-full' : '',
          rightClassName
        )}
      >
        {right}
      </section>
    </div>
  )
}
