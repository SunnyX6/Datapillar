import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from '@/utils'

interface ExpandToggleProps {
  variant: 'sidebar' | 'topnav'
  onToggle: () => void
  className?: string
}

/**
 * Semicircular expansion for left and right use/Collapse button，keep“A circle cut in half”vision
 */
export function ExpandToggle({ variant, onToggle, className }: ExpandToggleProps) {
  const isSidebarVariant = variant === 'sidebar'
  const label = isSidebarVariant ? 'Collapse navigation bar' : 'Expand navigation bar'

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
