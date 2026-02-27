import type { ReactNode } from 'react'
import { cn } from '@/utils'
import {
  paddingClassMap as layoutPaddingClassMap,
  contentMaxWidthClassMap,
  type Padding as PaddingScale
} from '@/design-tokens/dimensions'

type Surface = 'light' | 'dark'
type Align = 'start' | 'center' | 'stretch'

interface AppLayoutProps {
  children: ReactNode
  surface?: Surface
  padding?: PaddingScale
  maxWidthClassName?: string
  align?: Align
  scrollBehavior?: 'auto' | 'hidden'
  scrollClassName?: string
  className?: string
  contentClassName?: string
}

const surfaceClassMap: Record<Surface, string> = {
  light: 'bg-slate-50 text-slate-900 dark:bg-[#020617] dark:text-white',
  dark: 'bg-[#020617] text-white'
}

const alignClassMap: Record<Align, string> = {
  start: 'items-start',
  center: 'items-center',
  stretch: 'items-stretch'
}

/**
 * AppLayout: 基础页面容器
 *
 * - `surface` 控制背景/文字色
 * - `padding` / `maxWidthClassName` / `align` 用来快速调节布局
 * - 默认提供滚动容器，可通过 `scrollBehavior=\"hidden\"` 禁用
 */
export function AppLayout({
  children,
  surface = 'light',
  padding = 'md',
  maxWidthClassName = contentMaxWidthClassMap.extraWide,
  align = 'start',
  scrollBehavior = 'auto',
  scrollClassName,
  className,
  contentClassName
}: AppLayoutProps) {
  return (
    <div
      className={cn(
        'relative min-h-dvh w-full font-sans',
        surfaceClassMap[surface],
        className
      )}
    >
      <main
        className={cn(
          'flex min-h-dvh w-full flex-col',
          scrollBehavior === 'auto'
            ? 'overflow-y-auto custom-scrollbar'
            : 'overflow-hidden',
          scrollClassName
        )}
      >
        <div
          className={cn(
            'flex w-full flex-1 flex-col',
            alignClassMap[align],
            maxWidthClassName,
            layoutPaddingClassMap[padding],
            contentClassName,
            'mx-auto'
          )}
        >
          {children}
        </div>
      </main>
    </div>
  )
}
