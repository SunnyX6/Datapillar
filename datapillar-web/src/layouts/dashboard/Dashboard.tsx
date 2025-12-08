import type { ReactNode } from 'react'
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Cpu,
  Database,
  Globe,
  MoreHorizontal,
  RefreshCw,
  Server,
  Sparkles,
  TrendingDown,
  TrendingUp,
  Zap
} from 'lucide-react'
import {
  contentMaxWidthClassMap,
  paddingClassMap,
  containerHeightClassMap,
  progressWidthClassMap,
  gridColsClassMap,
  colSpanClassMap,
  autoRowsClassMap
} from '@/design-tokens/dimensions'
import { RESPONSIVE_TYPOGRAPHY } from '@/design-tokens/typography'

export function Dashboard() {
  return (
    <section className="h-full bg-slate-50 dark:bg-[#0f172a] selection:bg-indigo-500/30">
      <div className="h-full overflow-y-auto custom-scrollbar">
        <div
          className={`${contentMaxWidthClassMap.full} ${paddingClassMap.md} w-full mx-auto space-y-6 @container`}
        >
          <HeaderSection />
          <MetricGrid />
          <InsightGrid />
          <ActivitySection />
        </div>
      </div>
    </section>
  )
}

function HeaderSection() {
  return (
    <div className="flex flex-col gap-4 @md:flex-row @md:items-end @md:justify-between mb-8">
      <div className="space-y-2">
        <div className={`flex items-center gap-2 ${RESPONSIVE_TYPOGRAPHY.badge} text-slate-500 dark:text-slate-400`}>
          <span className="inline-flex items-center px-2 py-0.5 rounded bg-indigo-100 text-indigo-800 dark:bg-indigo-500/20 dark:text-indigo-300 font-medium">
            PRO Plan
          </span>
          <span className="h-1 w-1 rounded-full bg-slate-300 dark:bg-slate-600" />
          <span>US-EAST-1</span>
        </div>
        <h1 className={`${RESPONSIVE_TYPOGRAPHY.displayTitle} font-bold tracking-tight text-slate-900 dark:text-white`}>Command Center</h1>
        <p className={`text-slate-500 dark:text-slate-400 ${RESPONSIVE_TYPOGRAPHY.sectionTitle} max-w-2xl`}>
          Your data estate is <span className="text-emerald-600 dark:text-emerald-400 font-medium">99.9% healthy</span>. Datapillar AI has optimized 42 pipelines in the last 24h.
        </p>
      </div>
      <div className="flex items-center gap-3 @md:gap-4">
        <div className="hidden @md:flex items-center gap-2 px-3 py-1.5 bg-white dark:bg-slate-800 rounded-lg border border-slate-200 dark:border-slate-700 shadow-sm text-xs font-medium text-slate-600 dark:text-slate-300">
          <Globe size={14} className="text-slate-400" />
          Global View
        </div>
        <button type="button" className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium rounded-lg shadow-lg shadow-indigo-500/20 transition-all hover:scale-[1.02] active:scale-95">
          <RefreshCw size={14} className="animate-spin-slow" />
          <span>Sync Metrics</span>
        </button>
      </div>
    </div>
  )
}

function MetricGrid() {
  return (
    <div className={`${gridColsClassMap.base} gap-4 @lg:gap-5 ${autoRowsClassMap.equal}`}>
      <BentoCard className={`${colSpanClassMap.responsive3} h-full`}>
        <MetricHeader icon={<Activity size={18} className="text-blue-500" />} label="Active Pipelines" trend="+12%" isPositive />
        <MetricValue value="1,240" description="98% Success Rate" trendData={[40, 30, 45, 50, 60, 55, 70, 65, 80]} color="#3b82f6" />
      </BentoCard>

      <BentoCard className={`${colSpanClassMap.responsive3} h-full`}>
        <MetricHeader icon={<Database size={18} className="text-purple-500" />} label="Data Processed (24h)" trend="+5.4%" isPositive />
        <MetricValue value="8.4 PB" description="Delta Lake Storage" trendData={[20, 25, 30, 28, 35, 40, 38, 45, 50]} color="#a855f7" />
      </BentoCard>

      <BentoCard className={`${colSpanClassMap.responsive3} h-full`}>
        <MetricHeader icon={<Zap size={18} className="text-amber-500" />} label="Est. Daily Cost" trend="-18%" isPositive trendLabel="saved" />
        <MetricValue value="$4.2k" description="vs $5.1k projected" trendData={[80, 75, 70, 65, 60, 55, 50, 45, 40]} color="#f59e0b" />
      </BentoCard>
    </div>
  )
}

