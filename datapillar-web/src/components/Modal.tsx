/**
 * 通用模态框组件
 *
 * 支持三种尺寸：小(sm) | 中(md) | 大(lg)
 * 参考 examples/MetricRegistrationModal.tsx 设计风格
 */

import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import { modalWidthClassMap } from '@/design-tokens/dimensions'

interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title?: string
  children: ReactNode
  /** 统一 footer（与 footerLeft/footerRight 二选一） */
  footer?: ReactNode
  /** 左侧 footer 按钮区域 */
  footerLeft?: ReactNode
  /** 右侧 footer 按钮区域 */
  footerRight?: ReactNode
  /** 小(sm): 窄表单 | 中(md): 标准表单 | 大(lg): 复杂表单 */
  size?: 'sm' | 'md' | 'lg'
}

export function Modal({
  isOpen,
  onClose,
  title,
  children,
  footer,
  footerLeft,
  footerRight,
  size = 'md'
}: ModalProps) {
  const modalRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (!isOpen) return

    document.body.style.overflow = 'hidden'
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

  const hasFooter = footer || footerLeft || footerRight

  // 使用 design-tokens 中定义的模态框宽度
  const widthClass =
    size === 'sm'
      ? modalWidthClassMap.small
      : size === 'md'
        ? modalWidthClassMap.normal
        : modalWidthClassMap.huge

  const modalContent = (
    <div className="fixed inset-0 z-[999999] flex items-center justify-center p-6">
      {/* 背景遮罩 */}
      <div
        className="absolute inset-0 bg-slate-900/60 backdrop-blur-xl animate-in fade-in duration-300"
        onClick={onClose}
      />

      {/* 模态框内容 */}
      <div
        ref={modalRef}
        tabIndex={-1}
        className={`relative w-full ${widthClass} bg-white dark:bg-slate-900 shadow-[0_32px_128px_-16px_rgba(0,0,0,0.3)] rounded-[2rem] border border-slate-200 dark:border-slate-700 overflow-hidden outline-none flex flex-col max-h-[90vh] animate-in zoom-in-95 fade-in duration-300`}
      >
        {/* 关闭按钮 */}
        <button
          type="button"
          onClick={onClose}
          className="absolute top-6 right-6 w-10 h-10 flex items-center justify-center hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full text-slate-400 z-10 transition-all group"
          aria-label="关闭"
        >
          <X size={20} className="group-hover:rotate-90 transition-transform" />
        </button>

        {/* 标题 */}
        {title && (
          <div className="px-8 pt-8 pb-2">
            <h2 className="text-xl font-bold text-slate-900 dark:text-white tracking-tight">{title}</h2>
          </div>
        )}

        {/* 内容区域 */}
        <div className="flex-1 min-h-0 overflow-y-auto px-8 py-4 custom-scrollbar">{children}</div>

        {/* 底部按钮区域 */}
        {hasFooter && (
          <div className="flex items-center justify-between gap-3 px-8 py-5 border-t border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-800/30">
            <div className="flex items-center gap-2">{footerLeft}</div>
            <div className="flex items-center gap-2">{footer || footerRight}</div>
          </div>
        )}
      </div>
    </div>
  )

  return createPortal(modalContent, document.body)
}

/** 模态框取消按钮 */
export function ModalCancelButton({
  onClick,
  disabled,
  children = '取消'
}: {
  onClick: () => void
  disabled?: boolean
  children?: ReactNode
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className="px-5 py-2 rounded-xl text-sm font-medium text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 transition-all disabled:opacity-50"
    >
      {children}
    </button>
  )
}

/** 模态框确认按钮 */
export function ModalPrimaryButton({
  onClick,
  disabled,
  loading,
  children = '确认'
}: {
  onClick: () => void
  disabled?: boolean
  loading?: boolean
  children?: ReactNode
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled || loading}
      className="px-6 py-2 bg-blue-600 text-white rounded-xl text-sm font-semibold shadow-lg shadow-blue-100 dark:shadow-blue-900/30 hover:bg-blue-700 hover:scale-[1.02] transition-all disabled:opacity-50 disabled:scale-100 disabled:cursor-not-allowed flex items-center gap-2"
    >
      {loading && (
        <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      )}
      {children}
    </button>
  )
}
