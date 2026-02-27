import { useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  Activity,
  ArrowUpRight,
  Award,
  Clock3,
  Cpu,
  Globe,
  Layers,
  Mail,
  MessageSquare,
  ShieldCheck,
  Sparkles,
  Users
} from 'lucide-react'
import { Button, Card } from '@/components/ui'
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'
import { useAuthStore } from '@/state'
import { getMyProfile, type StudioUserProfile } from '@/services/studioUserProfileService'

const PROFILE_TAGS = ['Data Reliability', 'AI Copilot', 'Cost Guardrails']

interface StatMetric {
  label: string
  value: string
  subLabel: string
  change: string
  isPositive: boolean
  data: number[]
  color: string
  icon: ReactNode
}

const STAT_METRICS: StatMetric[] = [
  {
    label: 'Active Pipelines',
    value: '28',
    subLabel: '99.1% success',
    change: '+3.8%',
    isPositive: true,
    data: [18, 20, 19, 21, 22, 24, 23, 25, 26],
    color: '#6366f1',
    icon: <Activity size={16} className="text-indigo-500" />
  },
  {
    label: 'AI Assisted Fixes',
    value: '126',
    subLabel: 'Last 30 days',
    change: '+18%',
    isPositive: true,
    data: [4, 6, 5, 8, 10, 9, 11, 13, 12],
    color: '#22c55e',
    icon: <Sparkles size={16} className="text-emerald-500" />
  },
  {
    label: 'Spend vs Budget',
    value: '$82k',
    subLabel: 'Budget $96k',
    change: '-15%',
    isPositive: true,
    data: [95, 92, 91, 90, 88, 86, 84, 83, 82],
    color: '#f59e0b',
    icon: <Cpu size={16} className="text-amber-500" />
  }
]

interface JourneyStep {
  title: string
  detail: string
  time: string
  status: 'done' | 'in-progress' | 'upcoming'
}

const JOURNEY_STEPS: JourneyStep[] = [
  {
    title: 'Zero-copy ingestion fabric',
    detail: 'Kafka, MySQL sources unified into Delta mesh',
    time: '完成于 2 周前',
    status: 'done'
  },
  {
    title: 'AI guardrails rollout',
    detail: '42 anomaly rules monitored by Datapillar AI',
    time: 'Rolling out to finance zone',
    status: 'in-progress'
  },
  {
    title: 'Self-serve governance',
    detail: 'Federated access policies + audit automation',
    time: 'Next milestone · Q4',
    status: 'upcoming'
  }
]

interface ProjectFocus {
  name: string
  owner: string
  health: 'On Track' | 'Watch' | 'Blocked'
  risk: string
}

const PROJECT_FOCUS: ProjectFocus[] = [
  {
    name: 'Composable Data Products',
    owner: 'Team Nexus',
    health: 'On Track',
    risk: 'Latency budget 120ms'
  },
  {
    name: 'Governed AI Studio',
    owner: 'ModelOps Crew',
    health: 'Watch',
    risk: 'GPU quota @ 72%'
  },
  {
    name: 'Usage Based Billing',
    owner: 'RevOps Guild',
    health: 'On Track',
    risk: 'Needs lineage handshake'
  }
]

interface Contact {
  name: string
  role: string
  channel: string
  eta: string
}

const CONTACTS: Contact[] = [
  {
    name: 'Mila Tang',
    role: 'Data Product Lead',
    channel: 'Slack #datagov',
    eta: '响应 < 10 分钟'
  },
  {
    name: 'Raj Patel',
    role: 'ML Platform',
    channel: 'PagerDuty · Platform',
    eta: '24×7 on-call'
  },
  {
    name: 'Aurora Ops',
    role: 'Support Bot',
    channel: 'Email support@datapillar.ai',
    eta: 'SLA 4 小时'
  }
]

