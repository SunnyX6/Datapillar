import { useMemo } from 'react'
import {
  Activity,
  AlertCircle,
  BarChart3,
  Cpu,
  ShieldCheck,
  Zap,
} from 'lucide-react'
import { Card } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { AiAccessLevel, RoleDefinition, UserItem } from './Permission'

type RoleModeProps = {
  mode: 'role'
  role: RoleDefinition
  onUpdateModelAccess: (modelId: string, access: AiAccessLevel) => void
  className?: string
}

type UserModeProps = {
  mode: 'user'
  role: RoleDefinition
  user: UserItem
  onUpdateModelAccess: (
    userId: string,
    modelId: string,
    access: AiAccessLevel,
  ) => void
  className?: string
}

type AiPermissionProps = RoleModeProps | UserModeProps

interface AiModelDefinition {
  id: string
  name: string
  vendor: string
  quota: number
  used: number
  tps: number
}

const AI_MODELS: AiModelDefinition[] = [
  {
    id: 'model_gemini_3_pro',
    name: 'Gemini 3 Pro',
    vendor: 'Google',
    quota: 100,
    used: 22,
    tps: 120,
  },
  {
    id: 'model_claude_3_5_sonnet',
    name: 'Claude 3.5 Sonnet',
    vendor: 'Anthropic',
    quota: 50,
    used: 48,
    tps: 85,
  },
  {
    id: 'model_llama_3_1_405b',
    name: 'Llama 3.1 405B',
    vendor: 'Meta',
    quota: 10,
    used: 0,
    tps: 0,
  },
]

const ACCESS_LEVELS: AiAccessLevel[] = ['DISABLE', 'READ', 'ADMIN']

const ACCESS_LABELS: Record<AiAccessLevel, string> = {
  DISABLE: '禁止',
  READ: '查看',
  ADMIN: '管理',
}

const getAccessActiveClass = (level: AiAccessLevel) => {
  if (level === 'ADMIN') {
    return 'bg-white dark:bg-slate-900 text-brand-600 dark:text-brand-300 shadow-sm ring-1 ring-black/5 dark:ring-white/10'
  }
  if (level === 'READ') {
    return 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-300 shadow-sm ring-1 ring-black/5 dark:ring-white/10'
  }
  return 'bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 shadow-sm ring-1 ring-black/5 dark:ring-white/10'
}

const getUsageWidthClass = (usagePercent: number) => {
  if (usagePercent >= 99.5) return 'w-full'
  if (usagePercent >= 91.5) return 'w-11/12'
  if (usagePercent >= 83.5) return 'w-10/12'
  if (usagePercent >= 75.5) return 'w-9/12'
  if (usagePercent >= 66.5) return 'w-8/12'
  if (usagePercent >= 58.5) return 'w-7/12'
  if (usagePercent >= 50.5) return 'w-6/12'
  if (usagePercent >= 41.5) return 'w-5/12'
  if (usagePercent >= 33.5) return 'w-4/12'
  if (usagePercent >= 25.5) return 'w-3/12'
  if (usagePercent >= 16.5) return 'w-2/12'
  if (usagePercent >= 8.5) return 'w-1/12'
  return usagePercent > 0 ? 'w-px' : 'w-0'
}

