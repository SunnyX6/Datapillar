/**
 * infinite scroll loading Hook
 *
 * use IntersectionObserver Detect whether the sentinel element enters the viewport,Trigger loading of more data
 */

import { useRef,useCallback,useEffect } from 'react'

interface UseInfiniteScrollOptions {
 /** Is there more data?*/
 hasMore:boolean
 /** Is loading */
 loading:boolean
 /** Callback for loading more data */
 onLoadMore:() => void
 /** trigger threshold,Triggered at how many pixels from the bottom(Default 100) */
 threshold?: number
 /** autofill:When the first screen is not enough to generate scroll bars,Automatic continuous loading,Until scrollable or no more data */
 autoFill?: boolean
 /** dynamic threshold:rootMargin Bottom press"at least 1 Screen height"prefetch(Avoid large screen triggering too late) */
 dynamicThreshold?: boolean
 /**
 * IntersectionObserver of root.* - incoming Element:Triggered based on the scroll container(Recommended:Outer layer overflow-auto container)
 * - incoming null:to viewport Trigger for baseline
 * - Not passed on:Automatically search up for the nearest"Can be scrolled vertically"Ancestor element as root;If not found,it degrades to viewport
 */
 root?: Element | null
}

function findScrollableParent(element:HTMLElement) {
 if (typeof window === 'undefined') return null

 let current:HTMLElement | null = element.parentElement
 while (current) {
 const overflowY = window.getComputedStyle(current).overflowY
 const isScrollableY = overflowY === 'auto' || overflowY === 'scroll' || overflowY === 'overlay'
 if (isScrollableY) return current
 current = current.parentElement
 }

 return null
}

/**
 * infinite scroll Hook
 *
 * @returns sentinelRef - Sentinel element ref,placed at the end of the list
 */
export function useInfiniteScroll({
 hasMore,loading,onLoadMore,threshold = 100,autoFill = false,dynamicThreshold = false,root
}:UseInfiniteScrollOptions) {
 const sentinelRef = useRef<HTMLDivElement>(null)
 const observerRef = useRef<IntersectionObserver | null>(null)
 const rootRef = useRef<Element | null>(null)
 const inFlightRef = useRef(false)

 const handleIntersect = useCallback((entries:IntersectionObserverEntry[]) => {
 const [entry] = entries
 if (entry.isIntersecting && hasMore &&!loading &&!inFlightRef.current) {
 inFlightRef.current = true
 onLoadMore()
 }
 },[hasMore,loading,onLoadMore])

 useEffect(() => {
 const sentinel = sentinelRef.current
 if (!sentinel) return

 // Clean out the old ones observer
 if (observerRef.current) {
 observerRef.current.disconnect()
 }

 const resolvedRoot =
 root === undefined?findScrollableParent(sentinel):root

 rootRef.current = resolvedRoot?? null
 const rootHeight =
 resolvedRoot instanceof HTMLElement?resolvedRoot.clientHeight:typeof window!== 'undefined'?window.innerHeight:0
 const effectiveThreshold = dynamicThreshold?Math.max(threshold,rootHeight):threshold

 // create new observer
 observerRef.current = new IntersectionObserver(handleIntersect,{
 root:resolvedRoot?? null,// Only extend the trigger area downwards:distance from bottom threshold Pixel will preload the next page
 rootMargin:`0px 0px ${effectiveThreshold}px 0px`
 })

 observerRef.current.observe(sentinel)

 return () => {
 if (observerRef.current) {
 observerRef.current.disconnect()
 }
 }
 },[handleIntersect,threshold,dynamicThreshold,root])

 useEffect(() => {
 if (!loading) {
 inFlightRef.current = false
 }
 },[loading])

 useEffect(() => {
 if (!autoFill || loading ||!hasMore || inFlightRef.current) return

 const rootEl = rootRef.current
 if (!(rootEl instanceof HTMLElement)) return

 const hasVerticalScroll = rootEl.scrollHeight > rootEl.clientHeight + 1
 if (!hasVerticalScroll) {
 inFlightRef.current = true
 onLoadMore()
 }
 },[autoFill,hasMore,loading,onLoadMore])

 return { sentinelRef,rootRef }
}
