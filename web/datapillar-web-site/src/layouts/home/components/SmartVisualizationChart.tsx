import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import { TrendingUp } from 'lucide-react'
import { cn } from '@/lib/utils'

const CHART_DATA = [
  40, 45, 30, 50, 65, 55, 70, 60, 75, 68, 85, 90, 80, 95, 85, 75, 90, 100, 95, 85, 90, 80, 70, 85, 95, 105, 90, 110
]

const generateSmoothPath = (data: number[]) => {
  const width = 1000
  const height = 100
  const maxVal = Math.max(...data) * 1.1

  const points = data.map((val, i) => ({
    x: (i / (data.length - 1)) * width,
    y: height - (val / maxVal) * height
  }))

  return points.reduce((acc, point, i, pointsArray) => {
    if (i === 0) return `M ${point.x},${point.y}`
    const prev = pointsArray[i - 1]
    const cp1x = prev.x + (point.x - prev.x) / 2
    const cp1y = prev.y
    const cp2x = prev.x + (point.x - prev.x) / 2
    const cp2y = point.y
    return `${acc} C ${cp1x},${cp1y} ${cp2x},${cp2y} ${point.x},${point.y}`
  }, '')
}

interface SmartVisualizationChartProps {
  isActive: boolean
  className?: string
}

export function SmartVisualizationChart({ isActive, className }: SmartVisualizationChartProps) {
  const { t } = useTranslation('home')

  return (
    <div
      className={cn(
        'w-full h-full flex flex-col bg-[#151b2e]/30 border border-slate-700/50 rounded-xl p-6 backdrop-blur-sm transition-all duration-500',
        isActive ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-8',
        className
      )}
    >
      <div className="mb-6 flex justify-between items-start">
        <div>
          <h4 className="text-lg font-bold text-white flex items-center gap-2">
            {t('smartChart.title')}
            <span className="px-2 py-0.5 rounded bg-cyan-900/30 text-cyan-400 text-micro border border-cyan-700/30">{t('smartChart.live')}</span>
          </h4>
          <p className="text-sm text-slate-500 mt-1">
            {t('smartChart.sourceLabel')} <code className="text-slate-400">{t('smartChart.sourceValue')}</code>
          </p>
        </div>
        <div className="text-right">
          <div className="text-2xl font-bold text-white">{t('smartChart.metricValue')}</div>
          <div className="flex items-center justify-end gap-1 text-green-400 text-xs font-medium">
            <TrendingUp className="w-3 h-3" />
            <span>{t('smartChart.metricDelta')}</span>
          </div>
        </div>
      </div>

      <div className="flex-1 flex items-end justify-between gap-1 pb-4 border-b border-l border-slate-700/50 bg-[linear-gradient(rgba(255,255,255,0.02)_1px,transparent_1px)] bg-[size:20px_20px] px-2 relative">
        <div className="absolute inset-0 pointer-events-none flex flex-col justify-between py-4 opacity-10 z-0">
          <div className="w-full h-px bg-slate-400 border-t border-dashed" />
          <div className="w-full h-px bg-slate-400 border-t border-dashed" />
          <div className="w-full h-px bg-slate-400 border-t border-dashed" />
          <div className="w-full h-px bg-transparent" />
        </div>

        <svg className="absolute inset-0 w-full h-full pointer-events-none z-20" viewBox="0 0 1000 100" preserveAspectRatio="none">
          <path
            d={generateSmoothPath(CHART_DATA)}
            fill="none"
            stroke="#22d3ee"
            strokeWidth="2"
            vectorEffect="non-scaling-stroke"
            className="opacity-40 drop-shadow-[0_0_8px_rgba(34,211,238,0.3)]"
          />
        </svg>

        {CHART_DATA.map((val, i) => {
          const heightPercent = Math.min(100, (val / 110) * 100)
          const isPeak = val >= 100
          const isHigh = val > 80

          let gradient = 'linear-gradient(to top, #1e3a8a, #3b82f6)'
          if (isPeak) {
            gradient = 'linear-gradient(to top, #7c3aed, #f472b6)'
          } else if (isHigh) {
            gradient = 'linear-gradient(to top, #0891b2, #22d3ee)'
          }

          const barStyle: CSSProperties = {
            '--bar-scale': isActive ? `${heightPercent / 100}` : '0',
            '--bar-delay': `${i * 30}ms`,
            '--bar-gradient': gradient
          }

          return (
            <div key={i} className="flex-1 h-full flex items-end justify-center relative z-10">
              <div
                className={`w-2.5 rounded-t-sm transition-all duration-300 relative ai-demo-bar ${
                  isPeak ? 'shadow-[0_0_15px_rgba(244,114,182,0.6)]' : ''
                }`}
                style={barStyle}
              />
            </div>
          )
        })}
      </div>

      <div className="mt-3" />
    </div>
  )
}
