import { AppWindow, Fingerprint, Sliders } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'

type AccessControlHeaderTab = 'architecture' | 'definition'

interface AccessControlHeaderProps {
  activeTab?: AccessControlHeaderTab
  onTabChange?: (tab: AccessControlHeaderTab) => void
}

const HEADER_TABS: Array<{ id: AccessControlHeaderTab; label: string; icon: typeof Sliders }> = [
  { id: 'definition', label: '功能定义', icon: AppWindow },
  { id: 'architecture', label: '权限架构', icon: Sliders }
]

export function AccessControlHeader({ activeTab = 'definition', onTabChange }: AccessControlHeaderProps) {
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

        <div className="inline-flex items-center gap-1 rounded-xl bg-slate-100 dark:bg-slate-800/70 p-0.5 border border-slate-200 dark:border-slate-700">
          {HEADER_TABS.map((tab) => {
            const isActive = activeTab === tab.id
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => onTabChange?.(tab.id)}
                aria-pressed={isActive}
                className={cn(
                  TYPOGRAPHY.caption,
                  'px-3 py-1 rounded-lg font-semibold transition-colors flex items-center gap-1.5 cursor-pointer',
                  isActive
                    ? 'bg-white text-brand-600 shadow-sm dark:bg-slate-900 dark:text-brand-300'
                    : 'text-slate-500 dark:text-slate-400'
                )}
              >
                <tab.icon size={iconSizeToken.small} />
                {tab.label}
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