export function ProfileLayout() {
  const userId = useAuthStore((state) => state.user?.userId)
  const [profile, setProfile] = useState<StudioUserProfile | null>(null)

  useEffect(() => {
    if (!userId) {
      return
    }

    let isCancelled = false

    const loadProfile = async () => {
      try {
        const response = await getMyProfile()
        if (isCancelled) {
          return
        }
        setProfile(response)
      } catch {
        if (isCancelled) {
          return
        }
        setProfile(null)
      }
    }

    void loadProfile()

    return () => {
      isCancelled = true
    }
  }, [userId])

  return (
    <section className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 selection:bg-indigo-500/30 @container">
      <div className="flex-1 overflow-auto p-4 @md:p-6 @xl:p-8 custom-scrollbar">
        <div className={`${contentMaxWidthClassMap.full} w-full mx-auto flex flex-col gap-4 @md:gap-6 text-body`}>
          <ProfileHeader profile={profile} />
          <div className="grid grid-cols-12 gap-4 @md:gap-6 auto-rows-[minmax(0,1fr)]">
            {STAT_METRICS.map((metric) => (
              <BentoCard key={metric.label} className="col-span-12 @md:col-span-4">
                <StatCard metric={metric} />
              </BentoCard>
            ))}
          </div>
          <div className="grid grid-cols-12 gap-4 @md:gap-6 auto-rows-[minmax(0,1fr)]">
            <BentoCard className="col-span-12 @lg:col-span-6">
              <JourneyTimeline />
            </BentoCard>
            <BentoCard className="col-span-12 @lg:col-span-6">
              <ProjectFocusPanel />
            </BentoCard>
          </div>
          <div className="grid grid-cols-12 gap-4 @md:gap-6 auto-rows-[minmax(0,1fr)]">
            <BentoCard className="col-span-12 @lg:col-span-7">
              <ObjectiveHighlights />
            </BentoCard>
            <BentoCard className="col-span-12 @lg:col-span-5">
              <SupportContacts />
            </BentoCard>
          </div>
        </div>
      </div>
    </section>
  )
}

function ProfileHeader({ profile }: { profile: StudioUserProfile | null }) {
  const profileName = useMemo(() => {
    const nickname = profile?.nickname?.trim()
    if (nickname) {
      return nickname
    }

    const username = profile?.username?.trim()
    if (username) {
      return username
    }

    return 'Sunny Engineer'
  }, [profile?.nickname, profile?.username])

  const profileEmail = useMemo(() => {
    const email = profile?.email?.trim()
    return email || '暂未设置邮箱'
  }, [profile?.email])

  const profilePhone = useMemo(() => {
    const phone = profile?.phone?.trim()
    return phone || '暂未设置手机号'
  }, [profile?.phone])

  return (
    <Card
      padding="md"
      className="rounded-3xl @md:rounded-3xl overflow-hidden"
    >
      <div className="flex flex-col gap-4 @md:gap-5">
        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 @md:gap-5">
          <div className="flex items-start gap-4">
            <div className="w-14 h-14 @md:w-16 @md:h-16 rounded-2xl bg-gradient-to-tr from-indigo-500 via-purple-500 to-rose-500 p-0.5">
              <div className="w-full h-full rounded-[1rem] bg-white dark:bg-[#030712] flex items-center justify-center text-lg font-black text-transparent bg-clip-text bg-gradient-to-br from-slate-900 to-slate-600 dark:from-white dark:to-slate-300">
                {profileName.slice(0, 2).toUpperCase()}
              </div>
            </div>
            <div>
              <p className="text-legal uppercase tracking-[0.35em] text-slate-400 dark:text-slate-500">Principal Builder</p>
              <h1 className="text-heading @md:text-title font-semibold text-slate-900 dark:text-white tracking-tight mt-1">{profileName}</h1>
              <p className="text-body-sm text-slate-500 dark:text-slate-400 mt-2">
                工作邮箱：<span className="font-medium text-slate-700 dark:text-slate-200">{profileEmail}</span>
                {' · '}
                手机：<span className="font-medium text-slate-700 dark:text-slate-200">{profilePhone}</span>
              </p>
              <p className="text-body-sm text-slate-500 dark:text-slate-400 mt-2 max-w-3xl">
                Owns the federated data operating model for <span className="font-medium text-indigo-600 dark:text-indigo-300">Acme Corp</span>, orchestrating ingestion, AI quality guardrails, and governed activation layers.
              </p>
              <div className="flex flex-wrap gap-2 mt-3">
                {PROFILE_TAGS.map((tag) => (
                  <span key={tag} className="px-2.5 py-1 rounded-full bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-200 text-legal font-semibold">
                    {tag}
                  </span>
                ))}
              </div>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" className="px-3 py-1.5 rounded-xl border border-slate-200 dark:border-slate-700 text-body-sm font-medium text-slate-600 dark:text-slate-300 hover:border-indigo-400/60 hover:text-indigo-600 transition-colors flex items-center gap-2">
              <Mail size={14} />
              Share Brief
            </button>
            <Button type="button" variant="primary">
              <ArrowUpRight size={14} />
              Update Profile
            </Button>
          </div>
        </div>
        <div className="grid grid-cols-1 @md:grid-cols-2 @lg:grid-cols-4 gap-3">
          <QuickStat label="Preferred Region" value="us-east-1" icon={<Globe size={14} className="text-emerald-500" />} />
          <QuickStat label="AI Copilot" value="Enabled · Stage 4" icon={<Sparkles size={14} className="text-amber-400" />} />
          <QuickStat label="Delegated Teams" value="7 pods" icon={<Users size={14} className="text-indigo-500" />} />
          <QuickStat label="Escalation SLA" value="< 45 min" icon={<Clock3 size={14} className="text-rose-500" />} />
        </div>
      </div>
    </Card>
  )
}

