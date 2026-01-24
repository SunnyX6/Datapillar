/**
 * 无限滚动加载 Hook
 *
 * 使用 IntersectionObserver 检测哨兵元素是否进入视口，触发加载更多数据
 */

import { useRef, useCallback, useEffect } from 'react'

interface UseInfiniteScrollOptions {
  /** 是否还有更多数据 */
  hasMore: boolean
  /** 是否正在加载 */
  loading: boolean
  /** 加载更多数据的回调 */
  onLoadMore: () => void
  /** 触发阈值，距离底部多少像素时触发（默认 100） */
  threshold?: number
  /** 自动填充：首屏不足以产生滚动条时，自动连续加载，直到可滚动或无更多数据 */
  autoFill?: boolean
  /** 动态阈值：rootMargin 底部按“至少 1 屏高度”预取（避免大屏触发过晚） */
  dynamicThreshold?: boolean
  /**
   * IntersectionObserver 的 root。
   * - 传入 Element：以该滚动容器为基准触发（推荐：外层 overflow-auto 容器）
   * - 传入 null：以 viewport 为基准触发
   * - 不传：自动向上查找最近的「可纵向滚动」祖先元素作为 root；找不到则退化为 viewport
   */
  root?: Element | null
}

function findScrollableParent(element: HTMLElement) {
  if (typeof window === 'undefined') return null

  let current: HTMLElement | null = element.parentElement
  while (current) {
    const overflowY = window.getComputedStyle(current).overflowY
    const isScrollableY = overflowY === 'auto' || overflowY === 'scroll' || overflowY === 'overlay'
    if (isScrollableY) return current
    current = current.parentElement
  }

  return null
}

/**
 * 无限滚动 Hook
 *
 * @returns sentinelRef - 哨兵元素的 ref，放置在列表末尾
 */
export function useInfiniteScroll({
  hasMore,
  loading,
  onLoadMore,
  threshold = 100,
  autoFill = false,
  dynamicThreshold = false,
  root
}: UseInfiniteScrollOptions) {
  const sentinelRef = useRef<HTMLDivElement>(null)
  const observerRef = useRef<IntersectionObserver | null>(null)
  const rootRef = useRef<Element | null>(null)
  const inFlightRef = useRef(false)

  const handleIntersect = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      const [entry] = entries
      if (entry.isIntersecting && hasMore && !loading && !inFlightRef.current) {
        inFlightRef.current = true
        onLoadMore()
      }
    },
    [hasMore, loading, onLoadMore]
  )

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel) return

    // 清理旧的 observer
    if (observerRef.current) {
      observerRef.current.disconnect()
    }

    const resolvedRoot =
      root === undefined ? findScrollableParent(sentinel) : root

    rootRef.current = resolvedRoot ?? null
    const rootHeight =
      resolvedRoot instanceof HTMLElement
        ? resolvedRoot.clientHeight
        : typeof window !== 'undefined'
          ? window.innerHeight
          : 0
    const effectiveThreshold = dynamicThreshold ? Math.max(threshold, rootHeight) : threshold

    // 创建新的 observer
    observerRef.current = new IntersectionObserver(handleIntersect, {
      root: resolvedRoot ?? null,
      // 仅向下扩展触发区域：距离底部 threshold 像素就预加载下一页
      rootMargin: `0px 0px ${effectiveThreshold}px 0px`
    })

    observerRef.current.observe(sentinel)

    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect()
      }
    }
  }, [handleIntersect, threshold, dynamicThreshold, root])

  useEffect(() => {
    if (!loading) {
      inFlightRef.current = false
    }
  }, [loading])

  useEffect(() => {
    if (!autoFill || loading || !hasMore || inFlightRef.current) return

    const rootEl = rootRef.current
    if (!(rootEl instanceof HTMLElement)) return

    const hasVerticalScroll = rootEl.scrollHeight > rootEl.clientHeight + 1
    if (!hasVerticalScroll) {
      inFlightRef.current = true
      onLoadMore()
    }
  }, [autoFill, hasMore, loading, onLoadMore])

  return { sentinelRef, rootRef }
}