function InsightGrid() {
  return (
    <div className={`${gridColsClassMap.base} gap-4 @lg:gap-5 ${autoRowsClassMap.dashboard}`}>
      <ImpactCard className={`${colSpanClassMap.leftPanel} row-span-2`} />
      <EfficiencyCard className={`${colSpanClassMap.rightPanel} row-span-2`} />
    </div>
  )
}

function ActivitySection() {
  return (
    <div className={`${gridColsClassMap.base} gap-4 @lg:gap-5`}>
      <ActivityFeed className={colSpanClassMap.full} />
    </div>
  )
}

interface MetricHeaderProps {
  icon: ReactNode
  label: string
  trend: string
  isPositive: boolean
  trendLabel?: string
}

function MetricHeader({ icon, label, trend, isPositive, trendLabel }: MetricHeaderProps) {
  return (
    <div className="flex justify-between items-start">
      <div className="flex items-center gap-3">
        <div className="p-2 bg-slate-50 dark:bg-slate-800 rounded-lg text-slate-600 dark:text-slate-300 ring-1 ring-slate-900/5 dark:ring-white/10">
          {icon}
        </div>
        <span className={`${RESPONSIVE_TYPOGRAPHY.label} font-medium text-slate-500 dark:text-slate-400`}>{label}</span>
      </div>
      <div className={`flex items-center gap-1 ${RESPONSIVE_TYPOGRAPHY.badge} font-semibold px-2 py-1 rounded-full ${isPositive ? 'text-emerald-600 bg-emerald-50 dark:bg-emerald-500/10' : 'text-rose-600 bg-rose-50 dark:bg-rose-500/10'}`}>
        {isPositive ? <TrendingUp size={12} /> : <TrendingDown size={12} />}
        {trend} {trendLabel && <span className="font-normal opacity-80 ml-0.5">{trendLabel}</span>}
      </div>
    </div>
  )
}

interface MetricValueProps {
  value: string
  description: string
  trendData: number[]
  color: string
}

function MetricValue({ value, description, trendData, color }: MetricValueProps) {
  return (
    <div className="mt-4 flex items-end justify-between">
      <div>
        <p className={`${RESPONSIVE_TYPOGRAPHY.metricValue} font-bold text-slate-900 dark:text-white tracking-tight`}>{value}</p>
        <p className="text-xs text-slate-500 mt-1">{description}</p>
      </div>
      <Sparkline data={trendData} color={color} />
    </div>
  )
}

interface SparklineProps {
  data: number[]
  color: string
}

