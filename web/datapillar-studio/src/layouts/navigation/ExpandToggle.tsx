import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

interface ExpandToggleProps {
  variant: 'sidebar' | 'topnav'
  onToggle: () => void
  className?: string
}

/**
 * 左右通用的半圆展开/折叠按钮，保持“一个圆切成两半”的视觉
 */
export function ExpandToggle({ variant, onToggle, className }: ExpandToggleProps) {
  const isSidebarVariant = variant === 'sidebar'
  const label = isSidebarVariant ? '折叠导航栏' : '展开导航栏'

  return (
    <button
      type="button"
      onClick={onToggle}
      aria-label={label}
      title={label}
      className={cn(
        'relative flex-shrink-0 overflow-hidden flex items-center justify-center bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 shadow-lg text-slate-500 hover:text-indigo-500 transition-colors h-14 w-6',
        isSidebarVariant ? 'rounded-l-full' : 'rounded-r-full',
        className
      )}
    >
      <span
        aria-hidden
        className={cn(
          'absolute top-1/2 -translate-y-1/2 h-14 w-14 rounded-full border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 shadow-lg',
          isSidebarVariant ? '-right-7' : '-left-7'
        )}
      />
      <span className="relative z-10">
        {isSidebarVariant ? <ChevronLeft size={16} /> : <ChevronRight size={16} />}
      </span>
    </button>
  )
}