function QuickStat({ label, value, icon }: { label: string; value: string; icon: ReactNode }) {
  return (
    <div className="flex items-center gap-3 p-2.5 rounded-2xl bg-slate-50 dark:bg-slate-900/40 border border-slate-100 dark:border-white/5">
      <div className="w-8 h-8 rounded-2xl bg-white dark:bg-slate-900 flex items-center justify-center shadow-sm">
        {icon}
      </div>
      <div>
        <p className="text-legal uppercase tracking-widest text-slate-400">{label}</p>
        <p className="text-body-sm font-semibold text-slate-900 dark:text-white">{value}</p>
      </div>
    </div>
  )
}

function StatCard({ metric }: { metric: StatMetric }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-caption uppercase tracking-widest text-slate-400">{metric.label}</p>
          <div className="flex items-end gap-2 mt-1">
            <span className="text-title text-slate-900 dark:text-white">{metric.value}</span>
            <span className="text-legal text-slate-500">{metric.subLabel}</span>
          </div>
        </div>
        <div className={`px-2 py-1 rounded-full text-legal font-semibold ${metric.isPositive ? 'text-emerald-600 bg-emerald-50 dark:bg-emerald-500/10' : 'text-rose-600 bg-rose-50 dark:bg-rose-500/10'}`}>
          {metric.change}
        </div>
      </div>
      <Sparkline data={metric.data} color={metric.color} />
      <div className="flex items-center gap-2 text-slate-500 text-body-sm">
        <div className="w-8 h-8 rounded-2xl bg-slate-100 dark:bg-slate-900 flex items-center justify-center">
          {metric.icon}
        </div>
        <span>Trend monitored by Datapillar AI</span>
      </div>
    </div>
  )
}