function Sparkline({ data, color }: SparklineProps) {
  const max = Math.max(...data)
  const min = Math.min(...data)
  const range = max - min || 1
  const width = 100
  const height = 40
  const step = width / (data.length - 1)
  const points = data
    .map((value, index) => {
      const x = index * step
      const y = height - ((value - min) / range) * height
      return `${x},${y}`
    })
    .join(' ')

  return (
    <svg width="80" height="30" viewBox={`0 0 ${width} ${height}`} className="overflow-visible opacity-80">
      <polyline points={points} fill="none" stroke={color} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

interface DashboardGridItemProps {
  className?: string
}

function ImpactCard({ className = '' }: DashboardGridItemProps) {
  return (
    <div className={`bg-gradient-to-b from-indigo-600 to-violet-700 rounded-2xl p-5 @md:p-6 text-white relative overflow-hidden flex flex-col justify-between group shadow-xl shadow-indigo-500/10 h-full ${className}`}>
      <div className="absolute top-0 right-0 w-48 h-48 @md:w-56 @md:h-56 @lg:w-64 @lg:h-64 bg-white/10 rounded-full blur-3xl transform translate-x-1/3 -translate-y-1/3 pointer-events-none group-hover:bg-white/15 transition-colors duration-500" />
      <div>
        <div className="flex items-center gap-3 mb-6">
          <div className="p-2 bg-white/10 rounded-lg backdrop-blur-sm">
            <Sparkles size={18} className="text-indigo-200" />
          </div>
          <span className={`font-semibold tracking-wide ${RESPONSIVE_TYPOGRAPHY.subtitle}`}>AI Impact Report</span>
        </div>
        <div className="space-y-6">
          <ImpactRow label="Engineering Hours Saved" value="420h" barClass="bg-emerald-400" width="medium" />
          <ImpactRow label="Query Optimization" value="12x Faster" barClass="bg-amber-400" width="low" />
          <ImpactRow label="Auto-Healing Events" value="842" barClass="bg-blue-400" width="high" />
        </div>
      </div>
      <div className="mt-8">
        <div className="p-4 bg-white/10 rounded-xl backdrop-blur-md border border-white/10 hover:bg-white/20 transition-colors">
          <div className="flex items-start gap-3">
            <span className="w-2 h-2 mt-1.5 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)] animate-pulse" />
            <div>
              <p className={`${RESPONSIVE_TYPOGRAPHY.subtitle} font-medium text-white`}>Recommendation</p>
              <p className="text-legal text-indigo-200 mt-1 leading-relaxed">
                Snowflake warehouse "COMPUTE_WH_XL" is idle 40% of the time. Downgrade to "L" to save $1,200/mo.
              </p>
            </div>
          </div>
        </div>
        <button type="button" className={`w-full mt-4 py-2.5 bg-white text-indigo-600 font-semibold rounded-lg ${RESPONSIVE_TYPOGRAPHY.subtitle} hover:bg-indigo-50 transition-colors shadow-lg`}>
          Apply Optimization
        </button>
      </div>
    </div>
  )
}

interface ImpactRowProps {
  label: string
  value: string
  barClass: string
  width: keyof typeof progressWidthClassMap
}

function ImpactRow({ label, value, barClass, width }: ImpactRowProps) {
  return (
    <div>
      <div className={`flex justify-between ${RESPONSIVE_TYPOGRAPHY.badge} mb-2 text-indigo-100`}>
        <span>{label}</span>
        <span className="text-sm font-semibold text-white">{value}</span>
      </div>
      <div className="w-full bg-black/20 h-2 rounded-full overflow-hidden backdrop-blur-sm">
        <div className={`h-full ${barClass} ${progressWidthClassMap[width]} rounded-full shadow-[0_0_10px_rgba(255,255,255,0.2)]`} />
      </div>
    </div>
  )
}

function EfficiencyCard({ className = '' }: DashboardGridItemProps) {
  return (
    <BentoCard className={`${containerHeightClassMap.compact} @md:${containerHeightClassMap.normal} h-full ${className}`}>
      <div className="flex flex-col gap-3 @md:flex-row @md:items-center @md:justify-between mb-5">
        <div>
          <h3 className={`font-semibold text-slate-900 dark:text-white flex items-center gap-2 ${RESPONSIVE_TYPOGRAPHY.sectionTitle}`}>
            <Cpu size={18} className="text-slate-400" />
            Compute Efficiency
          </h3>
          <p className="text-xs text-slate-500 mt-1">Token consumption vs. Pipeline throughput</p>
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          <LegendItem color="bg-indigo-500" label="Tokens (M)" />
          <LegendItem color="bg-emerald-400" label="Throughput (GB/s)" />
          <select className={`ml-auto @md:ml-2 bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 ${RESPONSIVE_TYPOGRAPHY.badge} rounded-md px-2 py-1 text-slate-600 dark:text-slate-300 focus:ring-0 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors`}>
            <option>Last 24 Hours</option>
            <option>Last 7 Days</option>
          </select>
        </div>
      </div>
      <div className="w-full h-56 @md:h-60 relative -ml-2 @md:-ml-4 @lg:-ml-8 overflow-visible">
        <Chart />
        <div className="absolute top-[30%] left-[60%] w-px h-3/4 bg-slate-400/50 border-r border-dashed border-slate-400 dark:border-slate-500 pointer-events-none">
          <span className="absolute -top-1 -left-1 w-2 h-2 rounded-full bg-indigo-500 ring-4 ring-indigo-500/20" />
        <div className="absolute top-4 left-2 bg-slate-900 dark:bg-white text-white dark:text-slate-900 text-micro px-2 py-1 rounded shadow-lg whitespace-nowrap z-10">
          <span className="font-bold">Optimization Event</span>
          <br />
          <span className="opacity-80">Latency -40ms</span>
        </div>
        </div>
      </div>
    </BentoCard>
  )
}

function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-2">
      <span className={`w-3 h-3 rounded-full ${color} shadow-sm`} />
      <span className={`${RESPONSIVE_TYPOGRAPHY.legend} text-slate-600 dark:text-slate-400`}>{label}</span>
    </div>
  )
}

