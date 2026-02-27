/**
 * 响应式模态框组件
 *
 * 移动端全屏显示，桌面端居中显示
 * 自动处理遮罩层、滚动锁定、ESC 关闭
 *
 * 使用方式：
 * ```tsx
 * <ResponsiveModal open={isOpen} onClose={() => setIsOpen(false)}>
 *   <ResponsiveModal.Header>标题</ResponsiveModal.Header>
 *   <ResponsiveModal.Body>内容</ResponsiveModal.Body>
 *   <ResponsiveModal.Footer>
 *     <Button onClick={() => setIsOpen(false)}>关闭</Button>
 *   </ResponsiveModal.Footer>
 * </ResponsiveModal>
 * ```
 */

import { type ReactNode, useEffect } from 'react'
import { cn } from '@/utils'
import { X } from 'lucide-react'
import { Button } from '../Button'
import {
  modalWidthClassMap,
  modalHeightClassMap,
  type ModalWidth,
  type ModalHeight
} from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'

interface ResponsiveModalProps {
  children: ReactNode
  /** 是否打开 */
  open: boolean
  /** 关闭回调 */
  onClose: () => void
  /** 模态框尺寸（默认 responsive，自动适配）*/
  size?: ModalWidth
  height?: ModalHeight
  /** 是否显示关闭按钮 */
  showClose?: boolean
  /** 是否点击遮罩层关闭 */
  closeOnOverlay?: boolean
  /** 自定义类名 */
  className?: string
}

export function ResponsiveModal({
  children,
  open,
  onClose,
  size = 'normal',
  height = 'limited',
  showClose = true,
  closeOnOverlay = true,
  className
}: ResponsiveModalProps) {
  // 锁定页面滚动
  useEffect(() => {
    if (open) {
      document.body.style.overflow = 'hidden'
      return () => {
        document.body.style.overflow = ''
      }
    }
  }, [open])

  // ESC 键关闭
  useEffect(() => {
    if (!open) return

    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }

    document.addEventListener('keydown', handleEsc)
    return () => document.removeEventListener('keydown', handleEsc)
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-[9999] flex items-center justify-center p-4"
      onClick={closeOnOverlay ? onClose : undefined}
    >
      {/* 遮罩层 */}
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" />

      {/* 模态框内容 */}
      <div
        className={cn(
          'relative bg-white dark:bg-[#1e293b] rounded-2xl shadow-2xl',
          'overflow-hidden flex flex-col',
          modalWidthClassMap[size],
          modalHeightClassMap[height],
          className
        )}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 关闭按钮 */}
        {showClose && (
          <Button
            type="button"
            onClick={onClose}
            variant="ghost"
            size="icon"
            className="absolute top-4 right-4 z-10 rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-white transition-colors"
            aria-label="关闭"
          >
            <X size={20} />
          </Button>
        )}

        {children}
      </div>
    </div>
  )
}

/** 模态框头部 */
ResponsiveModal.Header = function ResponsiveModalHeader({
  children,
  className
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div
      className={cn(
        'px-6 py-5 border-b border-slate-200 dark:border-slate-700',
        TYPOGRAPHY.subtitle,
        'font-bold',
        'text-slate-900 dark:text-white',
        className
      )}
    >
      {children}
    </div>
  )
}

/** 模态框主体 */
ResponsiveModal.Body = function ResponsiveModalBody({
  children,
  className
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div
      className={cn(
        'flex-1 px-6 py-5 overflow-y-auto custom-scrollbar',
        TYPOGRAPHY.body,
        'text-slate-700 dark:text-slate-300',
        className
      )}
    >
      {children}
    </div>
  )
}

/** 模态框底部 */
ResponsiveModal.Footer = function ResponsiveModalFooter({
  children,
  className
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div
      className={cn(
        'px-6 py-4 border-t border-slate-200 dark:border-slate-700',
        'flex items-center justify-end gap-3',
        className
      )}
    >
      {children}
    </div>
  )
}