function JourneyTimeline() {
  return (
    <div>
      <h3 className="font-semibold text-slate-900 dark:text-white flex items-center gap-2 text-body-sm @md:text-subtitle">
        <ShieldCheck size={18} className="text-indigo-500" />
        Execution Journey
      </h3>
      <p className="text-body-sm text-slate-500 mt-1">Milestones for the unified data operating model.</p>
      <div className="mt-4 space-y-4">
        {JOURNEY_STEPS.map((step, index) => (
          <div key={step.title} className="flex gap-4">
            <div className="flex flex-col items-center">
              <div className={`w-4 h-4 rounded-full border-2 ${step.status === 'done' ? 'bg-emerald-500 border-emerald-500' : step.status === 'in-progress' ? 'bg-amber-400 border-amber-400 animate-pulse' : 'border-slate-400 bg-slate-50'}`} />
              {index !== JOURNEY_STEPS.length - 1 && <div className="flex-1 w-px bg-slate-200 dark:bg-slate-800" />}
            </div>
            <div className="flex-1 bg-slate-50 dark:bg-slate-900/40 border border-slate-100 dark:border-white/5 rounded-2xl p-3">
              <div className="flex items-center justify-between">
                <p className="font-semibold text-slate-900 dark:text-white">{step.title}</p>
                <span className="text-xs text-slate-500">{step.time}</span>
              </div>
              <p className="text-body-sm text-slate-500 mt-1">{step.detail}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function ProjectFocusPanel() {
  return (
    <div>
      <div className="flex items-center justify-between">
        <h3 className="font-semibold text-slate-900 dark:text-white flex items-center gap-2 text-body-sm @md:text-subtitle">
          <Layers size={18} className="text-purple-500" />
          Strategic focus
        </h3>
        <button type="button" className="text-xs font-semibold text-indigo-600 hover:text-indigo-400">View roadmap</button>
      </div>
      <p className="text-body-sm text-slate-500 mt-1">Pods owning impact-critical surfaces.</p>
      <div className="mt-4 space-y-3">
        {PROJECT_FOCUS.map((project) => (
          <div key={project.name} className="p-3 rounded-2xl border border-slate-100 dark:border-white/5 bg-slate-50 dark:bg-slate-900/40">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-semibold text-slate-900 dark:text-white">{project.name}</p>
                <p className="text-xs text-slate-500 mt-0.5">Owner · {project.owner}</p>
              </div>
              <span className={`text-legal font-semibold px-2 py-1 rounded-full ${project.health === 'On Track' ? 'text-emerald-600 bg-emerald-50 dark:bg-emerald-500/10' : project.health === 'Watch' ? 'text-amber-600 bg-amber-50 dark:bg-amber-500/10' : 'text-rose-600 bg-rose-50 dark:bg-rose-500/10'}`}>
                {project.health}
              </span>
            </div>
            <div className="flex items-center gap-2 text-body-sm text-slate-500 mt-3">
              <MessageSquare size={14} className="text-slate-400" />
              {project.risk}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function ObjectiveHighlights() {
  return (
    <div>
      <h3 className="font-semibold text-slate-900 dark:text-white flex items-center gap-2 text-body-sm @md:text-subtitle">
        <Award size={18} className="text-rose-500" />
        Objective Highlights
      </h3>
      <p className="text-body-sm text-slate-500 mt-1">North-star signals tracked for the lead builder.</p>
      <div className="mt-4 grid @md:grid-cols-2 gap-3">
        <HighlightCard
          title="Reliability posture"
          value="99.92%"
          description="Across ingestion, AI guardrails, and activation layers."
          pill="Meets SLO"
        />
        <HighlightCard
          title="Cost avoidance"
          value="$480k"
          description="Spend saved via auto-suspend + GPT optimizer."
          pill="Quarter-to-date"
        />
        <HighlightCard
          title="Team enablement"
          value="46 builders"
          description="Pods onboarded via delegated workspaces."
          pill="Self-serve"
        />
        <HighlightCard
          title="Governance maturity"
          value="Level 4"
          description="Federated controls, audit bots, lineage contracts."
          pill="AI ready"
        />
      </div>
    </div>
  )
}

function HighlightCard({ title, value, description, pill }: { title: string; value: string; description: string; pill: string }) {
  return (
    <div className="p-3 rounded-2xl border border-slate-100 dark:border-white/5 bg-white dark:bg-slate-900/60 shadow-sm">
      <div className="flex items-center justify-between">
        <p className="text-xs uppercase tracking-[0.25em] text-slate-400">{title}</p>
        <span className="text-micro font-semibold px-2 py-0.5 rounded-full bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-200">{pill}</span>
      </div>
      <p className="text-title text-slate-900 dark:text-white mt-3">{value}</p>
      <p className="text-body-sm text-slate-500 mt-1">{description}</p>
    </div>
  )
}

function SupportContacts() {
  return (
    <div>
      <h3 className="font-semibold text-slate-900 dark:text-white flex items-center gap-2 text-body-sm @md:text-subtitle">
        <Users size={18} className="text-sky-500" />
        Support graph
      </h3>
      <p className="text-body-sm text-slate-500 mt-1">Who keeps Sunny unblocked.</p>
      <div className="mt-4 space-y-3">
        {CONTACTS.map((contact) => (
          <div key={contact.name} className="p-3 rounded-2xl border border-slate-100 dark:border-white/5 bg-slate-50 dark:bg-slate-900/40">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-semibold text-slate-900 dark:text-white">{contact.name}</p>
                <p className="text-xs text-slate-500">{contact.role}</p>
              </div>
              <span className="text-legal font-semibold text-slate-500">{contact.eta}</span>
            </div>
            <div className="flex items-center gap-2 text-body-sm text-slate-500 mt-3">
              <MessageSquare size={14} className="text-indigo-500" />
              {contact.channel}
            </div>
          </div>
        ))}
      </div>
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
  const height = 36
  const step = width / (data.length - 1)
  const points = data
    .map((value, index) => {
      const x = index * step
      const y = height - ((value - min) / range) * height
      return `${x},${y}`
    })
    .join(' ')

  return (
    <svg width="100%" height="36" viewBox={`0 0 ${width} ${height}`} className="overflow-visible">
      <polyline points={points} fill="none" stroke={color} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" opacity="0.8" />
    </svg>
  )
}

interface BentoCardProps {
  children: ReactNode
  className?: string
}

function BentoCard({ children, className = '' }: BentoCardProps) {
  return (
    <Card
      padding="md"
      className={`rounded-2xl flex flex-col ${className}`}
    >
      {children}
    </Card>
  )
}