function Chart() {
  return (
    <svg className="w-full h-full overflow-visible" viewBox="0 0 100 50" preserveAspectRatio="none">
      <defs>
        <linearGradient id="tokenGradient" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#6366f1" stopOpacity="0.2" />
          <stop offset="100%" stopColor="#6366f1" stopOpacity="0" />
        </linearGradient>
        <linearGradient id="throughputGradient" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#34d399" stopOpacity="0.2" />
          <stop offset="100%" stopColor="#34d399" stopOpacity="0" />
        </linearGradient>
      </defs>
      {[10, 20, 30, 40].map((y) => (
        <line key={y} x1="0" y1={y} x2="100" y2={y} stroke="#94a3b8" strokeOpacity="0.1" strokeWidth="0.1" />
      ))}
      <path d="M0,45 C10,42 20,35 30,38 C40,41 50,25 60,30 C70,35 80,20 90,15 L100,10 L100,50 L0,50 Z" fill="url(#tokenGradient)" />
      <path d="M0,45 C10,42 20,35 30,38 C40,41 50,25 60,30 C70,35 80,20 90,15 L100,10" fill="none" stroke="#6366f1" strokeWidth="0.5" strokeLinecap="round" />
      <path d="M0,48 C15,45 25,46 35,40 C45,34 55,38 65,25 C75,12 85,15 95,8 L100,5 L100,50 L0,50 Z" fill="url(#throughputGradient)" />
      <path d="M0,48 C15,45 25,46 35,40 C45,34 55,38 65,25 C75,12 85,15 95,8 L100,5" fill="none" stroke="#34d399" strokeWidth="0.5" strokeLinecap="round" strokeDasharray="1,1" />
    </svg>
  )
}

