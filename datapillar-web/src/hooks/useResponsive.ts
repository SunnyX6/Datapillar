/**
 * 响应式断点检测 Hook
 *
 * 基于 PC 端断点（与 tailwind @theme 保持一致）：
 * - md: 1080px
 * - lg: 1440px
 * - xl: 1920px
 * - 2xl: 2560px
 * - 3xl: 3840px
 *
 * 使用方式：
 * ```tsx
 * const { isSmallScreen, isDesktop, breakpoint } = useResponsive()
 *
 * return (
 *   <div>
 *     {isMobile && <MobileView />}
 *     {isDesktop && <DesktopView />}
 *     <p>当前断点：{breakpoint}</p>
 *   </div>
 * )
 * ```
 */

import { useEffect, useState, useSyncExternalStore } from 'react'

export type Breakpoint = 'md' | 'lg' | 'xl' | '2xl' | '3xl'

/** Tailwind CSS 断点配置 */
export const BREAKPOINTS = {
  md: 1080,
  lg: 1440,
  xl: 1920,
  '2xl': 2560,
  '3xl': 3840
} as const

/**
 * 获取当前断点
 */
function getCurrentBreakpoint(): Breakpoint {
  if (typeof window === 'undefined') return 'lg' // SSR 默认值：1440 视口

  const width = window.innerWidth

  if (width >= BREAKPOINTS['3xl']) return '3xl'
  if (width >= BREAKPOINTS['2xl']) return '2xl'
  if (width >= BREAKPOINTS.xl) return 'xl'
  if (width >= BREAKPOINTS.lg) return 'lg'
  if (width >= BREAKPOINTS.md) return 'md'
  return 'md'
}

/**
 * 响应式断点检测 Hook
 */
export function useResponsive() {
  const [breakpoint, setBreakpoint] = useState<Breakpoint>(getCurrentBreakpoint)

  useEffect(() => {
    const handleResize = () => {
      setBreakpoint(getCurrentBreakpoint())
    }

    // 使用 ResizeObserver 监听窗口变化（性能更好）
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(handleResize)
      observer.observe(document.body)
      return () => observer.disconnect()
    }

    // 降级方案：使用 resize 事件
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  return {
    /** 当前断点 */
    breakpoint,

    /** 是否为最小屏（< 1440px，对应 md） */
    isSmallScreen: breakpoint === 'md',

    /** 是否为桌面端（>= 1440px）*/
    isDesktop: breakpoint === 'lg' || breakpoint === 'xl' || breakpoint === '2xl' || breakpoint === '3xl',

    /** 是否为大屏幕（>= 1920px）*/
    isLargeScreen: breakpoint === 'xl' || breakpoint === '2xl' || breakpoint === '3xl',

    /** 当前屏幕宽度 */
    width: typeof window !== 'undefined' ? window.innerWidth : 1024
  }
}

/**
 * 监听特定断点的 Hook
 *
 * 使用方式：
 * ```tsx
 * const isDesktop = useBreakpoint('lg') // >= 1440px 时为 true
 * const isSmall = useBreakpoint('md', 'max') // < 1080px 时为 true
 * ```
 */
export function useBreakpoint(
  breakpoint: keyof typeof BREAKPOINTS,
  type: 'min' | 'max' = 'min'
): boolean {
  const query = type === 'min'
    ? `(min-width: ${BREAKPOINTS[breakpoint]}px)`
    : `(max-width: ${BREAKPOINTS[breakpoint] - 1}px)`

  const [matches, setMatches] = useState(() => {
    if (typeof window === 'undefined') return false
    return window.matchMedia(query).matches
  })

  useEffect(() => {
    const mediaQuery = window.matchMedia(query)
    const handleChange = (e: MediaQueryListEvent) => setMatches(e.matches)

    // 兼容旧版浏览器
    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', handleChange)
      return () => mediaQuery.removeEventListener('change', handleChange)
    } else {
      // @ts-expect-error - 兼容旧版 API
      mediaQuery.addListener(handleChange)
      // @ts-expect-error - 兼容旧版 API
      return () => mediaQuery.removeListener(handleChange)
    }
  }, [query])

  return matches
}

/**
 * 使用 useSyncExternalStore 的断点检测（React 18+）
 * 性能更好，支持 SSR
 */
const listeners: Set<() => void> = new Set()
let currentBreakpoint: Breakpoint = 'lg'

if (typeof window !== 'undefined') {
  currentBreakpoint = getCurrentBreakpoint()

  const handleResize = () => {
    const newBreakpoint = getCurrentBreakpoint()
    if (newBreakpoint !== currentBreakpoint) {
      currentBreakpoint = newBreakpoint
      listeners.forEach(listener => listener())
    }
  }

  window.addEventListener('resize', handleResize)
}

/**
 * 使用 useSyncExternalStore 的响应式 Hook（推荐）
 * 相比 useResponsive，性能更好，支持 SSR
 */
export function useBreakpointSync() {
  return useSyncExternalStore(
    (callback) => {
      listeners.add(callback)
      return () => listeners.delete(callback)
    },
    () => currentBreakpoint,
    () => 'lg' // SSR 默认值
  )
}

/**
 * 动态尺寸计算工具
 *
 * 使用方式：
 * ```tsx
 * const { getDimension } = useResponsiveDimension()
 *
 * <div style={{ width: getDimension({ xs: '100%', lg: '320px' }) }} />
 * ```
 */
export function useResponsiveDimension() {
  const { breakpoint } = useResponsive()

  return {
    /**
     * 根据断点返回对应的尺寸值
     */
    getDimension: (dimensions: Partial<Record<Breakpoint, string | number>>) => {
      // 从当前断点向下查找最近的定义值
      const breakpoints: Breakpoint[] = ['3xl', '2xl', 'xl', 'lg', 'md']
      const currentIndex = breakpoints.indexOf(breakpoint)

      for (let i = currentIndex; i < breakpoints.length; i++) {
        const bp = breakpoints[i]
        if (dimensions[bp] !== undefined) {
          return dimensions[bp]
        }
      }

      // 如果没有找到，返回最小断点的值
      return dimensions.md || '100%'
    }
  }
}
