import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import { Sparkles, BarChart3, Search, Code2, ArrowRight } from 'lucide-react'
import { SmartVisualizationChart } from '../components/SmartVisualizationChart'

const DEMO_STEP_CONFIGS = [
  { id: 'ask', icon: <Search className="w-5 h-5" />, duration: 4000 },
  { id: 'sql', icon: <Code2 className="w-5 h-5" />, duration: 3000 },
  { id: 'visualize', icon: <BarChart3 className="w-5 h-5" />, duration: 5000 }
]

export function AIAnalyticsDemo() {
  const { t, i18n } = useTranslation('home')
  const demoSteps = useMemo(() => {
    const localized = t('analyticsDemo.steps', { returnObjects: true }) as Array<{
      id: string
      title: string
      description: string
    }>
    return DEMO_STEP_CONFIGS.map((config) => {
      const match = localized.find((item) => item.id === config.id)
      return {
        ...config,
        title: match?.title ?? config.id,
        description: match?.description ?? ''
      }
    })
  }, [t, i18n.language])

  const [activeStep, setActiveStep] = useState(0)
  const [progress, setProgress] = useState(0)

  useEffect(() => {
    const stepDuration = demoSteps[activeStep]?.duration ?? 3000
    const intervalTime = 50
    const steps = stepDuration / intervalTime
    let currentStep = 0

    const timer = window.setInterval(() => {
      currentStep += 1
      const newProgress = (currentStep / steps) * 100

      if (newProgress >= 100) {
        setProgress(0)
        setActiveStep((prev) => (prev + 1) % demoSteps.length)
        currentStep = 0
      } else {
        setProgress(newProgress)
      }
    }, intervalTime)

    return () => window.clearInterval(timer)
  }, [activeStep, demoSteps])

  return (
    <section id="solutions" className="py-24 bg-[#020410] relative overflow-hidden">
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[50rem] h-[32rem] bg-cyan-900/10 blur-[100px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-8 relative z-10">
        <div className="text-center mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-950/30 border border-cyan-500/30 text-cyan-400 text-xs font-mono mb-6">
            <Sparkles className="w-3 h-3" />
            <span>{t('analyticsDemo.badge')}</span>
          </div>
          <h2 className="text-5xl font-bold text-white mb-6">
            {t('analyticsDemo.title')}<br />
            <span className="text-slate-500">{t('analyticsDemo.subtitle')}</span>
          </h2>
        </div>

        <div className="max-w-5xl mx-auto">
          <div className="bg-[#0b0f19] border border-slate-800 rounded-2xl overflow-hidden shadow-2xl min-h-[500px] relative flex flex-col">
            <div className="h-10 bg-[#151b2e] border-b border-slate-800 flex items-center px-4 gap-2">
              <div className="w-3 h-3 rounded-full bg-red-500/20 border border-red-500/50" />
              <div className="w-3 h-3 rounded-full bg-yellow-500/20 border border-yellow-500/50" />
              <div className="w-3 h-3 rounded-full bg-green-500/20 border border-green-500/50" />
              <div className="ml-auto text-micro text-slate-500 font-mono">{t('analyticsDemo.analysisMode')}</div>
            </div>

            <div className="flex-1 p-12 flex items-center justify-center relative bg-gradient-to-b from-[#0b0f19] to-[#0f1623]">
              <div
                className={`absolute inset-0 flex items-center justify-center transition-all duration-700 transform ${
                  activeStep === 0 ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-95 translate-y-8 pointer-events-none'
                }`}
              >
                <div className="w-full max-w-2xl">
                  <div className="flex items-center gap-4 mb-8 justify-center">
                    <div className="w-12 h-12 rounded-full bg-gradient-to-tr from-cyan-500 to-violet-500 flex items-center justify-center shadow-lg shadow-cyan-500/20">
                      <Sparkles className="w-6 h-6 text-white animate-pulse" />
                    </div>
                    <h3 className="text-2xl text-slate-200 font-light">{t('analyticsDemo.promptTitle')}</h3>
                  </div>
                  <div className="relative group">
                    <input
                      type="text"
                      readOnly
                      value={t('analyticsDemo.promptValue')}
                      className="w-full bg-[#151b2e] border border-slate-700 text-slate-200 text-lg rounded-2xl px-6 py-5 pl-14 shadow-xl focus:outline-none focus:border-cyan-500/50 transition-colors"
                    />
                    <Search className="absolute left-5 top-1/2 -translate-y-1/2 text-slate-500 w-6 h-6" />
                    <div className="absolute right-4 top-1/2 -translate-y-1/2">
                      <button className="bg-cyan-600 hover:bg-cyan-500 text-white p-2 rounded-xl transition-colors shadow-lg shadow-cyan-900/20">
                        <ArrowRight className="w-5 h-5" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <div
                className={`absolute inset-0 flex items-center justify-center transition-all duration-700 transform ${
                  activeStep === 1 ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-95 translate-y-8 pointer-events-none'
                }`}
              >
                <div className="w-full max-w-3xl bg-[#020410] border border-slate-700 rounded-lg shadow-2xl overflow-hidden font-mono text-sm">
                  <div className="bg-[#1e293b] px-4 py-2 flex justify-between items-center border-b border-slate-700">
                    <span className="text-cyan-400 text-xs flex items-center gap-2">
                      <Code2 className="w-3 h-3" /> {t('analyticsDemo.sqlTitle')}
                    </span>
                    <span className="text-micro text-slate-500">{t('analyticsDemo.sqlExecution')}</span>
                  </div>
                  <div className="p-6 space-y-2">
                    <div className="flex">
                      <span className="text-slate-600 select-none mr-4">1</span>
                      <span className="text-violet-400">SELECT</span>
                    </div>
                    <div className="flex">
                      <span className="text-slate-600 select-none mr-4">2</span>
                      <span className="text-white pl-4">
                        DATE(order_date) <span className="text-violet-400">AS</span> day,
                      </span>
                    </div>
                    <div className="flex">
                      <span className="text-slate-600 select-none mr-4">3</span>
                      <span className="text-white pl-4">
                        <span className="text-yellow-400">SUM</span>(total_amount) <span className="text-violet-400">AS</span> revenue
                      </span>
                    </div>
                    <div className="flex">
                      <span className="text-slate-600 select-none mr-4">4</span>
                      <span className="text-violet-400">FROM</span> <span className="text-green-400">orders</span>
                    </div>
                    <div className="flex">
                      <span className="text-slate-600 select-none mr-4">5</span>
                      <span className="text-violet-400">WHERE</span>
                    </div>
                    <div className="flex">
                      <span className="text-slate-600 select-none mr-4">6</span>
                      <span className="text-white pl-4">
                        region = <span className="text-orange-400">'East_China'</span>
                      </span>
                    </div>
                    <div className="flex">
                      <span className="text-slate-600 select-none mr-4">7</span>
                      <span className="text-white pl-4">
                        <span className="text-violet-400">AND</span> order_date &gt;= <span className="text-violet-400">CURRENT_DATE</span> - INTERVAL
                        <span className="text-orange-400"> '30 days'</span>
                      </span>
                    </div>
                    <div className="flex">
                      <span className="text-slate-600 select-none mr-4">8</span>
                      <span className="text-violet-400">GROUP BY</span> <span className="text-white">1</span>
                    </div>
                    <div className="flex">
                      <span className="text-slate-600 select-none mr-4">9</span>
                      <span className="text-violet-400">ORDER BY</span> <span className="text-white">1;</span>
                    </div>
                  </div>
                </div>
              </div>

              <div
                className={`absolute inset-0 flex items-center justify-center p-12 transition-all duration-700 transform ${
                  activeStep === 2 ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-95 translate-y-8 pointer-events-none'
                }`}
              >
                <SmartVisualizationChart isActive={activeStep === 2} />
              </div>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4 mt-8">
            {demoSteps.map((step, index) => {
              const isActive = index === activeStep
              const progressStyle: CSSProperties = {
                '--progress-width': isActive ? `${progress}%` : '0%',
                '--progress-opacity': isActive ? 1 : 0
              }

              return (
                <button
                  key={step.id}
                  onClick={() => {
                    setActiveStep(index)
                    setProgress(0)
                  }}
                  className={`text-left group relative p-4 rounded-xl transition-all duration-300 ${
                    isActive ? 'bg-white/5' : 'hover:bg-white/5'
                  }`}
                >
                  <div className="absolute bottom-0 left-0 w-full h-0.5 bg-slate-800 rounded-b-xl overflow-hidden">
                    <div className="h-full bg-cyan-400 transition-all duration-75 ease-linear ai-demo-progress" style={progressStyle} />
                  </div>

                  <div className="flex items-start gap-4">
                    <div
                      className={`p-2 rounded-lg transition-colors ${
                        isActive ? 'bg-cyan-500 text-white shadow-lg shadow-cyan-500/30' : 'bg-slate-800 text-slate-500 group-hover:text-slate-300'
                      }`}
                    >
                      {step.icon}
                    </div>
                    <div>
                      <h4 className={`text-sm font-bold mb-1 transition-colors ${isActive ? 'text-white' : 'text-slate-400 group-hover:text-slate-200'}`}>
                        {step.title}
                      </h4>
                      <p className={`text-xs leading-relaxed transition-colors ${isActive ? 'text-cyan-100/70' : 'text-slate-600'}`}>
                        {step.description}
                      </p>
                    </div>
                  </div>
                </button>
              )
            })}
          </div>
        </div>
      </div>
    </section>
  )
}
