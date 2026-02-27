import { AlertTriangle, ShieldAlert } from 'lucide-react'
import { Card } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import type { ActiveNode } from './FeatureStep'

interface FeatureStepAuditProps {
  activeNode: ActiveNode
  activeColor: string
  activeIconId: string
  targetModuleId: string
  releaseStageId: 'ga' | 'beta' | 'alpha' | 'deprecated'
  menuWeight: number
  menuBadge: string | null
  cardClassName: string
  sectionTitleClassName: string
}

export function FeatureStepAudit({
  activeNode,
  activeColor,
  activeIconId,
  targetModuleId,
  releaseStageId,
  menuWeight,
  menuBadge,
  cardClassName,
  sectionTitleClassName
}: FeatureStepAuditProps) {
  const releaseStageMeta = {
    ga: { title: '正式版', subtitle: '全量可用' },
    beta: { title: '公测', subtitle: '功能预览' },
    alpha: { title: '内测', subtitle: '开发阶段' },
    deprecated: { title: '废弃', subtitle: '计划下线' }
  } as const

  const stageInfo = releaseStageMeta[releaseStageId]
  return (
    <div className="space-y-4 animate-in slide-in-from-bottom-4 duration-500">
      <div className="space-y-2">
        <div className="px-1">
          <h3 className={sectionTitleClassName}>配置汇总</h3>
        </div>
        <div className="grid grid-cols-12 gap-4">
          <Card variant="default" padding="none" className={cn(cardClassName, 'col-span-7 overflow-hidden shadow-none dark:shadow-none')}>
            <div className="px-4 py-3 border-b border-slate-100 dark:border-slate-800">
              <span className={cn(TYPOGRAPHY.micro, 'text-slate-400 dark:text-slate-500 uppercase tracking-wider font-bold')}>基础属性</span>
            </div>
            <div className="p-4 grid grid-cols-2 gap-4">
              <div className="space-y-1">
                <div className={cn(TYPOGRAPHY.micro, 'text-slate-400 dark:text-slate-500 uppercase tracking-wider')}>显示名称</div>
                <div className={cn(TYPOGRAPHY.legal, 'font-semibold text-slate-700 dark:text-slate-100')}>{activeNode.name}</div>
              </div>
              <div className="space-y-1">
                <div className={cn(TYPOGRAPHY.micro, 'text-slate-400 dark:text-slate-500 uppercase tracking-wider')}>资源 ID</div>
                <div className={cn(TYPOGRAPHY.legal, 'font-mono text-slate-500 dark:text-slate-400')}>{activeNode.id}</div>
              </div>
              <div className="space-y-1 col-span-2">
                <div className={cn(TYPOGRAPHY.micro, 'text-slate-400 dark:text-slate-500 uppercase tracking-wider')}>资源描述</div>
                <div className={cn(TYPOGRAPHY.legal, 'text-slate-500 dark:text-slate-400')}>
                  {activeNode.description || '—'}
                </div>
              </div>
              <div className="space-y-1">
                <div className={cn(TYPOGRAPHY.micro, 'text-slate-400 dark:text-slate-500 uppercase tracking-wider')}>图标标识</div>
                <div className={cn(TYPOGRAPHY.legal, 'text-slate-500 dark:text-slate-400')}>{activeIconId}</div>
              </div>
              <div className="space-y-1">
                <div className={cn(TYPOGRAPHY.micro, 'text-slate-400 dark:text-slate-500 uppercase tracking-wider')}>品牌色</div>
                <div className="flex items-center gap-2">
                  <span className="w-3 h-3 rounded-full border border-slate-200 dark:border-slate-700" style={{ backgroundColor: activeColor }} />
                  <span className={cn(TYPOGRAPHY.legal, 'text-slate-500 dark:text-slate-400')}>{activeColor}</span>
                </div>
              </div>
            </div>
          </Card>

          <Card variant="default" padding="none" className={cn(cardClassName, 'col-span-5 overflow-hidden shadow-none dark:shadow-none')}>
            <div className="px-4 py-3 border-b border-slate-100 dark:border-slate-800">
              <span className={cn(TYPOGRAPHY.micro, 'text-slate-400 dark:text-slate-500 uppercase tracking-wider font-bold')}>发布生命周期</span>
            </div>
            <div className="p-4 space-y-2">
              <div className={cn(TYPOGRAPHY.bodySm, 'font-semibold text-slate-700 dark:text-slate-100')}>{stageInfo.title}</div>
              <div className={cn(TYPOGRAPHY.caption, 'text-slate-400 dark:text-slate-500')}>{stageInfo.subtitle}</div>
            </div>
          </Card>

          <Card variant="default" padding="none" className={cn(cardClassName, 'col-span-12 overflow-hidden shadow-none dark:shadow-none')}>
            <div className="px-4 py-3 border-b border-slate-100 dark:border-slate-800">
              <span className={cn(TYPOGRAPHY.micro, 'text-slate-400 dark:text-slate-500 uppercase tracking-wider font-bold')}>动作清单</span>
            </div>
            <div className="p-4">
              <div className="flex flex-wrap gap-2">
                {(activeNode.actions ?? []).length === 0 ? (
                  <span className={cn(TYPOGRAPHY.legal, 'text-slate-400 dark:text-slate-500')}>暂无动作</span>
                ) : (
                  (activeNode.actions ?? []).map((action) => (
                    <span
                      key={action}
                      className={cn(TYPOGRAPHY.legal, 'px-2 py-1 rounded-md bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-300 font-semibold')}
                    >
                      {action}
                    </span>
                  ))
                )}
              </div>
            </div>
          </Card>

          <Card variant="default" padding="none" className={cn(cardClassName, 'col-span-12 overflow-hidden shadow-none dark:shadow-none')}>
            <div className="px-4 py-3 border-b border-slate-100 dark:border-slate-800">
              <span className={cn(TYPOGRAPHY.micro, 'text-slate-400 dark:text-slate-500 uppercase tracking-wider font-bold')}>侧边栏模拟预览</span>
            </div>
            <div className="p-4">
              <p className={cn(TYPOGRAPHY.caption, 'text-slate-500 dark:text-slate-400 leading-relaxed')}>
                挂载配置决定了该资源在最终用户界面中的可访问入口。更改挂载点不会影响 API 的路径。
              </p>
            </div>
          </Card>
        </div>
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between px-1">
          <h3 className={sectionTitleClassName}>变更差分预览 (Diff Viewer)</h3>
          <span className={cn(TYPOGRAPHY.nano, 'font-black px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-300 dark:border-emerald-500/30')}>
            VALIDATED
          </span>
        </div>
        <Card variant="default" padding="none" className={cn(cardClassName, 'overflow-hidden flex flex-col shadow-none dark:shadow-none')}>
          <div className={cn(TYPOGRAPHY.legal, 'p-4 font-mono leading-relaxed overflow-y-auto h-[200px] custom-scrollbar')}>
            <div className="opacity-60 text-emerald-600 dark:text-emerald-400 mb-2">-- Delta Release Snapshot</div>
            <p className="text-emerald-600 dark:text-emerald-400">
              <span className="text-emerald-500 font-bold">+</span> UPSERT INTO resources (id, name, color, icon) VALUES ('{activeNode.id}',
              '{activeNode.name}', '{activeColor}', '{activeIconId}');
            </p>
            <p className="text-emerald-600 dark:text-emerald-400">
              <span className="text-emerald-500 font-bold">+</span> BIND ROUTE '{activeNode.id}' TO MOUNT_POINT '{targetModuleId}';
            </p>
            <p className="text-emerald-600 dark:text-emerald-400">
              <span className="text-emerald-500 font-bold">+</span> SET NAV_CONFIG (weight: {menuWeight}, badge: '{menuBadge || 'null'}');
            </p>
            {(activeNode.actions ?? []).map((action) => (
              <p key={action} className="text-emerald-600 dark:text-emerald-400">
                <span className="text-emerald-500 font-bold">+</span> REGISTER ACL_ACTION '{activeNode.id}:{action}';
              </p>
            ))}
          </div>
        </Card>
      </div>

      <div className="space-y-2">
        <div className="px-1">
          <h3 className={sectionTitleClassName}>上线声明</h3>
        </div>
        <Card variant="default" padding="none" className={cn(cardClassName, 'flex items-center gap-3 p-4 relative overflow-hidden shadow-none dark:shadow-none')}>
          <div className="absolute top-0 right-0 p-4 opacity-10">
            <ShieldAlert size={80} />
          </div>
          <AlertTriangle size={28} className="text-brand-600 dark:text-brand-300 shrink-0" />
          <div>
            <p className={cn(TYPOGRAPHY.caption, 'text-brand-700/70 dark:text-brand-200/80 leading-relaxed font-medium')}>
              执行发布将同步权限模型至全球节点。此操作不可逆，将影响当前所有活跃会话的鉴权结果。
            </p>
          </div>
        </Card>
      </div>
    </div>
  )
}
