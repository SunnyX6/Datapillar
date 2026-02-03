import React from 'react';
import { Check, X } from 'lucide-react';
import { PRICING_PLANS } from '../constants';

interface PricingProps {
  onRequestAccess: () => void;
}

export const Pricing: React.FC<PricingProps> = ({ onRequestAccess }) => {
  return (
    <section id="pricing" className="py-32 bg-[#020410] relative overflow-hidden border-b border-white/5">
      {/* Background Glow */}
      <div className="absolute bottom-0 left-0 w-full h-[500px] bg-gradient-to-t from-violet-900/10 to-transparent pointer-events-none"></div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="text-center mb-20">
          <h2 className="text-violet-400 font-mono text-xs mb-4 tracking-wider uppercase">订阅方案</h2>
          <h3 className="text-3xl md:text-5xl font-bold text-white mb-6">按用量付费，透明可控</h3>
          <p className="max-w-xl mx-auto text-slate-400 text-lg">
             仅为实际使用的计算和存储付费。不限制坐席数量。
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 max-w-6xl mx-auto items-center">
          {PRICING_PLANS.map((plan) => (
            <div 
              key={plan.id} 
              className={`relative flex flex-col p-8 rounded-xl border transition-all duration-300 h-full ${
                plan.highlight 
                  ? 'bg-[#0f1623] border-violet-500/50 shadow-[0_0_40px_rgba(99,102,241,0.15)] z-10 scale-105' 
                  : 'bg-[#0b0f19] border-white/5 hover:border-violet-500/20'
              }`}
            >
              {plan.highlight && (
                <div className="absolute top-0 left-1/2 -translate-x-1/2 -translate-y-1/2 bg-[#5558ff] text-white text-[10px] font-bold px-3 py-1 rounded-full uppercase tracking-wide border border-violet-400/50 shadow-lg">
                  最受欢迎
                </div>
              )}

              <div className="mb-8">
                <h4 className="text-base font-medium text-slate-300 mb-2">{plan.name}</h4>
                <div className="flex items-baseline gap-1">
                  <span className="text-4xl font-bold text-white tracking-tight">{plan.price}</span>
                  {plan.price !== '定制' && <span className="text-slate-500 text-sm">/月</span>}
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
                  plan.highlight
                    ? 'bg-[#5558ff] hover:bg-[#4548e6] text-white border-violet-500 shadow-lg shadow-violet-500/20'
                    : 'bg-transparent hover:bg-white/5 text-white border-white/10'
                }`}
              >
                {plan.cta}
              </button>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};
