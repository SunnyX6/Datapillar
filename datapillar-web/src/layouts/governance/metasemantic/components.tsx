import { TrendingUp } from 'lucide-react'
import type { CategoryConfig } from './types'
import { iconSizeToken } from '@/design-tokens/dimensions'

export function Badge({
  children,
  variant = 'default'
}: {
  children: React.ReactNode
  variant?: 'default' | 'success' | 'warning' | 'purple' | 'blue' | 'danger'
}) {
  const styles = {
    default: 'bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700',
    success: 'bg-emerald-50 text-emerald-600 border-emerald-100 dark:bg-emerald-900/30 dark:text-emerald-400 dark:border-emerald-800',
    warning: 'bg-amber-50 text-amber-600 border-amber-100 dark:bg-amber-900/30 dark:text-amber-400 dark:border-amber-800',
    purple: 'bg-purple-50 text-purple-600 border-purple-100 dark:bg-purple-900/30 dark:text-purple-400 dark:border-purple-800',
    blue: 'bg-blue-50 text-blue-600 border-blue-100 dark:bg-blue-900/30 dark:text-blue-400 dark:border-blue-800',
    danger: 'bg-rose-50 text-rose-600 border-rose-100 dark:bg-rose-900/30 dark:text-rose-400 dark:border-rose-800'
  }
  return (
    <span
      className={`px-2 py-0.5 rounded-full text-micro font-semibold border ${styles[variant]} uppercase tracking-tight inline-flex items-center gap-1`}
    >
      {children}
    </span>
  )
}

export function HubAssetCard({ config, onClick }: { config: CategoryConfig; onClick: () => void }) {
  return (
    <div
      onClick={onClick}
      className="group bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl @md:rounded-2xl p-4 @md:p-6 hover:shadow-lg hover:border-blue-300 dark:hover:border-blue-600 transition-all cursor-pointer relative overflow-hidden"
    >
      <div
        className={`${config.color} w-8 h-8 @md:w-10 @md:h-10 rounded-lg @md:rounded-xl flex items-center justify-center text-white mb-3 @md:mb-4 shadow-md`}
      >
        <config.icon size={iconSizeToken.medium} className="@md:hidden" />
        <config.icon size={iconSizeToken.large} className="hidden @md:block" />
      </div>
      <h3 className="text-body-sm @md:text-heading font-semibold text-slate-800 dark:text-slate-100 mb-1 @md:mb-1.5 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
        {config.label}
      </h3>
      <p className="text-slate-500 dark:text-slate-400 text-caption @md:text-body-sm leading-relaxed mb-3 @md:mb-4">{config.description}</p>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1 @md:gap-1.5">
          <span className="text-subtitle @md:text-title font-bold text-slate-900 dark:text-slate-100">{config.count}</span>
          <span className="text-micro @md:text-caption font-medium text-slate-400 dark:text-slate-500 uppercase tracking-wider">Assets</span>
        </div>
        {config.trend && (
          <div className="text-emerald-600 dark:text-emerald-400 text-micro font-semibold bg-emerald-50 dark:bg-emerald-900/30 px-1.5 @md:px-2 py-0.5 rounded-full border border-emerald-100 dark:border-emerald-800 flex items-center gap-0.5 @md:gap-1">
            <TrendingUp size={iconSizeToken.tiny} /> {config.trend}
          </div>
        )}
      </div>
    </div>
  )
}
