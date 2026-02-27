import { useEffect, useState } from 'react'
import type { LucideIcon } from 'lucide-react'
import {
  Activity,
  ArrowRight,
  BarChart3,
  Check,
  Database,
  Filter,
  Globe,
  Layers,
  LayoutGrid,
  Plus,
  Server,
  Signal,
  Users,
  Workflow,
  Zap
} from 'lucide-react'
import { toast } from 'sonner'
import { Button, Card, Modal, ModalCancelButton, ModalPrimaryButton } from '@/components/ui'
import { contentMaxWidthClassMap } from '@/design-tokens/dimensions'
import { DWStackOverView } from './stack/DWStackOverView'
import type { StackEnv } from './stack/types'
import type { ModuleType, ProjectModule, ProjectTemplateType } from '../utils/projectTemplateModules'
import { buildInitialModulesFromTemplates } from '../utils/projectTemplateModules'

interface ProjectSummary {
  id: string
  name: string
  description: string
  env: StackEnv
  health: number
  dailyExecutions: string
  teamMembers: number
  lastActivity: string
  tags: string[]
  modules: ProjectModule[]
}

const initialProjects: ProjectSummary[] = [
  {
    id: 'p1',
    name: 'Core_Trade_Engine',
    description: '核心交易链路数据治理与风控计算。',
    env: 'PROD',
    health: 98,
    dailyExecutions: '1.2M',
    teamMembers: 12,
    lastActivity: '2m ago',
    tags: ['Financial', 'Core'],
    modules: [
      { type: 'offline', name: '离线数仓', status: 'active', stats: '24 pipelines', load: 45 },
      { type: 'realtime', name: '实时风控', status: 'active', stats: '8 jobs', load: 82 },
      { type: 'serving', name: '数据 API', status: 'active', stats: '99.99% uptime', load: 12 }
    ]
  },
  {
    id: 'p2',
    name: 'RAG_Knowledge_Brain',
    description: '企业知识库向量索引构建。',
    env: 'PROD',
    health: 100,
    dailyExecutions: '45k',
    teamMembers: 4,
    lastActivity: '1h ago',
    tags: ['AI', 'Vector'],
    modules: [
      { type: 'vector', name: '向量索引', status: 'active', stats: '1.2M chunks', load: 60 },
      { type: 'offline', name: '清洗管道', status: 'active', stats: '4 flows', load: 20 }
    ]
  },
  {
    id: 'p3',
    name: 'Marketing_Attr_v3',
    description: '多触点归因模型测试环境。',
    env: 'STAGING',
    health: 76,
    dailyExecutions: '200k',
    teamMembers: 8,
    lastActivity: '15m ago',
    tags: ['Marketing'],
    modules: [
      { type: 'offline', name: '离线计算', status: 'active', stats: '15 flows', load: 88 },
      { type: 'bi', name: 'BI 看板', status: 'beta', stats: '12 dashboards', load: 5 }
    ]
  },
  {
    id: 'p4',
    name: 'IoT_Sensor_Hub',
    description: '工厂传感器数据采集。',
    env: 'DEV',
    health: 92,
    dailyExecutions: '5.5M',
    teamMembers: 3,
    lastActivity: '4h ago',
    tags: ['IoT', 'Stream'],
    modules: [{ type: 'realtime', name: '流式计算', status: 'active', stats: '2 jobs', load: 30 }]
  }
]

type ModuleConfig = {
  icon: LucideIcon
  accent: string
  border: string
  hoverBorder: string
  bg: string
  glow: string
  barColor: string
}

