/**
 * 响应式断点检测 Hook
 *
 * 基于 @theme 中的断点变量（src/index.css）
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
import { BREAKPOINT_ORDER, getBreakpoints, getBreakpointValue, type BreakpointKey } from '@/design-tokens/breakpoints'

export type Breakpoint = BreakpointKey

/**
 * 获取当前断点
 */
function getCurrentBreakpoint(): Breakpoint {
  if (typeof window === 'undefined') return 'lg' // SSR 默认值：lg 断点

  const width = window.innerWidth
  const breakpoints = getBreakpoints()

  if (width >= breakpoints['3xl']) return '3xl'
  if (width >= breakpoints['2xl']) return '2xl'
  if (width >= breakpoints.xl) return 'xl'
  if (width >= breakpoints.lg) return 'lg'
  if (width >= breakpoints.md) return 'md'
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
 * const isDesktop = useBreakpoint('lg') // >= lg 断点时为 true
 * const isSmall = useBreakpoint('md', 'max') // < md 断点时为 true
 * ```
 */
export function useBreakpoint(
  breakpoint: Breakpoint,
  type: 'min' | 'max' = 'min'
): boolean {
  const breakpointValue = getBreakpointValue(breakpoint)
  const query = type === 'min'
    ? `(min-width: ${breakpointValue}px)`
    : `(max-width: ${breakpointValue - 1}px)`

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
      mediaQuery.addListener(handleChange)
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
 * <div style={{ width: getDimension({ md: '100%', lg: '320px' }) }} />
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
      const currentIndex = BREAKPOINT_ORDER.indexOf(breakpoint)

      for (let i = currentIndex; i < BREAKPOINT_ORDER.length; i++) {
        const bp = BREAKPOINT_ORDER[i]
        if (dimensions[bp] !== undefined) {
          return dimensions[bp]
        }
      }

      // 如果没有找到，返回最小断点的值
      return dimensions.md || '100%'
    }
  }
}
