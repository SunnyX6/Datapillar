import { lazy, Suspense, useState, useRef, useEffect, useMemo, useLayoutEffect, type CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import { ChatPanel } from '@/layouts/workflow/Chat'
import { useLayout } from '@/layouts/responsive'

const MIN_CHAT_WIDTH = 300
const MAX_CHAT_WIDTH = 520
const CHAT_WIDTH_RATIO = 0.32
const HANDLE_ICON_HEIGHT = 48

const clampWidth = (value: number) => Math.max(MIN_CHAT_WIDTH, Math.min(MAX_CHAT_WIDTH, value))

const LazyWorkflowCanvasPanel = lazy(async () => {
  const module = await import('@/layouts/workflow/WorkflowStudio')
  return { default: module.WorkflowCanvasPanel }
})

export function WorkflowStudioPage() {
  const { ref: layoutRef, width: containerWidth, ready: layoutReady } = useLayout<HTMLDivElement>({
    baseWidth: 1440,
    baseHeight: 900,
    scaleFactor: 1,
    minScale: 0.5,
    maxScale: 2
  })
  const [chatWidth, setChatWidth] = useState(360)
  const [isResizing, setIsResizing] = useState(false)
  const [hasManualResize, setHasManualResize] = useState(false)
  const [isCollapsed, setIsCollapsed] = useState(false)
  const [layoutLocked, setLayoutLocked] = useState(false)
  const [canvasViewportVersion, setCanvasViewportVersion] = useState(0)
  const lastExpandedWidthRef = useRef(chatWidth)
  const animationTimeoutRef = useRef<NodeJS.Timeout | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const [containerRect, setContainerRect] = useState<DOMRect | null>(null)

  const bumpViewportVersion = () => setCanvasViewportVersion((version) => version + 1)
  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isResizing || !containerRef.current) return

      const containerRect = containerRef.current.getBoundingClientRect()
      const newWidth = e.clientX - containerRect.left

      // 限制最小和最大宽度
      if (newWidth >= MIN_CHAT_WIDTH && newWidth <= MAX_CHAT_WIDTH) {
        if (!hasManualResize) {
          setHasManualResize(true)
        }
        setChatWidth(newWidth)
      }
    }

    const handleMouseUp = () => {
      setIsResizing(false)
      bumpViewportVersion()
    }

    if (isResizing) {
      document.addEventListener('mousemove', handleMouseMove)
      document.addEventListener('mouseup', handleMouseUp)
      document.body.style.cursor = 'col-resize'
      document.body.style.userSelect = 'none'
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove)
      document.removeEventListener('mouseup', handleMouseUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
  }, [isResizing, hasManualResize])

  useLayoutEffect(() => {
    let rafId: number | null = null

    const updateRect = () => {
      const target = containerRef.current
      if (rafId) {
        cancelAnimationFrame(rafId)
      }
      rafId = requestAnimationFrame(() => {
        setContainerRect(target ? target.getBoundingClientRect() : null)
      })
    }

    updateRect()

    let resizeObserver: ResizeObserver | null = null
    if (typeof ResizeObserver !== 'undefined' && containerRef.current) {
      resizeObserver = new ResizeObserver(() => updateRect())
      resizeObserver.observe(containerRef.current)
    }

    window.addEventListener('resize', updateRect)
    window.addEventListener('scroll', updateRect, true)

    return () => {
      if (rafId) {
        cancelAnimationFrame(rafId)
      }
      resizeObserver?.disconnect()
      window.removeEventListener('resize', updateRect)
      window.removeEventListener('scroll', updateRect, true)
    }
  }, [])

  const handleMouseDown = () => {
    if (isCollapsed) {
      setIsCollapsed(false)
      setChatWidth(lastExpandedWidthRef.current)
      bumpViewportVersion()
    }
    setIsResizing(true)
  }

  const handleDoubleClick = () => {
    if (isCollapsed) {
      setIsCollapsed(false)
      setChatWidth(lastExpandedWidthRef.current)
      setHasManualResize(true)
      bumpViewportVersion()
      return
    }
    lastExpandedWidthRef.current = clampWidth(chatWidth)
    setIsCollapsed(true)
    setIsResizing(true)
    if (animationTimeoutRef.current) {
      clearTimeout(animationTimeoutRef.current)
    }
    animationTimeoutRef.current = setTimeout(() => {
      setIsResizing(false)
    }, 320)
    bumpViewportVersion()
  }

  useLayoutEffect(() => {
    let rafId: number | null = null

    const commitLockState = (locked: boolean) => {
      if (rafId) {
        cancelAnimationFrame(rafId)
      }
      rafId = requestAnimationFrame(() => {
        setLayoutLocked(locked)
      })
    }

    if (!layoutReady) {
      commitLockState(false)
      return () => {
        if (rafId) cancelAnimationFrame(rafId)
      }
    }

    let widthFrame: number | null = null

    if (!isCollapsed && !hasManualResize && containerWidth > 0) {
      const nextWidth = clampWidth(containerWidth * CHAT_WIDTH_RATIO)
      widthFrame = requestAnimationFrame(() => {
        setChatWidth((prev) => (Math.abs(prev - nextWidth) < 0.5 ? prev : nextWidth))
      })
    }

    commitLockState(true)

    return () => {
      if (rafId) cancelAnimationFrame(rafId)
      if (widthFrame) cancelAnimationFrame(widthFrame)
    }
  }, [layoutReady, containerWidth, isCollapsed, hasManualResize])

  const effectiveChatWidth = useMemo(() => (isCollapsed ? 0 : clampWidth(chatWidth)), [chatWidth, isCollapsed])

  useEffect(() => {
    if (!isCollapsed) {
      lastExpandedWidthRef.current = clampWidth(effectiveChatWidth || chatWidth)
    }
  }, [isCollapsed, effectiveChatWidth, chatWidth])

  const handlePosition = effectiveChatWidth <= 0 ? 0 : effectiveChatWidth
  const handleTop = containerRect?.top ?? 0
  const handleHeight = containerRect?.height ?? 0
  const handleLeft = containerRect ? containerRect.left + handlePosition : null
  const portalTarget = typeof document !== 'undefined' ? document.body : null
  const handleVisibleHeight = HANDLE_ICON_HEIGHT
  const handleOffsetTop = Math.max(handleTop, handleTop + (handleHeight - handleVisibleHeight) / 2)

  const handleNode = (
    <div
      onMouseDown={handleMouseDown}
      onDoubleClick={handleDoubleClick}
      className="relative top-0 h-full w-6 cursor-col-resize group -translate-x-1/2 pointer-events-auto"
    >
      <div className="absolute inset-y-0 -left-1 -right-1 z-10" />
      <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-20 flex flex-col items-center gap-2 py-2 px-2 rounded-full bg-slate-300/80 dark:bg-slate-600/80 shadow-[0_4px_20px_rgba(0,0,0,0.25)] group-hover:bg-indigo-400 dark:group-hover:bg-indigo-500 transition-all backdrop-blur-sm border border-white/20">
        <div className="w-1 h-1 bg-white rounded-full shadow-inner" />
        <div className="w-1 h-1 bg-white rounded-full shadow-inner" />
      </div>
    </div>
  )

  const handlePortal =
    handleLeft !== null && portalTarget
      ? createPortal(
        <div
          style={
            {
              '--handle-top': `${handleOffsetTop}px`,
              '--handle-left': `${handleLeft}px`,
              '--handle-height': `${handleVisibleHeight}px`
            } as CSSProperties
          }
          className="fixed pointer-events-none z-50 [top:var(--handle-top)] [left:var(--handle-left)] [height:var(--handle-height)]"
        >
          <div
            className="h-full pointer-events-auto"
          >
            {handleNode}
          </div>
        </div>,
        portalTarget
      )
      : null

  return (
    <>
      <section
        className={`flex h-full flex-col overflow-y-hidden overflow-x-visible bg-[#f8fafc] dark:bg-[#01030a] text-slate-900 dark:text-white relative z-40 ${layoutLocked ? 'visible' : 'invisible'}`}
      >
        <div ref={(node) => {
          containerRef.current = node
          layoutRef.current = node
        }} className="flex flex-1 overflow-y-hidden overflow-x-visible relative">
          <div
            style={{ '--chat-panel-width': `${effectiveChatWidth}px` } as CSSProperties}
            className="relative h-full flex-shrink-0 [width:var(--chat-panel-width)]"
          >
            <ChatPanel />
            <div className="pointer-events-none absolute top-0 right-0 h-full w-0.5 bg-gradient-to-r from-slate-900/20 via-transparent dark:from-white/25" />
          </div>

          <div className="flex-1 flex flex-col overflow-hidden">
            <Suspense fallback={<div className="flex flex-1 items-center justify-center bg-[#050713]"><div className="rounded-full border-2 border-indigo-200 border-t-indigo-500 size-10 animate-spin" /></div>}>
              <LazyWorkflowCanvasPanel viewportVersion={canvasViewportVersion} />
            </Suspense>
          </div>
        </div>
      </section>
      {handlePortal}
    </>
  )
}