export function AiPermission(props: AiPermissionProps) {
  const { role, className } = props
  const mode = props.mode
  const user = props.mode === 'user' ? props.user : null

  const modelAccessMap = useMemo(() => {
    if (mode === 'user') {
      return new Map(
        (user?.aiModelPermissions ?? []).map((item) => [
          item.modelId,
          item.access,
        ]),
      )
    }

    return new Map(
      (role.aiModelPermissions ?? []).map((item) => [
        item.modelId,
        item.access,
      ]),
    )
  }, [mode, role.aiModelPermissions, user?.aiModelPermissions])

  const handleModelAccessUpdate = (modelId: string, access: AiAccessLevel) => {
    if (props.mode === 'user') {
      props.onUpdateModelAccess(props.user.id, modelId, access)
      return
    }
    props.onUpdateModelAccess(modelId, access)
  }

  const subjectLabel = mode === 'user' ? '该用户' : '此角色'

  return (
    <div className={cn('space-y-6', className)}>
      {mode === 'user' && (
        <div className="mb-6 bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-100 dark:border-indigo-500/30 rounded-lg p-4 flex gap-3">
          <AlertCircle
            size={20}
            className="text-indigo-600 dark:text-indigo-300 shrink-0"
          />
          <div
            className={cn(TYPOGRAPHY.bodySm, 'text-indigo-900 dark:text-indigo-200')}
          >
            <p className="font-medium">独立权限配置模式</p>
            <p className={cn(TYPOGRAPHY.caption, 'opacity-80 mt-0.5')}>
              您正在为该用户单独配置 AI 模型权限。此处的修改将覆盖{' '}
              <span className="font-semibold">{role.name}</span>{' '}
              角色的默认设置。
            </p>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4">
        {AI_MODELS.map((model) => {
          const access = modelAccessMap.get(model.id) ?? 'DISABLE'
          const usagePercent =
            model.quota > 0
              ? Math.min((model.used / model.quota) * 100, 100)
              : 0
          const disabled = access === 'DISABLE'

          return (
            <Card
              key={model.id}
              variant="default"
              padding="none"
              className={cn(
                'px-5 py-4 transition-colors shadow-none dark:shadow-none',
                disabled
                  ? 'bg-slate-50/80 dark:bg-slate-900/60 opacity-70'
                  : 'bg-white dark:bg-slate-900/90 hover:border-slate-300 dark:hover:border-slate-700',
              )}
            >
              <div className="flex items-start justify-between gap-4 mb-4">
                <div className="flex items-start gap-4">
                  <div
                    className={cn(
                      'size-10 rounded-xl border border-slate-200 dark:border-slate-700 flex items-center justify-center transition-colors',
                      disabled
                        ? 'bg-slate-100 dark:bg-slate-800 text-slate-400 dark:text-slate-500'
                        : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-200',
                    )}
                  >
                    <Cpu size={18} />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h4
                        className={cn(
                          TYPOGRAPHY.bodySm,
                          'font-bold text-slate-900 dark:text-slate-100 tracking-tight',
                        )}
                      >
                        {model.name}
                      </h4>
                      <span
                        className={cn(
                          TYPOGRAPHY.nano,
                          'px-2 py-0.5 rounded-full border border-slate-200 dark:border-slate-700 text-slate-500 dark:text-slate-400 uppercase tracking-wider font-semibold',
                        )}
                      >
                        {model.vendor}
                      </span>
                    </div>
                    <div
                      className={cn(
                        TYPOGRAPHY.nano,
                        'mt-1 flex items-center gap-4 text-slate-400 dark:text-slate-500 uppercase tracking-wider',
                      )}
                    >
                      <span className="inline-flex items-center gap-1">
                        <BarChart3 size={10} />
                        {model.tps} Tokens/秒
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <Zap size={10} />
                        托管入口点
                      </span>
                    </div>
                  </div>
                </div>

                <div className="inline-flex items-center gap-0.5 rounded-lg bg-slate-100 dark:bg-slate-800/80 p-0.5 border border-slate-200 dark:border-slate-700">
                  {ACCESS_LEVELS.map((level) => {
                    const active = access === level
                    return (
                      <button
                        key={level}
                        type="button"
                        onClick={() => handleModelAccessUpdate(model.id, level)}
                        data-testid={`ai-access-${model.id}-${level}`}
                        className={cn(
                          TYPOGRAPHY.micro,
                          'px-2.5 py-1 rounded-md font-semibold transition-colors',
                          active
                            ? getAccessActiveClass(level)
                            : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200',
                        )}
                      >
                        {ACCESS_LABELS[level]}
                      </button>
                    )
                  })}
                </div>
              </div>

              <div className="space-y-2.5">
                <div className="flex items-end justify-between gap-2">
                  <div className="flex items-baseline gap-1.5">
                    <span
                      className={cn(
                        TYPOGRAPHY.subtitle,
                        'font-black text-slate-900 dark:text-slate-100 tracking-tight',
                      )}
                    >
                      {model.used}M
                    </span>
                    <span
                      className={cn(
                        TYPOGRAPHY.nano,
                        'font-semibold text-slate-400 dark:text-slate-500',
                      )}
                    >
                      / {model.quota}M Token 额度
                    </span>
                  </div>
                  <span
                    className={cn(
                      TYPOGRAPHY.nano,
                      'px-2 py-1 rounded-md font-semibold uppercase tracking-wider',
                      usagePercent > 80
                        ? 'text-rose-600 bg-rose-50 dark:text-rose-300 dark:bg-rose-500/10'
                        : 'text-slate-600 bg-slate-100 dark:text-slate-300 dark:bg-slate-800',
                    )}
                  >
                    利用率 {usagePercent.toFixed(1)}%
                  </span>
                </div>
                <div className="h-1.5 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
                  <div
                    className={cn(
                      'h-full transition-all duration-500',
                      getUsageWidthClass(usagePercent),
                      usagePercent > 80
                        ? 'bg-rose-500'
                        : 'bg-slate-900 dark:bg-slate-200',
                    )}
                  />
                </div>
              </div>
            </Card>
          )
        })}
      </div>

      <Card
        variant="default"
        padding="none"
        className="bg-slate-950 text-white px-6 py-6 relative overflow-hidden border-slate-900 dark:border-slate-900 shadow-none"
      >
        <div className="relative z-10">
          <div className="flex items-center gap-2 mb-3">
            <Activity size={16} className="text-emerald-400" />
            <h4
              className={cn(
                TYPOGRAPHY.micro,
                'font-black uppercase tracking-widest text-slate-300',
              )}
            >
              治理网关 (Governance Gateway)
            </h4>
          </div>
          <p
            className={cn(
              TYPOGRAPHY.caption,
              'text-slate-300 leading-relaxed max-w-2xl',
            )}
          >
            {subjectLabel}的 AI
            流量将由统一策略网关实时拦截，自动执行敏感信息脱敏、Prompt
            注入拦截与策略审计留痕。
          </p>
        </div>
        <ShieldCheck
          size={110}
          className="absolute -right-6 -top-5 text-white/10 pointer-events-none"
        />
      </Card>
    </div>
  )
}