const MODULE_CONFIG_MAP: Record<ModuleType, ModuleConfig> = {
  offline: {
    icon: Workflow,
    accent: 'text-brand-600 dark:text-brand-400',
    border: 'border-brand-100 dark:border-brand-800/40',
    hoverBorder: 'hover:border-brand-300 dark:hover:border-brand-600/60',
    bg: 'bg-brand-50/30 dark:bg-brand-900/15',
    glow: 'hover:shadow-[0_4px_20px_-4px_rgba(124,58,237,0.3)]',
    barColor: 'bg-brand-500'
  },
  realtime: {
    icon: Zap,
    accent: 'text-blue-600 dark:text-blue-400',
    border: 'border-blue-100 dark:border-blue-800/40',
    hoverBorder: 'hover:border-blue-300 dark:hover:border-blue-600/60',
    bg: 'bg-blue-50/30 dark:bg-blue-900/15',
    glow: 'hover:shadow-[0_4px_20px_-4px_rgba(59,130,246,0.3)]',
    barColor: 'bg-blue-500'
  },
  vector: {
    icon: Database,
    accent: 'text-purple-600 dark:text-purple-400',
    border: 'border-purple-100 dark:border-purple-800/40',
    hoverBorder: 'hover:border-purple-300 dark:hover:border-purple-600/60',
    bg: 'bg-purple-50/30 dark:bg-purple-900/15',
    glow: 'hover:shadow-[0_4px_20px_-4px_rgba(147,51,234,0.3)]',
    barColor: 'bg-purple-500'
  },
  serving: {
    icon: Globe,
    accent: 'text-emerald-600 dark:text-emerald-400',
    border: 'border-emerald-100 dark:border-emerald-800/40',
    hoverBorder: 'hover:border-emerald-300 dark:hover:border-emerald-600/60',
    bg: 'bg-emerald-50/30 dark:bg-emerald-900/15',
    glow: 'hover:shadow-[0_4px_20px_-4px_rgba(16,185,129,0.3)]',
    barColor: 'bg-emerald-500'
  },
  bi: {
    icon: LayoutGrid,
    accent: 'text-orange-600 dark:text-orange-400',
    border: 'border-orange-100 dark:border-orange-800/40',
    hoverBorder: 'hover:border-orange-300 dark:hover:border-orange-600/60',
    bg: 'bg-orange-50/30 dark:bg-orange-900/15',
    glow: 'hover:shadow-[0_4px_20px_-4px_rgba(249,115,22,0.3)]',
    barColor: 'bg-orange-500'
  }
}

const envStyles: Record<StackEnv, { bar: string; border: string; hoverBorder: string; fade: string; badge: string; glow: string }> = {
  PROD: {
    bar: 'bg-emerald-500',
    border: 'border-emerald-500/25 dark:border-emerald-500/20',
    hoverBorder: 'hover:border-emerald-500/45 dark:hover:border-emerald-500/35',
    fade: 'from-emerald-50/60 to-white dark:from-emerald-500/10 dark:to-slate-900',
    badge: 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20',
    glow: 'shadow-emerald-500/20 dark:shadow-emerald-500/30'
  },
  STAGING: {
    bar: 'bg-amber-500',
    border: 'border-amber-500/25 dark:border-amber-500/20',
    hoverBorder: 'hover:border-amber-500/45 dark:hover:border-amber-500/35',
    fade: 'from-amber-50/60 to-white dark:from-amber-500/10 dark:to-slate-900',
    badge: 'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-500/10 dark:text-amber-200 dark:border-amber-500/20',
    glow: 'shadow-amber-500/20 dark:shadow-amber-500/30'
  },
  DEV: {
    bar: 'bg-blue-500',
    border: 'border-blue-500/25 dark:border-blue-500/20',
    hoverBorder: 'hover:border-blue-500/45 dark:hover:border-blue-500/35',
    fade: 'from-blue-50/60 to-white dark:from-blue-500/10 dark:to-slate-900',
    badge: 'bg-blue-50 text-blue-700 border-blue-100 dark:bg-blue-500/10 dark:text-blue-200 dark:border-blue-500/20',
    glow: 'shadow-blue-500/20 dark:shadow-blue-500/30'
  }
}

