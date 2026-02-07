import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, X } from 'lucide-react'
import { getPricingPlans } from '@/layouts/home/constants'
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'

interface PricingProps {
  onRequestAccess: () => void
}

export function Pricing({ onRequestAccess }: PricingProps) {
  const { t } = useTranslation('home')
  const pricingPlans = useMemo(() => getPricingPlans(t), [t])
  const middlePlanId = pricingPlans[Math.floor(pricingPlans.length / 2)]?.id
  const defaultPlanId = middlePlanId ?? pricingPlans.find((plan) => plan.highlight)?.id ?? pricingPlans[0]?.id
  const [activePlanId, setActivePlanId] = useState(defaultPlanId)

  return (
    <section id="pricing" className="py-24 bg-[#020410] relative overflow-hidden border-b border-white/5">
      <div className="absolute bottom-0 left-0 w-full h-[500px] bg-gradient-to-t from-violet-900/10 to-transparent pointer-events-none" />

      <div className={`${contentMaxWidthClassMap.ultraWide} mx-auto px-8 relative z-10`}>
        <div className="text-center mb-20">
          <h2 className="text-violet-400 font-mono text-xs mb-4 tracking-wider uppercase">{t('pricing.eyebrow')}</h2>
          <h3 className="text-5xl font-bold text-white mb-6">{t('pricing.title')}</h3>
          <p className="max-w-xl mx-auto text-slate-400 text-lg">{t('pricing.subtitle')}</p>
        </div>

        <div className="grid grid-cols-3 gap-6 max-w-6xl mx-auto items-center">
          {pricingPlans.map((plan) => {
            const isActive = plan.id === activePlanId
            const isPopular = plan.id === middlePlanId
            return (
            <div
              key={plan.id}
              role="button"
              tabIndex={0}
              onClick={() => setActivePlanId(plan.id)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault()
                  setActivePlanId(plan.id)
                }
              }}
              className={`relative flex flex-col p-8 rounded-xl border transition-all duration-300 h-full cursor-pointer focus:outline-none focus:ring-2 focus:ring-violet-500/40 ${
                isActive
                  ? 'bg-[#0f1623] border-violet-500/50 shadow-[0_0_40px_rgba(99,102,241,0.15)] z-10 scale-105'
                  : 'bg-[#0b0f19] border-white/5 hover:border-violet-500/20'
              }`}
            >
              {isPopular && (
                <div className="absolute top-0 left-1/2 -translate-x-1/2 -translate-y-1/2 bg-[#5558ff] text-white text-micro font-bold px-3 py-1 rounded-full uppercase tracking-wide border border-violet-400/50 shadow-lg">
                  {t('pricing.popular')}
                </div>
              )}

              <div className="mb-8">
                <h4 className="text-base font-medium text-slate-300 mb-2">{plan.name}</h4>
                <div className="flex items-baseline gap-1">
                  <span className="text-4xl font-bold text-white tracking-tight">{plan.price}</span>
                  {!plan.isCustom && <span className="text-slate-500 text-sm">{t('pricing.perMonth')}</span>}
                </div>
                <p className="text-slate-500 text-sm mt-4 min-h-[40px] leading-relaxed">{plan.description}</p>
              </div>

              <div className="flex-1 mb-8 pt-8 border-t border-white/5">
                <ul className="space-y-4">
                  {plan.features.map((feature, idx) => (
                    <li key={idx} className="flex items-start gap-3">
                      {feature.included ? (
                        <Check className="w-4 h-4 text-cyan-400 shrink-0 mt-0.5" />
                      ) : (
                        <X className="w-4 h-4 text-slate-700 shrink-0 mt-0.5" />
                      )}
                      <span className={`text-sm ${feature.included ? 'text-slate-300' : 'text-slate-600'}`}>
                        {feature.text}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>

              <button
                onClick={onRequestAccess}
                className={`w-full py-3 rounded-lg font-medium text-sm transition-all border ${
                  isActive
                    ? 'bg-[#5558ff] hover:bg-[#4548e6] text-white border-violet-500 shadow-lg shadow-violet-500/20'
                    : 'bg-transparent hover:bg-white/5 text-white border-white/10'
                }`}
              >
                {plan.cta}
              </button>
            </div>
          )})}
        </div>
      </div>
    </section>
  )
}
