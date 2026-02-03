import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

export function Features() {
  const { t, i18n } = useTranslation('home')
  const tags = useMemo(() => t('features.tags', { returnObjects: true }) as string[], [t, i18n.language])

  return (
    <section id="features" className="pt-16 pb-20 bg-[#020410] relative border-b border-white/5">
      <div className="max-w-7xl mx-auto px-8">
        <div className="mb-12">
          <h2 className="text-violet-500 font-mono text-xs mb-4 tracking-wider uppercase">{t('features.eyebrow')}</h2>
          <h3 className="text-5xl font-bold text-white tracking-tight mb-6">
            {t('features.title')} <br />
            <span className="text-slate-500">{t('features.titleHighlight')}</span>
          </h3>
          <p className="text-slate-400 max-w-2xl text-lg mb-8 leading-relaxed">
            {t('features.description.line1')}
            <br />
            {t('features.description.line2')}
          </p>

          <div className="flex flex-wrap gap-3">
            {tags.map((tag, index) => {
              const dotColor = index === 0 ? 'bg-violet-500' : index === 1 ? 'bg-cyan-500' : 'bg-emerald-500'
              return (
                <div
                  key={tag}
                  className="px-3 py-1.5 rounded-full bg-[#0f1623] border border-white/10 text-xs text-slate-300 font-mono flex items-center gap-2 cursor-default"
                >
                  <div className={`w-1.5 h-1.5 rounded-full ${dotColor}`} />
                  {tag}
                </div>
              )
            })}
          </div>
        </div>

      </div>
    </section>
  )
}
