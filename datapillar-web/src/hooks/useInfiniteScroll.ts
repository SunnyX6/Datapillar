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
  threshold = 100
}: UseInfiniteScrollOptions) {
  const sentinelRef = useRef<HTMLDivElement>(null)
  const observerRef = useRef<IntersectionObserver | null>(null)

  const handleIntersect = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      const [entry] = entries
      if (entry.isIntersecting && hasMore && !loading) {
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

    // 创建新的 observer
    observerRef.current = new IntersectionObserver(handleIntersect, {
      rootMargin: `${threshold}px`
    })

    observerRef.current.observe(sentinel)

    return () => {
      if (observerRef.current) {
        observerRef.current.disconnect()
      }
    }
  }, [handleIntersect, threshold])

  return { sentinelRef }
}
