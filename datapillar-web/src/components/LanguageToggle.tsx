import { useI18nStore } from '@/stores'

const options = [
  { id: 'zh-CN', label: '中' },
  { id: 'en-US', label: 'EN' }
] as const

export function LanguageToggle() {
  const language = useI18nStore((state) => state.language)
  const setLanguage = useI18nStore((state) => state.setLanguage)

  const handleSelect = (next: (typeof options)[number]['id']) => {
    if (language !== next) {
      setLanguage(next)
    }
  }

  return (
    <div className="inline-flex items-center rounded-full border border-slate-200 dark:border-white/10 bg-slate-100 dark:bg-white/5 p-0.5 text-micro font-semibold h-8">
      {options.map((option) => (
        <button
          key={option.id}
          type="button"
          onClick={() => handleSelect(option.id)}
          className={`px-2 py-0.5 rounded-full transition-colors ${
            language === option.id
              ? 'bg-white dark:bg-indigo-500/20 text-indigo-600 dark:text-white shadow'
              : 'text-slate-500 dark:text-slate-400 hover:text-indigo-500'
          }`}
          aria-pressed={language === option.id}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
