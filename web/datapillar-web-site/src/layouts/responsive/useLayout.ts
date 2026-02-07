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
  /** 延迟更新的缩放比例（性能优化，推荐使用） */
  scale: number
  /** 延迟更新的宽度（性能优化，推荐使用） */
  width: number
  /** 延迟更新的高度（性能优化，推荐使用） */
  height: number
  ready: boolean
  /** 实时缩放比例（用于需要立即响应的场景） */
  immediateScale: number
}

/**
 * useLayout：监听容器尺寸并返回等比缩放信息
 *
 * - 根据 baseWidth/baseHeight 计算缩放比例
 * - scaleFactor 用于一次性调节整体大小
 * - 使用 React 19 useDeferredValue 优化性能，避免高频 resize 阻塞用户交互
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

  // React 19 useDeferredValue：自动延迟低优先级更新，根据设备性能自适应
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
