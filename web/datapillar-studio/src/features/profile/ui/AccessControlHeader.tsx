import { Fingerprint, Sliders } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'

export function AccessControlHeader() {
  return (
    <div className="bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800">
      <div className="px-6 @md:px-8 py-5 flex items-center justify-between gap-6">
        <div className="flex items-center gap-4 min-w-0">
          <div className="size-11 rounded-2xl bg-white text-slate-600 dark:bg-slate-900 dark:text-slate-200 border border-slate-200 dark:border-slate-800 flex items-center justify-center shadow-sm">
            <Fingerprint size={iconSizeToken.large} />
          </div>
          <div className="min-w-0">
            <div className={cn(TYPOGRAPHY.caption, 'font-bold uppercase tracking-[0.2em] text-slate-600 dark:text-slate-300')}>
              Governance Control
            </div>
            <div className={cn(TYPOGRAPHY.caption, 'mt-1 flex items-center gap-2 text-slate-400 dark:text-slate-500')}>
              <span className="font-semibold text-slate-500 dark:text-slate-300">ADMIN_SCOPE</span>
              <span className="text-slate-300 dark:text-slate-600">&gt;</span>
              <span>身份与访问管理 (IAM)</span>
            </div>
          </div>
        </div>

        <div
          className={cn(
            TYPOGRAPHY.caption,
            'inline-flex items-center gap-1.5 px-3 py-1 rounded-lg font-semibold bg-white text-brand-600 shadow-sm border border-slate-200 dark:border-slate-700 dark:bg-slate-900 dark:text-brand-300'
          )}
        >
          <Sliders size={iconSizeToken.small} />
          权限架构
        </div>
      </div>
    </div>
  )
}
