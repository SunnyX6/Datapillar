/**
 * 通用模态框组件
 */

import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import { modalWidthClassMap } from '@/design-tokens/dimensions'

interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title: string
  children: ReactNode
  footerLeft?: ReactNode
  footerRight?: ReactNode
  contentOverlay?: ReactNode
  size?: 'sm' | 'md' | 'lg' | 'responsive'
}

export function Modal({
  isOpen,
  onClose,
  title,
  children,
  footerLeft,
  footerRight,
  contentOverlay,
  size = 'responsive'
}: ModalProps) {
  const modalRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!isOpen) return

    // 阻止背景滚动
    document.body.style.overflow = 'hidden'

    // 聚焦到模态框
    if (modalRef.current) {
      modalRef.current.focus()
    }

    return () => {
      document.body.style.overflow = ''
    }
  }, [isOpen])

  useEffect(() => {
    if (!isOpen) return

    const handler = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [isOpen, onClose])

  if (!isOpen) return null

  const sizeClasses = {
    sm: modalWidthClassMap.small,
    md: modalWidthClassMap.normal,
    lg: modalWidthClassMap.large,
    responsive: modalWidthClassMap.responsive
  }

  const modalContent = (
    <div
      className="fixed inset-0 z-[999999] flex items-center justify-center bg-slate-900/60 backdrop-blur-sm @container"
    >
      <div
        ref={modalRef}
        tabIndex={-1}
        className={`relative w-full ${sizeClasses[size]} mx-4 bg-white dark:bg-slate-900 rounded-lg shadow-2xl border border-slate-200 dark:border-slate-700 overflow-hidden outline-none flex flex-col max-h-[80vh]`}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200 dark:border-slate-800 flex-shrink-0">
          <h3 className="text-base font-semibold text-slate-900 dark:text-white">{title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
            aria-label="关闭"
          >
            <X size={16} />
          </button>
        </div>

        {/* Content - 滚动容器 */}
        <div className="relative flex-1 flex flex-col overflow-hidden">
          <div className="flex-1 overflow-y-auto custom-scrollbar px-3 py-4 text-slate-800 dark:text-slate-100 bg-white dark:bg-slate-900">
            {children}
          </div>

          {/* 内容覆盖层 (抽屉等) */}
          {contentOverlay}
        </div>

        {/* Footer */}
        {(footerLeft || footerRight) && (
          <div className="flex items-center justify-between gap-2 px-5 py-3 bg-slate-50 dark:bg-slate-800/50 border-t border-slate-200 dark:border-slate-800">
            <div className="flex items-center gap-2">{footerLeft}</div>
            <div className="flex items-center gap-2">{footerRight}</div>
          </div>
        )}
      </div>
    </div>
  )

  return createPortal(modalContent, document.body)
}