function ActivityFeed({ className = '' }: DashboardGridItemProps) {
  return (
    <BentoCard className={`${containerHeightClassMap.compact} overflow-hidden h-full ${className}`}>
      <div className="flex flex-col gap-3 @md:flex-row @md:items-center @md:justify-between mb-4">
        <h3 className={`font-semibold text-slate-900 dark:text-white flex items-center gap-2 ${RESPONSIVE_TYPOGRAPHY.sectionTitle}`}>
          <MoreHorizontal size={18} className="text-slate-400" />
          Live Activity Feed
        </h3>
        <div className="flex items-center gap-2 text-xs text-slate-500 font-medium">
          <span className="flex h-2 w-2 relative">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
            <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500" />
          </span>
          Monitoring Real-time
        </div>
      </div>
      <div className="w-full overflow-x-auto">
        <table className={`w-full text-left ${RESPONSIVE_TYPOGRAPHY.badge}`}>
          <thead>
            <tr className={`border-b border-slate-100 dark:border-slate-700/50 ${RESPONSIVE_TYPOGRAPHY.tableHeader} uppercase text-slate-500 font-medium`}>
              <th className="pb-3 pl-2">Status</th>
              <th className="pb-3">Event</th>
              <th className="pb-3">Pipeline / Source</th>
              <th className="pb-3">Duration</th>
              <th className="pb-3 text-right pr-4">Time</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-50 dark:divide-slate-800/50">
            <ActivityRow status="running" event="ETL Sync Started" source="PostgreSQL_Orders_DB" duration="2m 14s" time="Just now" />
            <ActivityRow status="success" event="AI Schema Healing" source="Salesforce_Contacts_V2" duration="450ms" time="5 mins ago" tag="Auto-Fixed" />
            <ActivityRow status="warning" event="High Latency Warning" source="Kafka_Clickstream" duration="-" time="12 mins ago" />
            <ActivityRow status="success" event="Warehouse Suspended" source="Snowflake_Compute_WH" duration="-" time="1 hr ago" tag="Cost Saving" />
          </tbody>
        </table>
      </div>
    </BentoCard>
  )
}

type ActivityStatus = 'success' | 'warning' | 'running' | 'idle'

interface ActivityRowProps {
  status: ActivityStatus
  event: string
  source: string
  duration: string
  time: string
  tag?: string
}

function ActivityRow({ status, event, source, duration, time, tag }: ActivityRowProps) {
  const icon = getStatusIcon(status)
  return (
    <tr className="group hover:bg-slate-50 dark:hover:bg-slate-800/30 transition-colors">
      <td className="py-3 pl-2">
        <div className="flex items-center gap-2">{icon}</div>
      </td>
      <td className={`py-3 font-medium text-slate-700 dark:text-slate-200 ${RESPONSIVE_TYPOGRAPHY.badge}`}>
        {event}
        {tag && <span className={`ml-2 ${RESPONSIVE_TYPOGRAPHY.tag} px-1.5 py-0.5 rounded bg-indigo-50 dark:bg-indigo-500/20 text-indigo-600 dark:text-indigo-300 font-bold uppercase tracking-wider`}>{tag}</span>}
      </td>
      <td className={`py-3 text-slate-500 ${RESPONSIVE_TYPOGRAPHY.badge}`}>{source}</td>
      <td className={`py-3 text-slate-400 font-mono ${RESPONSIVE_TYPOGRAPHY.tableHeader}`}>{duration}</td>
      <td className={`py-3 text-right pr-4 text-slate-400 ${RESPONSIVE_TYPOGRAPHY.tableHeader}`}>{time}</td>
    </tr>
  )
}

function getStatusIcon(status: ActivityStatus) {
  switch (status) {
    case 'success':
      return <CheckCircle2 size={16} className="text-emerald-500" />
    case 'warning':
      return <AlertTriangle size={16} className="text-amber-500" />
    case 'running':
      return <RefreshCw size={16} className="text-blue-500 animate-spin" />
    default:
      return <Server size={16} className="text-slate-400" />
  }
}

interface BentoCardProps {
  children: ReactNode
  className?: string
}

function BentoCard({ children, className = '' }: BentoCardProps) {
  return (
    <div className={`bg-white dark:bg-[#1e293b] rounded-2xl p-5 @lg:p-6 border border-slate-200 dark:border-slate-700/60 shadow-sm hover:shadow-lg transition-all duration-300 dark:shadow-black/20 flex flex-col ${className}`}>
      {children}
    </div>
  )
}
