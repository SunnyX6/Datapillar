/**
 * Universal modal component
 *
 * Support size：mini(mini) | small(sm) | in(md) | Big(lg) | extra large(xl)
 * Reference examples/MetricRegistrationModal.tsx design style
 */

import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import { modalWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { Button } from './Button'

interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title?: ReactNode
  /** subtitle，Shown below title */
  subtitle?: ReactNode
  children: ReactNode
  /** unify footer（with footerLeft/footerRight Choose one） */
  footer?: ReactNode
  /** left side footer button area */
  footerLeft?: ReactNode
  /** right side footer button area */
  footerRight?: ReactNode
  /** mini(mini): simple form | small(sm): narrow form | in(md): standard form | Big(lg): wide form | extra large(xl): Extra wide complex forms */
  size?: 'mini' | 'sm' | 'md' | 'lg' | 'xl'
}

export function Modal({
  isOpen,
  onClose,
  title,
  subtitle,
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

  // use design-tokens The width of the modal box defined in
  const widthClass =
    size === 'mini'
      ? modalWidthClassMap.mini
      : size === 'sm'
        ? modalWidthClassMap.small
        : size === 'md'
          ? modalWidthClassMap.normal
          : size === 'lg'
            ? modalWidthClassMap.large
            : modalWidthClassMap.huge

  const modalContent = (
    <div className="fixed inset-0 z-[999999] flex items-center justify-center p-6">
      {/* background mask */}
      <div
        className="absolute inset-0 bg-slate-900/60 backdrop-blur-xs animate-in fade-in duration-300"
        onClick={onClose}
      />

      {/* Modal box content */}
      <div
        ref={modalRef}
        tabIndex={-1}
        className={`relative w-full ${widthClass} bg-white dark:bg-slate-900 shadow-[0_32px_128px_-16px_rgba(0,0,0,0.3)] rounded-2xl border border-slate-200 dark:border-slate-700 overflow-hidden outline-none flex flex-col max-h-[90vh] animate-in zoom-in-95 fade-in duration-300`}
      >
        {/* close button */}
        <Button
          type="button"
          onClick={onClose}
          variant="ghost"
          size="icon"
          className="absolute top-6 right-6 w-10 h-10 flex items-center justify-center hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full text-slate-400 z-10 transition-all group"
          aria-label="close"
        >
          <X size={20} className="group-hover:rotate-90 transition-transform" />
        </Button>

        {/* Title */}
        {title && (
          <div className="px-8 pt-8 pb-2">
            <h2 className={`${TYPOGRAPHY.subtitle} font-bold text-slate-900 dark:text-white tracking-tight`}>
              {title}
            </h2>
            {subtitle && <div className="mt-1">{subtitle}</div>}
          </div>
        )}

        {/* content area */}
        <div className="flex-1 min-h-0 overflow-y-auto px-8 py-4 custom-scrollbar">{children}</div>

        {/* Bottom button area */}
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

/** Modal box cancel button */
export function ModalCancelButton({
  onClick,
  disabled,
  children = 'Cancel'
}: {
  onClick: () => void
  disabled?: boolean
  children?: ReactNode
}) {
  return (
    <Button
      onClick={onClick}
      disabled={disabled}
      variant="ghost"
      size="normal"
      className="px-5 py-2 rounded-xl text-sm font-medium text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 transition-all disabled:opacity-50"
    >
      {children}
    </Button>
  )
}

/** Modal box confirmation button */
export function ModalPrimaryButton({
  onClick,
  disabled,
  loading,
  variant = 'blue',
  children = 'Confirm'
}: {
  onClick: () => void
  disabled?: boolean
  loading?: boolean
  variant?: 'blue' | 'amber'
  children?: ReactNode
}) {
  const variantClass =
    variant === 'amber'
      ? 'bg-amber-500 hover:bg-amber-600 shadow-amber-100 dark:shadow-amber-900/30'
      : 'shadow-blue-100 dark:shadow-blue-900/30'

  return (
    <Button
      onClick={onClick}
      disabled={disabled || loading}
      variant="primary"
      size="normal"
      className={`px-6 py-2 text-white rounded-xl text-sm font-semibold shadow-lg ${variantClass} hover:scale-[1.02] transition-all disabled:opacity-50 disabled:scale-100 disabled:cursor-not-allowed flex items-center gap-2`}
    >
      {loading && (
        <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      )}
      {children}
    </Button>
  )
}
