import { useI18nStore } from '@/state'
import { cn } from '@/utils'

const languages = [
  { id: 'zh-CN', label: '中' },
  { id: 'en-US', label: 'EN' }
] as const

export function LanguageToggle() {
  const language = useI18nStore((state) => state.language)
  const setLanguage = useI18nStore((state) => state.setLanguage)

  const handleSelect = (next: (typeof languages)[number]['id']) => {
    if (language !== next) {
      setLanguage(next)
    }
  }

  const isZh = language === 'zh-CN'

  return (
    <div className="inline-flex items-center rounded-full border border-slate-200 dark:border-white/10 bg-slate-100 dark:bg-white/5 p-0.5 h-7">
      <div className="relative flex h-full w-12 items-center">
        <span
          className={cn(
            'pointer-events-none absolute inset-0 w-1/2 rounded-full bg-white dark:bg-indigo-500/20 shadow-sm transition-transform duration-200',
            isZh ? 'translate-x-0' : 'translate-x-full'
          )}
          aria-hidden
        />
        {languages.map((item) => {
          const isActive = language === item.id
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => handleSelect(item.id)}
              className={cn(
                'relative z-10 flex-1 h-full rounded-full text-center text-micro font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500/30',
                isActive
                  ? 'text-indigo-600 dark:text-white'
                  : 'text-slate-500 dark:text-slate-400 hover:text-indigo-500'
              )}
              aria-pressed={isActive}
            >
              {item.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}