const templateOptions: Array<{
  id: ProjectTemplateType
  label: string
  icon: LucideIcon
  desc: string
  color: string
  bg: string
  border: string
  ring: string
}> = [
  {
    id: 'BATCH_ETL',
    label: '离线数仓开发',
    icon: Workflow,
    desc: '分布式批处理（Spark/Hive）',
    color: 'text-brand-600 dark:text-brand-400',
    bg: 'bg-brand-50 dark:bg-brand-900/20',
    border: 'border-brand-200 dark:border-brand-800',
    ring: 'ring-brand-400 dark:ring-brand-500/50'
  },
  {
    id: 'STREAM_PROCESS',
    label: '实时流计算',
    icon: Zap,
    desc: '实时流处理（Flink）',
    color: 'text-blue-600 dark:text-blue-400',
    bg: 'bg-blue-50 dark:bg-blue-900/20',
    border: 'border-blue-200 dark:border-blue-800',
    ring: 'ring-blue-400 dark:ring-blue-500/50'
  },
  {
    id: 'RAG_KNOWLEDGE',
    label: 'RAG 知识库',
    icon: Database,
    desc: '向量检索引擎（RAG）',
    color: 'text-purple-600 dark:text-purple-400',
    bg: 'bg-purple-50 dark:bg-purple-900/20',
    border: 'border-purple-200 dark:border-purple-800',
    ring: 'ring-purple-400 dark:ring-purple-500/50'
  },
  {
    id: 'DATA_SERVICE',
    label: '数据 API 服务',
    icon: Globe,
    desc: '高并发数据服务',
    color: 'text-emerald-600 dark:text-emerald-400',
    bg: 'bg-emerald-50 dark:bg-emerald-900/20',
    border: 'border-emerald-200 dark:border-emerald-800',
    ring: 'ring-emerald-400 dark:ring-emerald-500/50'
  }
]

const envOptions: Array<{ id: StackEnv; color: 'blue' | 'amber' | 'emerald'; desc: string }> = [
  { id: 'DEV', color: 'blue', desc: '沙箱' },
  { id: 'STAGING', color: 'amber', desc: '预生产' },
  { id: 'PROD', color: 'emerald', desc: '线上' }
]

