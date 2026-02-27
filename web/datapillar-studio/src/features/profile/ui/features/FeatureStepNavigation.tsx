import type { ComponentType } from 'react'
import { Box, CheckCircle2, Info, Monitor } from 'lucide-react'
import { Card } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import { FEATURE_SCHEMA } from './featureSchema'
import type { ActiveNode } from './FeatureStep'

interface FeatureStepNavigationProps {
  activeNode: ActiveNode
  activeColor: string
  ActiveIcon: ComponentType<{ size?: number; strokeWidth?: number }>
  targetModuleId: string
  onTargetModuleChange: (value: string) => void
  navPlacement: 'global' | 'sidebar'
  onNavPlacementChange: (value: 'global' | 'sidebar') => void
  cardClassName: string
  sectionTitleClassName: string
}

export function FeatureStepNavigation({
  activeNode,
  activeColor,
  ActiveIcon,
  targetModuleId,
  onTargetModuleChange,
  navPlacement,
  onNavPlacementChange,
  cardClassName,
  sectionTitleClassName
}: FeatureStepNavigationProps) {
  return (
    <div className="grid grid-cols-12 gap-5 animate-in slide-in-from-right-4 duration-300">
      <div className="col-span-5 space-y-4">
        <div className="space-y-2">
          <div className="px-1">
            <h3 className={sectionTitleClassName}>路由</h3>
          </div>
          <div className="bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl p-0 flex items-stretch overflow-hidden font-mono focus-within:border-brand-400 dark:focus-within:border-brand-400/70 focus-within:ring-2 focus-within:ring-brand-500/10 dark:focus-within:ring-brand-500/20">
            <div className={cn(TYPOGRAPHY.micro, 'px-3 py-2 bg-slate-100 dark:bg-slate-800 text-slate-400 dark:text-slate-500 border-r border-slate-200 dark:border-slate-700 flex items-center')}>
              /app/workspace/
            </div>
            <input
              type="text"
              defaultValue={activeNode.id.split('_')[1] ?? activeNode.id}
              className={cn(TYPOGRAPHY.legal, 'flex-1 bg-transparent px-3 py-2 text-brand-600 dark:text-brand-300 outline-none font-black')}
            />
          </div>
        </div>

        <div className="space-y-2">
          <div className="px-1">
            <h3 className={sectionTitleClassName}>父级容器归属</h3>
          </div>
          <div className="grid grid-cols-1 gap-2">
            {FEATURE_SCHEMA.map((mod) => (
              <Card
                key={mod.id}
                variant="default"
                padding="none"
                role="button"
                tabIndex={0}
                onClick={() => onTargetModuleChange(mod.id)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    onTargetModuleChange(mod.id)
                  }
                }}
                className={cn(
                  cardClassName,
                  'p-3 border text-left transition-all relative overflow-hidden cursor-pointer group focus:outline-none focus-visible:ring-0 shadow-none dark:shadow-none',
                  targetModuleId === mod.id
                    ? 'border-brand-400/70 bg-brand-50/30 ring-2 ring-brand-500/10 dark:border-brand-400/50 dark:bg-brand-500/10'
                    : 'border-slate-100 bg-white hover:border-slate-200/80 dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-700'
                )}
              >
                <div className="flex items-center gap-3 relative z-10">
                  <div
                    className={`w-7 h-7 rounded-lg flex items-center justify-center transition-colors ${
                      targetModuleId === mod.id ? 'bg-brand-500 text-white' : 'bg-slate-100 dark:bg-slate-800 text-slate-400 dark:text-slate-500'
                    }`}
                  >
                    <Box size={14} />
                  </div>
                  <div className="min-w-0">
                    <p
                      className={cn(
                        TYPOGRAPHY.legal,
                        `font-black uppercase tracking-tight truncate ${targetModuleId === mod.id ? 'text-brand-900 dark:text-brand-200' : 'text-slate-900 dark:text-slate-100'}`
                      )}
                    >
                      {mod.name}
                    </p>
                    <p className={cn(TYPOGRAPHY.nano, 'text-slate-400 dark:text-slate-500 font-medium mt-0.5')}>
                      {mod.children.length} 个子资源
                    </p>
                  </div>
                </div>
                {targetModuleId === mod.id && (
                  <div className="absolute top-0 right-0 p-2">
                    <CheckCircle2 size={14} className="text-brand-600 dark:text-brand-300" />
                  </div>
                )}
              </Card>
            ))}
          </div>
        </div>

        <div className="space-y-2">
          <div className="px-1">
            <h3 className={sectionTitleClassName}>挂载策略与交互</h3>
          </div>
          <div className="flex items-center gap-2">
            {[
              { id: 'global' as const, label: '全局导航' },
              { id: 'sidebar' as const, label: '侧边导航' }
            ].map((item) => {
              const isActive = navPlacement === item.id
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => onNavPlacementChange(item.id)}
                  className={cn(
                    TYPOGRAPHY.micro,
                    'px-3 py-2 rounded-lg border font-semibold transition-colors',
                    isActive
                      ? 'border-brand-400 bg-brand-50 text-brand-700 dark:border-brand-400/60 dark:bg-brand-500/15 dark:text-brand-200'
                      : 'border-slate-200 bg-white text-slate-500 hover:border-slate-300 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400 dark:hover:border-slate-600'
                  )}
                >
                  {item.label}
                </button>
              )
            })}
          </div>
        </div>
      </div>

      <div className="col-span-7 flex flex-col">
        <div className="mb-3 px-2">
          <h4 className={cn(TYPOGRAPHY.micro, 'font-black text-slate-400 dark:text-slate-500 uppercase tracking-widest')}>侧边栏模拟预览</h4>
        </div>

        <Card variant="default" padding="none" className={cn(cardClassName, 'overflow-hidden flex flex-col h-[440px]')}>
          <div className="h-10 border-b border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-900 flex items-center px-4 gap-1.5 shrink-0">
            <div className="w-2.5 h-2.5 rounded-full bg-rose-400" />
            <div className="w-2.5 h-2.5 rounded-full bg-amber-400" />
            <div className="w-2.5 h-2.5 rounded-full bg-emerald-400" />
            <div className="ml-4 h-5 flex-1 bg-white dark:bg-slate-800 rounded-md border border-slate-200 dark:border-slate-700 flex items-center px-3">
              <div className="w-20 h-1 bg-slate-100 dark:bg-slate-700 rounded-full" />
            </div>
          </div>

          <div className="flex-1 flex overflow-hidden">
            <div className="w-48 border-r border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900 flex flex-col p-3 space-y-4">
              {FEATURE_SCHEMA.map((mod) => {
                const isTarget = mod.id === targetModuleId
                return (
                  <div key={mod.id} className="space-y-2">
                    <div className={cn(TYPOGRAPHY.tiny, 'font-black text-slate-300 dark:text-slate-600 uppercase tracking-widest flex items-center gap-2')}>
                      {mod.name}
                      {isTarget && <div className="w-1 h-1 bg-brand-500 rounded-full animate-pulse" />}
                    </div>
                    <div className="space-y-1">
                      {mod.children.slice(0, 1).map((child) => (
                        <div
                          key={child.id}
                          className="flex items-center gap-2 px-2 py-1.5 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 opacity-40"
                        >
                          <div className="w-4 h-4 rounded bg-slate-200 dark:bg-slate-700" />
                          <div className="w-12 h-1 bg-slate-200 dark:bg-slate-700 rounded-full" />
                        </div>
                      ))}

                      {isTarget && (
                        <div className="flex items-center gap-2 px-2 py-1.5 rounded-lg bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 shadow-lg shadow-slate-900/10 ring-1 ring-slate-900 dark:ring-slate-200 animate-in zoom-in-95 duration-500 origin-left scale-105">
                          <div className="w-4 h-4 rounded-md flex items-center justify-center" style={{ backgroundColor: activeColor }}>
                            <ActiveIcon size={10} strokeWidth={3} />
                          </div>
                          <span className={cn(TYPOGRAPHY.nano, 'font-black flex-1 truncate')}>{activeNode.name}</span>
                        </div>
                      )}

                      {mod.children.slice(1, 2).map((child) => (
                        <div
                          key={child.id}
                          className="flex items-center gap-2 px-2 py-1.5 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-100 dark:border-slate-700 opacity-40"
                        >
                          <div className="w-4 h-4 rounded bg-slate-200 dark:bg-slate-700" />
                          <div className="w-16 h-1 bg-slate-200 dark:bg-slate-700 rounded-full" />
                        </div>
                      ))}
                    </div>
                  </div>
                )
              })}
            </div>

            <div className="flex-1 bg-slate-50 dark:bg-slate-900/60 p-4">
              <div className="w-full h-full border-2 border-dashed border-slate-200 dark:border-slate-700 rounded-2xl flex flex-col items-center justify-center gap-3 text-center p-6">
                <div className="w-10 h-10 bg-white dark:bg-slate-800 rounded-2xl shadow-sm dark:shadow-none flex items-center justify-center text-slate-200 dark:text-slate-600">
                  <Monitor size={20} />
                </div>
                <div className="space-y-2">
                  <div className="w-24 h-2 bg-slate-200 dark:bg-slate-700 rounded-full mx-auto" />
                  <div className="w-16 h-1 bg-slate-100 dark:bg-slate-800 rounded-full mx-auto" />
                </div>
              </div>
            </div>
          </div>
        </Card>

        <Card variant="default" padding="none" className={cn(cardClassName, 'mt-2 p-2 flex gap-2')}>
          <Info size={14} className="text-brand-600 dark:text-brand-300 shrink-0" />
          <p className={cn(TYPOGRAPHY.micro, 'text-brand-700 dark:text-brand-200 font-medium leading-relaxed')}>
            挂载配置决定了该资源在最终用户界面中的可访问入口。更改挂载点不会影响 API 的路径。
          </p>
        </Card>
      </div>
    </div>
  )
}
