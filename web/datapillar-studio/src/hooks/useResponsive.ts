/**
 * Responsive breakpoint detection Hook
 *
 * Based on @theme breakpoint variable in（src/index.css）
 *
 * Usage：
 * ```tsx
 * const { isSmallScreen, isDesktop, breakpoint } = useResponsive()
 *
 * return (
 *   <div>
 *     {isMobile && <MobileView />}
 *     {isDesktop && <DesktopView />}
 *     <p>current breakpoint：{breakpoint}</p>
 *   </div>
 * )
 * ```
 */

import { useEffect, useState, useSyncExternalStore } from 'react'
import { BREAKPOINT_ORDER, getBreakpoints, getBreakpointValue, type BreakpointKey } from '@/design-tokens/breakpoints'

export type Breakpoint = BreakpointKey

/**
 * Get the current breakpoint
 */
function getCurrentBreakpoint(): Breakpoint {
  if (typeof window === 'undefined') return 'lg' // SSR Default value：lg breakpoint

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
 * Responsive breakpoint detection Hook
 */
export function useResponsive() {
  const [breakpoint, setBreakpoint] = useState<Breakpoint>(getCurrentBreakpoint)

  useEffect(() => {
    const handleResize = () => {
      setBreakpoint(getCurrentBreakpoint())
    }

    // use ResizeObserver Listen for window changes（Better performance）
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(handleResize)
      observer.observe(document.body)
      return () => observer.disconnect()
    }

    // Downgrade plan：use resize event
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  return {
    /** current breakpoint */
    breakpoint,

    /** Whether it is the smallest screen（< 1440px，Correspond md） */
    isSmallScreen: breakpoint === 'md',

    /** Is it a desktop version?（>= 1440px）*/
    isDesktop: breakpoint === 'lg' || breakpoint === 'xl' || breakpoint === '2xl' || breakpoint === '3xl',

    /** Is it a large screen?（>= 1920px）*/
    isLargeScreen: breakpoint === 'xl' || breakpoint === '2xl' || breakpoint === '3xl',

    /** Current screen width */
    width: typeof window !== 'undefined' ? window.innerWidth : 1024
  }
}

/**
 * Listen to specific breakpoints Hook
 *
 * Usage：
 * ```tsx
 * const isDesktop = useBreakpoint('lg') // >= lg The breakpoint is true
 * const isSmall = useBreakpoint('md', 'max') // < md The breakpoint is true
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

    // Compatible with older browsers
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
 * use useSyncExternalStore breakpoint detection（React 18+）
 * Better performance，support SSR
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
 * use useSyncExternalStore responsive Hook（Recommended）
 * compared to useResponsive，Better performance，support SSR
 */
export function useBreakpointSync() {
  return useSyncExternalStore(
    (callback) => {
      listeners.add(callback)
      return () => listeners.delete(callback)
    },
    () => currentBreakpoint,
    () => 'lg' // SSR Default value
  )
}

/**
 * Dynamic size calculation tool
 *
 * Usage：
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
     * Return the corresponding size value based on the breakpoint
     */
    getDimension: (dimensions: Partial<Record<Breakpoint, string | number>>) => {
      // Find the most recently defined value from the current breakpoint downwards
      const currentIndex = BREAKPOINT_ORDER.indexOf(breakpoint)

      for (let i = currentIndex; i < BREAKPOINT_ORDER.length; i++) {
        const bp = BREAKPOINT_ORDER[i]
        if (dimensions[bp] !== undefined) {
          return dimensions[bp]
        }
      }

      // if not found，Returns the value of the minimum breakpoint
      return dimensions.md || '100%'
    }
  }
}