const ProjectCard = ({ project, onEnterModule }: { project: ProjectSummary; onEnterModule: (module: ProjectModule) => void }) => {
  const rackCapacity = 3
  const emptySlots = Math.max(0, rackCapacity - project.modules.length)
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    const timer = window.setTimeout(() => setMounted(true), 100)
    return () => window.clearTimeout(timer)
  }, [])

  const style = envStyles[project.env]

  return (
    <Card
      variant="default"
      padding="none"
      className={`group h-[26rem] rounded-2xl border border-t-0 ${style.border} ${style.hoverBorder} shadow-[0_2px_8px_-2px_rgba(0,0,0,0.05)] hover:shadow-[0_16px_32px_-8px_rgba(0,0,0,0.12)] transition-all duration-500 overflow-hidden relative transform hover:-translate-y-1`}
    >
      <div className="relative flex h-full flex-col overflow-hidden rounded-2xl">
        <div className={`absolute top-0 inset-x-0 h-1.5 ${style.bar} z-50 overflow-hidden`}>
          <div className="absolute inset-0 bg-white/30 dark:bg-slate-900/20 transform -translate-x-full group-hover:translate-x-full transition-transform duration-1000 ease-in-out"></div>
        </div>

        <div className="absolute inset-0 opacity-0 group-hover:opacity-100 pointer-events-none overflow-hidden transition-opacity duration-700">
          <div className="absolute -inset-full top-0 block h-full w-full -skew-x-12 bg-gradient-to-r from-transparent via-white/40 dark:via-slate-200/10 to-transparent -translate-x-full group-hover:animate-[shimmer_1.2s_linear_infinite] z-50"></div>
        </div>

        <div className={`px-6 pt-7 pb-2 relative z-10 bg-gradient-to-b ${style.fade}`}>
          <div className="flex justify-between items-start mb-3">
            <div className="flex items-center space-x-3.5">
              <div className="relative flex-shrink-0 mt-1">
                <div className={`w-2.5 h-2.5 rounded-full ${project.health > 90 ? 'bg-emerald-500' : 'bg-amber-500'} ${style.glow} shadow-sm transition-transform duration-500 group-hover:scale-110`}></div>
                <div className={`absolute -inset-1 rounded-full ${project.health > 90 ? 'bg-emerald-500' : 'bg-amber-500'} opacity-10 animate-ping`}></div>
              </div>

              <div>
                <h3 className="text-body-sm font-semibold text-slate-900 dark:text-slate-100 tracking-tight leading-none group-hover:text-brand-600 dark:group-hover:text-brand-400 transition-colors duration-300">
                  {project.name}
                </h3>
                <div className="flex items-center space-x-3 mt-1.5 text-legal font-mono text-slate-500 dark:text-slate-400 tracking-wide">
                  <span className="font-medium">ID: {project.id}</span>
                  <span className="w-0.5 h-3 bg-slate-300/50 dark:bg-slate-700/60"></span>
                  <span>{project.lastActivity}</span>
                </div>
              </div>
            </div>

            <div className={`px-2.5 py-1 rounded-md text-micro font-bold tracking-wider uppercase border ${style.badge} transition-transform duration-300 group-hover:rotate-1`}>
              {project.env}
            </div>
          </div>

          <p className="text-caption text-slate-500 dark:text-slate-400 leading-relaxed pl-6 pr-2 mb-2 line-clamp-2 h-9 font-medium transition-opacity duration-300 opacity-80 group-hover:opacity-100">
            {project.description}
          </p>
        </div>

        <div className="px-4 pb-4 flex flex-col flex-1 min-h-0 relative">
          <div className="flex-1 min-h-0 overflow-y-auto custom-scrollbar space-y-2 pr-1">
            {project.modules.map((mod, index) => {
              const modStyle = MODULE_CONFIG_MAP[mod.type]
              return (
                <div
                  key={`${project.id}-${mod.type}-${index}`}
                  onClick={() => onEnterModule(mod)}
                  className={`group/mod relative flex items-center justify-between p-3 rounded-xl border bg-white dark:bg-slate-900 cursor-pointer transition-all duration-300 h-[60px] z-10 ${modStyle.border} ${modStyle.hoverBorder} ${modStyle.glow} hover:z-20`}
                >
                  <div className={`absolute inset-0 rounded-xl opacity-0 group-hover/mod:opacity-100 transition-opacity duration-300 pointer-events-none ${modStyle.bg}`}></div>

                  <div className={`absolute left-0.5 top-3 bottom-3 w-1 rounded-full opacity-0 group-hover/mod:opacity-100 transition-all duration-300 ${modStyle.barColor}`}></div>

                  <div className="flex items-center space-x-3.5 pl-3 relative z-10">
                    <div className={`text-slate-400 dark:text-slate-500 group-hover/mod:text-slate-900 dark:group-hover/mod:text-slate-100 transition-colors duration-300 transform group-hover/mod:scale-110 ${modStyle.accent}`}>
                      <modStyle.icon size={18} strokeWidth={2} />
                    </div>
                    <div className="flex flex-col">
                      <span className="text-caption font-semibold text-slate-700 dark:text-slate-200 group-hover/mod:text-slate-900 dark:group-hover/mod:text-white leading-none tracking-tight transition-colors">{mod.name}</span>
                      <span className="text-nano text-slate-400 dark:text-slate-500 mt-1 font-mono group-hover/mod:text-slate-600 dark:group-hover/mod:text-slate-300 transition-colors font-medium">{mod.stats}</span>
                    </div>
                  </div>

                  <div className="flex items-center space-x-4 relative z-10">
                    <div className="flex flex-col items-end w-14 group-hover/mod:opacity-0 transition-opacity duration-200">
                      <div className="flex items-center space-x-1.5 mb-1">
                        <div className={`w-1 h-1 rounded-full ${mod.load > 80 ? 'bg-amber-500' : 'bg-green-500'}`}></div>
                        <span className="text-nano text-slate-400 dark:text-slate-500 font-mono font-medium">负载</span>
                      </div>
	                    <div className="w-full h-1.5 bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden border border-slate-100 dark:border-slate-800">
		                  <div
		                    className={`h-full w-full origin-left rounded-full ${modStyle.barColor} transition-transform duration-1000 ease-out`}
		                    style={{ transform: `scaleX(${mounted ? mod.load / 100 : 0})` }}
		                  ></div>
	                    </div>
                    </div>

                    <div className="w-8 h-8 flex items-center justify-center rounded-lg bg-white dark:bg-slate-900 border border-slate-100 dark:border-slate-800 shadow-sm text-slate-300 dark:text-slate-700 opacity-0 -translate-x-3 group-hover/mod:opacity-100 group-hover/mod:translate-x-0 group-hover/mod:text-slate-700 dark:group-hover/mod:text-slate-200 group-hover/mod:border-slate-200 dark:group-hover/mod:border-slate-700 transition-all duration-300">
                      <ArrowRight size={14} strokeWidth={2.5} />
                    </div>
                  </div>
                </div>
              )
            })}

            {[...Array(emptySlots)].map((_, index) => (
              <div
                key={`${project.id}-ghost-${index}`}
                className="h-[60px] rounded-xl border border-dashed border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/20 flex items-center justify-center relative overflow-hidden opacity-60"
              >
                <div className="flex items-center space-x-2 opacity-50">
                  <div className="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-700"></div>
                  <span className="text-nano font-bold text-slate-400 dark:text-slate-500 uppercase tracking-widest">空闲槽位</span>
                  <div className="w-1 h-1 rounded-full bg-slate-300 dark:bg-slate-700"></div>
                </div>
              </div>
            ))}
          </div>

          <button className="flex items-center justify-center py-2 rounded-xl border border-dashed border-gray-300 dark:border-slate-700 text-micro font-bold text-gray-400 dark:text-slate-500 hover:text-brand-600 dark:hover:text-brand-400 hover:border-brand-300 dark:hover:border-brand-500/60 hover:bg-brand-50/50 dark:hover:bg-brand-900/10 transition-all uppercase tracking-wide h-[44px] mt-2 relative overflow-hidden group/btn hover:shadow-sm">
            <Plus size={12} className="mr-1.5 group-hover/btn:scale-110 transition-transform" />
            配置栈
          </button>
        </div>

        <div className="px-6 py-3 bg-gray-50 dark:bg-slate-950/20 border-t border-gray-200 dark:border-slate-800 flex items-center justify-between mt-auto">
          <div className="flex items-center space-x-4">
            <div className="flex items-center text-micro font-mono text-slate-500 dark:text-slate-400">
              <Signal size={12} className="mr-1.5 text-slate-400 dark:text-slate-500" />
              <span className="font-bold text-slate-700 dark:text-slate-200">{project.dailyExecutions}</span>
            </div>
            <div className="w-px h-3 bg-slate-200 dark:bg-slate-800"></div>
            <div className="flex items-center text-micro font-mono text-slate-500 dark:text-slate-400">
              <Server size={12} className="mr-1.5 text-slate-400 dark:text-slate-500" />
              <span className="font-bold text-slate-700 dark:text-slate-200">12</span>
              <span className="ml-1 text-slate-400 dark:text-slate-500 font-normal">节点</span>
            </div>
          </div>

          <div className="flex items-center">
            <div className="flex -space-x-2">
              {[...Array(Math.min(project.teamMembers, 3))].map((_, index) => (
                <div
                  key={`${project.id}-member-${index}`}
                  className="w-6 h-6 rounded-full border-2 border-white dark:border-slate-900 bg-gray-200 dark:bg-slate-700 flex items-center justify-center text-tiny font-bold text-gray-600 dark:text-slate-200 shadow-sm relative z-0 hover:z-10 transition-all hover:scale-110"
                >
                  {String.fromCharCode(65 + index)}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </Card>
  )
}

export function ProjectView() {
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [selectedModule, setSelectedModule] = useState<ProjectModule | null>(null)
  const [projectList, setProjectList] = useState<ProjectSummary[]>(() => initialProjects)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [newProjectData, setNewProjectData] = useState({
    name: '',
    description: '',
    env: 'DEV' as StackEnv,
    templates: ['BATCH_ETL'] as ProjectTemplateType[]
  })

  const handleCreateProject = () => {
    if (!newProjectData.name.trim()) {
      toast.warning('请先填写项目名称')
      return
    }

    if (newProjectData.templates.length === 0) {
      toast.warning('请至少选择一个技术栈蓝图')
      return
    }

    const initialModules = buildInitialModulesFromTemplates(newProjectData.templates)

    const newProject: ProjectSummary = {
      id: `p${projectList.length + 1}`,
      name: newProjectData.name,
      description: newProjectData.description.trim() || '暂无描述。',
      env: newProjectData.env,
      health: 100,
      dailyExecutions: '0',
      teamMembers: 1,
      lastActivity: '刚刚',
      tags: ['New'],
      modules: initialModules
    }

    setProjectList((prev) => [...prev, newProject])
    setIsCreateModalOpen(false)
    setNewProjectData({ name: '', description: '', env: 'DEV', templates: ['BATCH_ETL'] })
  }

  const handleEnterModule = (project: ProjectSummary, module: ProjectModule) => {
    if (module.type !== 'offline') {
      toast.warning('当前仅支持进入离线数仓栈，其他栈暂未接入。')
      return
    }
    setSelectedProjectId(project.id)
    setSelectedModule(module)
  }

  const handleBackToWorkspace = () => {
    setSelectedProjectId(null)
    setSelectedModule(null)
  }

  const currentProject = projectList.find((project) => project.id === selectedProjectId) ?? null

  if (currentProject && selectedModule) {
    return (
      <DWStackOverView
        projectName={currentProject.name}
        projectEnv={currentProject.env}
        stackName={selectedModule.name}
        onBack={handleBackToWorkspace}
      />
    )
  }

  return (
    <section className="h-full bg-white dark:bg-slate-900 flex flex-col overflow-hidden relative @container">
      <div className="p-4 @md:p-6 @xl:p-8 pb-2 @md:pb-2 @xl:pb-2 flex-shrink-0">
        <div className="flex flex-col gap-3 @md:gap-4">
          <div className="flex items-end">
            <div>
              <h2 className="text-heading @md:text-title @xl:text-display font-black text-slate-900 dark:text-slate-100 tracking-tight flex items-center">
                项目概览
              </h2>
              <p className="text-slate-500 dark:text-slate-400 mt-2 text-body-sm @md:text-body">管理 {projectList.length} 个关键项目，覆盖 PROD / STAGING / DEV。</p>
            </div>
          </div>

          <div className="grid grid-cols-4 gap-2 @md:gap-3">
            {[
              { label: 'Total Projects', value: '4', meta: 'Active', icon: BarChart3, color: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-50 dark:bg-blue-900/30' },
              { label: 'Stack Services', value: '8', meta: 'Running', icon: Layers, color: 'text-purple-600 dark:text-purple-400', bg: 'bg-purple-50 dark:bg-purple-900/30' },
              { label: 'Avg Health Score', value: '92%', meta: 'Stable', icon: Activity, color: 'text-emerald-600 dark:text-emerald-400', bg: 'bg-emerald-50 dark:bg-emerald-900/30' },
              { label: 'Active Members', value: '27', meta: 'Contributors', icon: Users, color: 'text-orange-600 dark:text-orange-400', bg: 'bg-orange-50 dark:bg-orange-900/30' }
            ].map((stat) => (
              <Card
                key={stat.label}
                className="flex items-center justify-between group"
              >
                <div>
                  <div className="text-legal font-semibold uppercase tracking-widest text-slate-500 dark:text-slate-400 mb-1">{stat.label}</div>
                  <div className="text-subtitle @md:text-title font-bold text-slate-900 dark:text-slate-100">
                    {stat.value}
                    {stat.meta ? <span className="ml-2 text-micro @md:text-caption font-semibold text-slate-400 dark:text-slate-500">{stat.meta}</span> : null}
                  </div>
                </div>
                <div className={`w-10 h-10 rounded-lg ${stat.bg} flex items-center justify-center ${stat.color} opacity-80 group-hover:opacity-100 transition-opacity`}>
                  <stat.icon size={20} />
                </div>
              </Card>
            ))}
          </div>

	          <div className="flex items-center justify-end gap-3 pt-1">
	            <Button
	              variant="outline"
	              size="header"
	              className="py-1.5 @md:py-2 border-slate-200 text-slate-600 shadow-sm hover:bg-slate-50 dark:bg-slate-900 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
	            >
	              <Filter size={14} className="text-slate-400 dark:text-slate-500" />
	              筛选
	            </Button>
	            <Button
	              onClick={() => setIsCreateModalOpen(true)}
	              size="header"
	              className="py-1.5 @md:py-2"
	            >
	              <Plus size={14} />
	              创建项目
	            </Button>
	          </div>
	        </div>

      </div>

      <div className="flex-1 overflow-y-auto p-4 @md:p-6 @xl:p-8 pt-0 @md:pt-0 @xl:pt-0 custom-scrollbar">
        <div className={`grid grid-cols-1 @md:grid-cols-3 gap-2 @md:gap-3 w-full ${contentMaxWidthClassMap.full} mx-auto`}>
          {projectList.map((project, index) => (
            <div
              key={project.id}
              className="w-full"
              style={{ animation: `fade-in 0.35s ease-out ${index * 120}ms both` }}
            >
              <ProjectCard project={project} onEnterModule={(module) => handleEnterModule(project, module)} />
            </div>
          ))}

          <div
            className="w-full"
            style={{ animation: `fade-in 0.35s ease-out ${projectList.length * 120}ms both` }}
          >
            <button
              onClick={() => setIsCreateModalOpen(true)}
              className="w-full h-[26rem] border-2 border-dashed border-gray-200 dark:border-slate-800 rounded-2xl p-6 flex flex-col items-center justify-center text-gray-400 dark:text-slate-500 bg-transparent dark:bg-slate-950/20 hover:border-gray-300 hover:text-gray-500 dark:hover:border-slate-700 dark:hover:text-slate-300 hover:bg-gray-50 dark:hover:bg-slate-950/30 transition-all group"
            >
              <div className="w-16 h-16 rounded-full bg-gray-50 dark:bg-slate-800 flex items-center justify-center mb-5 group-hover:scale-110 group-hover:bg-white dark:group-hover:bg-slate-900 group-hover:shadow-md transition-all duration-300">
                <Plus size={28} className="text-gray-300 dark:text-slate-700 group-hover:text-brand-500 dark:group-hover:text-brand-400" />
              </div>
              <span className="font-bold text-body-sm text-gray-600 dark:text-slate-200">创建新项目</span>
		              <span className="text-caption mt-2 text-center max-w-xs text-gray-400 dark:text-slate-500 leading-relaxed">
		                新建一个可配置的环境，支持 ETL、流处理、向量索引或 API 服务模板。
		              </span>
            </button>
          </div>
        </div>
      </div>

      <Modal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        title="创建新项目"
        subtitle={<span className="text-xs text-slate-400 dark:text-slate-500">初始化一个新的工作空间以管理数据流。</span>}
        size="md"
        footerRight={
          <>
            <ModalCancelButton onClick={() => setIsCreateModalOpen(false)}>取消</ModalCancelButton>
            <ModalPrimaryButton onClick={handleCreateProject} disabled={!newProjectData.name.trim() || newProjectData.templates.length === 0}>
              创建项目
            </ModalPrimaryButton>
          </>
        }
      >
        <div className="space-y-5">
          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
              项目名称 <span className="text-red-500">*</span>
            </label>
            <div className="relative">
              <input
                type="text"
                value={newProjectData.name}
                onChange={(event) => setNewProjectData({ ...newProjectData, name: event.target.value })}
                placeholder="e.g. User_Behavior_Analytics_v2"
                className="w-full px-4 py-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm text-slate-900 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
                autoFocus
              />
              <div className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-400 dark:text-slate-500 pointer-events-none font-medium">必填</div>
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">描述</label>
            <textarea
              value={newProjectData.description}
              onChange={(event) => setNewProjectData({ ...newProjectData, description: event.target.value })}
              placeholder="简要描述该项目的用途..."
              rows={2}
              className="w-full px-4 py-2.5 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-sm text-slate-900 dark:text-slate-200 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all resize-none placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>

		          <div className="space-y-2.5">
		            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">技术栈蓝图（可多选）</label>
		            <div className="grid grid-cols-2 gap-3">
			              {templateOptions.map((tpl) => {
			                const isSelected = newProjectData.templates.includes(tpl.id)
			                return (
			                  <Card
			                    key={tpl.id}
			                    variant="default"
			                    padding="none"
		                    className={`overflow-hidden ${
		                      isSelected
		                        ? `${tpl.bg} ${tpl.border} ring-1 ring-offset-0 ${tpl.ring}`
		                        : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-800'
		                    }`}
		                  >
		                    <button
		                      type="button"
		                      onClick={() => {
		                        const nextTemplates = isSelected
		                          ? newProjectData.templates.filter((id) => id !== tpl.id)
		                          : [...newProjectData.templates, tpl.id]
		                        if (nextTemplates.length === 0) {
		                          toast.warning('请至少选择一个技术栈蓝图')
		                        }
		                        setNewProjectData({ ...newProjectData, templates: nextTemplates })
		                      }}
		                      className="relative flex w-full items-start p-3 text-left"
		                    >
	                      <div
	                        className={`p-2 rounded-lg ${
	                          isSelected ? 'bg-white dark:bg-slate-900' : 'bg-slate-100 dark:bg-slate-800'
	                        } ${tpl.color} mr-3`}
	                      >
	                        <tpl.icon size={16} />
	                      </div>
	                      <div>
	                        <div
	                          className={`text-body-xs font-semibold ${
	                            isSelected ? 'text-slate-900 dark:text-slate-100' : 'text-slate-700 dark:text-slate-200'
	                          }`}
	                        >
	                          {tpl.label}
	                        </div>
	                        <div className="text-legal font-medium text-slate-500 dark:text-slate-400 mt-0.5">{tpl.desc}</div>
	                      </div>
	                      {isSelected && (
	                        <div className="absolute top-2 right-2 text-brand-600 dark:text-brand-400">
	                          <Check size={14} className={tpl.color} />
	                        </div>
	                      )}
	                    </button>
	                  </Card>
	                )
	              })}
	            </div>
	          </div>

	          <div className="space-y-2.5">
	            <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">运行环境</label>
	            <div className="grid grid-cols-3 gap-3">
		              {envOptions.map((env) => {
		                const isSelected = newProjectData.env === env.id
		                return (
		                  <Card
		                    key={env.id}
		                    variant="default"
		                    padding="none"
		                    className={`overflow-hidden transition-all duration-200 ${
		                      isSelected
		                        ? env.color === 'emerald'
		                          ? 'bg-emerald-50 border-emerald-500 text-emerald-700 dark:bg-emerald-900/30 dark:border-emerald-500 dark:text-emerald-300'
		                          : env.color === 'amber'
		                            ? 'bg-amber-50 border-amber-500 text-amber-700 dark:bg-amber-900/30 dark:border-amber-500 dark:text-amber-300'
	                            : 'bg-blue-50 border-blue-500 text-blue-700 dark:bg-blue-900/30 dark:border-blue-500 dark:text-blue-300'
	                        : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 hover:border-slate-300 dark:hover:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-800'
	                    }`}
	                  >
	                    <button
	                      type="button"
	                      onClick={() => setNewProjectData({ ...newProjectData, env: env.id })}
	                      className="relative flex w-full flex-col items-center justify-center py-3"
	                    >
	                      {isSelected && (
	                        <div
	                          className={`absolute top-1.5 right-1.5 w-4 h-4 rounded-full flex items-center justify-center ${
	                            env.color === 'emerald'
	                              ? 'bg-emerald-500 text-white'
	                              : env.color === 'amber'
	                                ? 'bg-amber-500 text-white'
	                                : 'bg-blue-500 text-white'
	                          }`}
	                        >
	                          <Check size={10} strokeWidth={4} />
	                        </div>
	                      )}
	                      <span className={`text-body-xs font-semibold ${isSelected ? 'scale-105' : ''}`}>{env.id}</span>
	                      <span className="text-legal font-medium opacity-70 mt-0.5">{env.desc}</span>
	                    </button>
	                  </Card>
	                )
	              })}
	            </div>
	          </div>
        </div>
      </Modal>
    </section>
  )
}
