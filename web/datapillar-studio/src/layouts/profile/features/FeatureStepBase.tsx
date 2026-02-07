import type { ComponentType } from 'react'
import { CheckCircle2, Clock, FlaskConical, Info, Lock, Plus, ShieldCheck, Zap } from 'lucide-react'
import { Card } from '@/components/ui'
import { menuWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { ActiveNode } from './FeatureStep'

interface FeatureStepBaseProps {
  activeNode: ActiveNode
  activeColor: string
  ActiveIcon: ComponentType<{ size?: number; strokeWidth?: number }>
  onOpenIconModal: () => void
  releaseStageId: 'ga' | 'beta' | 'alpha' | 'deprecated'
  onReleaseStageChange: (value: 'ga' | 'beta' | 'alpha' | 'deprecated') => void
  cardClassName: string
  sectionTitleClassName: string
}

export function FeatureStepBase({
  activeNode,
  activeColor,
  ActiveIcon,
  onOpenIconModal,
  releaseStageId,
  onReleaseStageChange,
  cardClassName,
  sectionTitleClassName
}: FeatureStepBaseProps) {
  const releaseStages = [
    {
      id: 'ga',
      title: '正式版',
      subtitle: '全量可用',
      icon: ShieldCheck,
      tone: 'border-emerald-300/60 bg-emerald-50/60 text-emerald-700 dark:border-emerald-500/40 dark:bg-emerald-500/10 dark:text-emerald-200',
      activeTone: 'border-emerald-400/80 bg-emerald-50/90 text-emerald-800 dark:border-emerald-400/80 dark:bg-emerald-500/15 dark:text-emerald-100'
    },
    {
      id: 'beta',
      title: '公测',
      subtitle: '功能预览',
      icon: FlaskConical,
      tone: 'border-blue-300/60 bg-blue-50/60 text-blue-700 dark:border-blue-500/40 dark:bg-blue-500/10 dark:text-blue-200',
      activeTone: 'border-blue-400/80 bg-blue-50/90 text-blue-800 dark:border-blue-400/80 dark:bg-blue-500/15 dark:text-blue-100'
    },
    {
      id: 'alpha',
      title: '内测',
      subtitle: '开发阶段',
      icon: Zap,
      tone: 'border-amber-300/60 bg-amber-50/60 text-amber-700 dark:border-amber-500/40 dark:bg-amber-500/10 dark:text-amber-200',
      activeTone: 'border-amber-400/80 bg-amber-50/90 text-amber-800 dark:border-amber-400/80 dark:bg-amber-500/15 dark:text-amber-100'
    },
    {
      id: 'deprecated',
      title: '废弃',
      subtitle: '计划下线',
      icon: Clock,
      tone: 'border-rose-300/60 bg-rose-50/60 text-rose-700 dark:border-rose-500/40 dark:bg-rose-500/10 dark:text-rose-200',
      activeTone: 'border-rose-400/80 bg-rose-50/90 text-rose-800 dark:border-rose-400/80 dark:bg-rose-500/15 dark:text-rose-100'
    }
  ] as const
  return (
    <div className="space-y-4 animate-in slide-in-from-right-4 duration-300">
      <div className="space-y-2">
        <div className="px-1">
          <h3 className={sectionTitleClassName}>基础属性</h3>
        </div>
        <Card variant="default" padding="none" className={cn(cardClassName, 'overflow-hidden p-4 shadow-none dark:shadow-none')}>
          <div className="flex gap-6">
            <div className="flex flex-col items-center gap-3 shrink-0">
              <div
                onClick={onOpenIconModal}
                className="w-24 h-24 rounded-2xl border-2 border-dashed border-slate-200 dark:border-slate-700 flex flex-col items-center justify-center transition-all cursor-pointer group shadow-inner relative overflow-hidden"
                style={{ backgroundColor: `${activeColor}08` }}
              >
                <div className="absolute inset-0 opacity-0 group-hover:opacity-100 bg-black/5 dark:bg-white/5 flex items-center justify-center transition-opacity z-10 backdrop-blur-[2px]">
                  <span className={cn(TYPOGRAPHY.tiny, 'uppercase font-black tracking-widest px-3 py-1 rounded-full bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900')}>
                    点击定制
                  </span>
                </div>
                <div style={{ color: activeColor }}>
                  <ActiveIcon size={36} strokeWidth={2} />
                </div>
                <span
                  className={cn(
                    TYPOGRAPHY.nano,
                    'font-black mt-2 uppercase tracking-widest text-slate-400 group-hover:text-slate-600 dark:text-slate-500 dark:group-hover:text-slate-300'
                  )}
                >
                  Feature Icon
                </span>
              </div>
              <span
                className={cn(
                  TYPOGRAPHY.nano,
                  'uppercase font-black px-3 py-0.5 rounded-full bg-slate-100 dark:bg-slate-800 dark:text-slate-200 tracking-widest'
                )}
              >
                {activeNode.nodeType}
              </span>
            </div>
            <div className="flex-1 grid grid-cols-2 gap-x-6 gap-y-4">
              <div className="space-y-2 col-span-2 @md:col-span-1">
                <label
                  className={cn(
                    TYPOGRAPHY.micro,
                    'font-black text-slate-500 dark:text-slate-400 uppercase tracking-widest flex items-center gap-2'
                  )}
                >
                  显示名称 <Info size={10} className="text-slate-300 dark:text-slate-500" />
                </label>
                <input
                  type="text"
                  defaultValue={activeNode.name}
                  className={cn(
                    TYPOGRAPHY.bodyXs,
                    'w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-1.5 font-bold text-slate-900 dark:text-slate-100 focus:ring-4 focus:ring-brand-500/5 dark:focus:ring-brand-400/20 focus:border-brand-500 outline-none'
                  )}
                />
              </div>
              <div className="space-y-2 col-span-2 @md:col-span-1">
                <label className={cn(TYPOGRAPHY.micro, 'font-black text-slate-500 dark:text-slate-400 uppercase tracking-widest')}>
                  资源 ID (SLUG)
                </label>
                <div
                  className={cn(
                    TYPOGRAPHY.legal,
                    'px-3 py-1.5 bg-slate-50 dark:bg-slate-900 border border-slate-100 dark:border-slate-700 rounded-lg text-slate-400 dark:text-slate-500 font-mono flex items-center gap-2'
                  )}
                >
                  <Lock size={12} /> {activeNode.id}
                </div>
              </div>
              <div className="space-y-2 col-span-2">
                <label className={cn(TYPOGRAPHY.micro, 'font-black text-slate-500 dark:text-slate-400 uppercase tracking-widest')}>
                  资源描述
                </label>
                <textarea
                  rows={2}
                  placeholder="详细描述该资源的业务场景与权限边界..."
                  className={cn(
                    TYPOGRAPHY.bodyXs,
                    'w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:ring-4 focus:ring-brand-500/5 dark:focus:ring-brand-400/20 focus:border-brand-500 outline-none resize-none'
                  )}
                />
              </div>
            </div>
          </div>
        </Card>
      </div>

      <div className="space-y-2">
        <div className="px-1">
          <h3 className={sectionTitleClassName}>发布生命周期 (STATUS)</h3>
        </div>
        <div className="flex items-center gap-3 w-fit">
          {releaseStages.map((stage) => {
            const isActive = stage.id === releaseStageId
            const StageIcon = stage.icon
            return (
              <Card
                key={stage.id}
                variant="default"
                padding="none"
                role="button"
                tabIndex={0}
                onClick={() => onReleaseStageChange(stage.id)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    onReleaseStageChange(stage.id)
                  }
                }}
                className={cn(
                  cardClassName,
                  `flex flex-col items-center justify-center gap-1.5 px-3 h-20 transition-colors cursor-pointer ${menuWidthClassMap.compact} shadow-none dark:shadow-none text-center focus:outline-none focus-visible:ring-0 relative`,
                  isActive ? stage.activeTone : stage.tone
                )}
              >
                {isActive && (
                  <div className="absolute top-0 right-0 p-2">
                    <CheckCircle2 size={14} className="text-brand-600 dark:text-brand-300" />
                  </div>
                )}
                <StageIcon size={14} className="opacity-80" />
                <div className="space-y-0.5">
                  <div className={cn(TYPOGRAPHY.bodyXs, 'font-semibold')}>{stage.title}</div>
                  <div className={cn(TYPOGRAPHY.legal, 'opacity-75')}>{stage.subtitle}</div>
                </div>
              </Card>
            )
          })}
        </div>
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between px-1">
          <h3 className={sectionTitleClassName}>动作清单 (Action Manifest)</h3>
          <button
            type="button"
            className={cn(
              TYPOGRAPHY.micro,
              'inline-flex items-center gap-1.5 text-brand-600 dark:text-brand-300 font-semibold hover:text-brand-700 dark:hover:text-brand-200'
            )}
          >
            <Plus size={12} />
            新增动作标识
          </button>
        </div>
        <Card variant="default" padding="none" className={cn(cardClassName, 'overflow-hidden shadow-none dark:shadow-none')}>
          <table className="w-full border-collapse">
            <thead className="bg-slate-50 dark:bg-slate-800/50 text-slate-400 dark:text-slate-400 uppercase tracking-wider border-b border-slate-100 dark:border-slate-700">
              <tr className={cn(TYPOGRAPHY.micro, 'font-bold')}>
                <th className="px-4 py-2 text-left">操作特征描述 (BEHAVIOR)</th>
                <th className="px-4 py-2 text-left">实时审计策略</th>
                <th className="px-4 py-2 text-left">安全威胁影响</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50 dark:divide-slate-800">
              {(activeNode.actions ?? ['ACL:READ', 'ACL:WRITE', 'ACL:MANAGE']).map((action) => (
                <tr key={action} className="hover:bg-slate-50/50 dark:hover:bg-slate-800/40 transition-colors">
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-3">
                      <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: activeColor }} />
                      <code className={cn(TYPOGRAPHY.legal, 'font-black text-slate-700 dark:text-slate-100 font-mono tracking-tight')}>{action}</code>
                    </div>
                    <div className={cn(TYPOGRAPHY.legal, 'text-slate-500 dark:text-slate-400 italic mt-1')}>注册原子业务动作拦截器</div>
                  </td>
                  <td className={cn(TYPOGRAPHY.legal, 'px-4 py-2 text-slate-400 dark:text-slate-500')}>—</td>
                  <td className={cn(TYPOGRAPHY.legal, 'px-4 py-2 text-slate-400 dark:text-slate-500')}>—</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      </div>
    </div>
  )
}
