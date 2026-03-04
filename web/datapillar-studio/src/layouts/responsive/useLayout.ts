import { useDeferredValue, useEffect, useLayoutEffect, useRef, useState, type RefObject } from 'react'

interface LayoutOptions {
  baseWidth: number
  baseHeight?: number
  scaleFactor?: number
  minScale?: number
  maxScale?: number
}

interface LayoutResult<T extends HTMLElement> {
  ref: RefObject<T>
  /** Delayed scaling of updates（Performance optimization，Recommended） */
  scale: number
  /** Delayed update width（Performance optimization，Recommended） */
  width: number
  /** Delayed update height（Performance optimization，Recommended） */
  height: number
  ready: boolean
  /** Real-time scaling（Used for scenarios requiring immediate response） */
  immediateScale: number
}

/**
 * useLayout：Monitor container size and return proportional scaling information
 *
 * - According to baseWidth/baseHeight Calculate scaling
 * - scaleFactor Used to adjust the overall size at one time
 * - use React 19 useDeferredValue Optimize performance，avoid high frequency resize Block user interaction
 *
 * @see https://react.dev/reference/react/useDeferredValue
 */
const useIsomorphicLayoutEffect = typeof window !== 'undefined' ? useLayoutEffect : useEffect

export function useLayout<T extends HTMLElement>({
  baseWidth,
  baseHeight,
  scaleFactor = 1,
  minScale,
  maxScale = 1.2
}: LayoutOptions): LayoutResult<T> {
  const containerRef = useRef<T | null>(null)
  const [immediateState, setImmediateState] = useState(() => ({
    width: baseWidth,
    height: baseHeight ?? baseWidth,
    scale: 1,
    ready: false
  }))

  // React 19 useDeferredValue：Automatically defer low priority updates，Adapt according to device performance
  const deferredState = useDeferredValue(immediateState)

  useIsomorphicLayoutEffect(() => {
    const element = containerRef.current
    if (!element) {
      return
    }

    const updateState = (width: number, height: number) => {
      const widthRatio = width / baseWidth
      const heightRatio = baseHeight ? height / baseHeight : widthRatio
      let nextScale = Math.min(widthRatio, heightRatio) * scaleFactor

      if (typeof minScale === 'number') {
        nextScale = Math.max(minScale, nextScale)
      }

      if (typeof maxScale === 'number') {
        nextScale = Math.min(maxScale, nextScale)
      }

      setImmediateState({
        width,
        height,
        scale: Number(nextScale.toFixed(4)),
        ready: true
      })
    }

    const measure = () => {
      const rect = element.getBoundingClientRect()
      updateState(rect.width, rect.height)
    }

    measure()

    if (typeof ResizeObserver === 'undefined') {
      if (typeof window === 'undefined') {
        return undefined
      }
      const handleResize = () => measure()
      window.addEventListener('resize', handleResize)
      return () => window.removeEventListener('resize', handleResize)
    }

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (!entry) return
      const { width, height } = entry.contentRect
      updateState(width, height)
    })

    observer.observe(element)
    return () => observer.disconnect()
  }, [baseHeight, baseWidth, maxScale, minScale, scaleFactor])

  return {
    ref: containerRef,
    scale: deferredState.scale,
    width: deferredState.width,
    height: deferredState.height,
    ready: deferredState.ready,
    immediateScale: immediateState.scale
  }
}
