import { useCallback, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'

type TooltipSide = 'top' | 'right' | 'bottom' | 'left' | 'center-bottom'

export interface TooltipProps {
  children: ReactNode
  content: ReactNode
  side?: TooltipSide
  className?: string
  contentClassName?: string
  delay?: number
  disabled?: boolean
  sideOffset?: number
  /** 触发器被点击时是否自动关闭（避免点击后 tooltip 残留） */
  closeOnClick?: boolean
}

export function Tooltip({
  children,
  content,
  side = 'right',
  className,
  contentClassName,
  delay = 0,
  disabled = false,
  sideOffset = 10,
  closeOnClick = true
}: TooltipProps) {
  const [isVisible, setIsVisible] = useState(false)
  const triggerRef = useRef<HTMLDivElement | null>(null)
  const tooltipRef = useRef<HTMLDivElement | null>(null)
  const timerRef = useRef<number | null>(null)
  // 处理“鼠标点击导致 focus”场景：避免 click 后 tooltip 因 focus 事件再次被打开
  const pointerDownRef = useRef(false)

  useEffect(() => {
    return () => {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [])

  const updatePosition = useCallback(() => {
    const el = triggerRef.current
    if (!el) return

    const rect = el.getBoundingClientRect()
    let top = 0
    let left = 0
    const gap = sideOffset
    const tooltip = tooltipRef.current
    if (!tooltip) return

    switch (side) {
      case 'right':
        top = rect.top + rect.height / 2
        left = rect.right + gap
        tooltip.style.removeProperty('--tooltip-arrow-left')
        break
      case 'left':
        top = rect.top + rect.height / 2
        left = rect.left - gap
        tooltip.style.removeProperty('--tooltip-arrow-left')
        break
      case 'top':
        top = rect.top - gap
        left = rect.left + rect.width / 2
        tooltip.style.removeProperty('--tooltip-arrow-left')
        break
      case 'center-bottom':
      case 'bottom':
        top = rect.bottom + gap
        left = rect.left + rect.width / 2
        tooltip.style.removeProperty('--tooltip-arrow-left')
        break
    }
    tooltip.style.top = `${top}px`
    tooltip.style.left = `${left}px`
  }, [side, sideOffset])

  const close = useCallback(() => {
    if (timerRef.current) {
      window.clearTimeout(timerRef.current)
      timerRef.current = null
    }
    setIsVisible(false)
  }, [])

  const open = useCallback(() => {
    if (disabled) return
    if (content === null || content === undefined) return
    if (delay > 0) {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current)
      }
      timerRef.current = window.setTimeout(() => {
        updatePosition()
        setIsVisible(true)
        timerRef.current = null
      }, delay)
      return
    }

    updatePosition()
    setIsVisible(true)
  }, [content, delay, disabled, updatePosition])

  useLayoutEffect(() => {
    if (!isVisible) return
    updatePosition()
  }, [isVisible, updatePosition])

  useEffect(() => {
    if (!isVisible) return
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [isVisible, updatePosition])

  if (content === null || content === undefined || disabled) {
    return <>{children}</>
  }

  return (
    <div
      ref={triggerRef}
      className={cn('relative inline-flex', className)}
      onMouseEnter={open}
      onMouseLeave={close}
      onFocus={() => {
        if (pointerDownRef.current) return
        open()
      }}
      onBlur={close}
      onPointerDownCapture={() => {
        if (!closeOnClick) return
        pointerDownRef.current = true
        close()
        window.setTimeout(() => {
          pointerDownRef.current = false
        }, 0)
      }}
      onClickCapture={() => {
        if (!closeOnClick) return
        close()
      }}
    >
      {children}
      {isVisible &&
        createPortal(
          <div
            ref={tooltipRef}
            className="fixed z-[1000000] pointer-events-none"
            style={{
              transform:
                side === 'right'
                  ? 'translateY(-50%)'
                  : side === 'left'
                  ? 'translate(-100%, -50%)'
                  : side === 'top'
                    ? 'translate(-50%, -100%)'
                    : 'translate(-50%, 0)'
            }}
          >
            <div className="relative">
              <div
                className={cn(
                  'absolute w-3 h-3 bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-700 z-10 rotate-45',
                  side === 'right' && 'left-[-5px] top-1/2 -translate-y-1/2 border-l border-b',
                  side === 'left' && 'right-[-5px] top-1/2 -translate-y-1/2 border-r border-t',
                  side === 'top' && 'bottom-[-5px] left-1/2 -translate-x-1/2 border-r border-b',
                  (side === 'bottom' || side === 'center-bottom') && 'top-[-5px] left-1/2 -translate-x-1/2 border-l border-t'
                )}
              />

              <div
                className={cn(
                  TYPOGRAPHY.caption,
                  'relative z-20 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 px-3 py-2 rounded-lg shadow-sm animate-in fade-in zoom-in-95 duration-100 whitespace-nowrap',
                  contentClassName
                )}
              >
                {content}
              </div>
            </div>
          </div>,
          document.body
        )}
    </div>
  )
}
