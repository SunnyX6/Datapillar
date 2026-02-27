import { Activity, AlertCircle, ShieldCheck, Zap } from 'lucide-react'
import { Button, Card } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import type { AiAccessLevel, RoleDefinition, UserItem } from '../../utils/permissionTypes'

export interface AiPermissionModelItem {
  aiModelId: number
  providerModelId: string
  name: string
  providerName: string
  modelType?: string | null
  modelStatus?: string | null
  access: AiAccessLevel
  callCount?: string | null
  totalTokens?: string | null
  totalCostUsd?: string | null
}

type RoleModeProps = {
  mode: 'role'
  role: RoleDefinition
  models: AiPermissionModelItem[]
  loading?: boolean
  error?: string | null
  updatingModelId?: number | null
  onRetry?: () => void
  onUpdateModelAccess: (aiModelId: number, access: AiAccessLevel) => void
  className?: string
}

type UserModeProps = {
  mode: 'user'
  role: RoleDefinition
  user: UserItem
  models: AiPermissionModelItem[]
  loading?: boolean
  error?: string | null
  updatingModelId?: number | null
  onRetry?: () => void
  onUpdateModelAccess: (
    userId: string,
    aiModelId: number,
    access: AiAccessLevel,
  ) => void
  className?: string
}

type AiPermissionProps = RoleModeProps | UserModeProps

const ACCESS_LEVELS: AiAccessLevel[] = ['DISABLE', 'READ', 'ADMIN']

const ACCESS_LABELS: Record<AiAccessLevel, string> = {
  DISABLE: '禁止',
  READ: '查看',
  ADMIN: '管理',
}

const MODEL_STATUS_LABELS: Record<string, string> = {
  ACTIVE: '已连接',
  INACTIVE: '未连接',
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

function normalizeModelStatus(status?: string | null): string {
  const normalized = status?.trim().toUpperCase()
  if (!normalized) {
    return '未知状态'
  }
  return MODEL_STATUS_LABELS[normalized] ?? normalized
}

function isModelActive(status?: string | null): boolean {
  return status?.trim().toUpperCase() === 'ACTIVE'
}

function resolveModelInitial(name: string): string {
  const normalized = name.trim()
  if (!normalized) {
    return '?'
  }
  return normalized.charAt(0).toUpperCase()
}

function parseMetricValue(value?: string | null): number | null {
  if (!value) {
    return null
  }
  const normalized = value.replace(/,/g, '').trim()
  if (!normalized) {
    return null
  }
  const parsed = Number(normalized)
  if (!Number.isFinite(parsed) || parsed < 0) {
    return null
  }
  return parsed
}

function resolveUsagePercent(model: AiPermissionModelItem): number {
  const totalTokenValue = parseMetricValue(model.totalTokens)
  if (totalTokenValue === null || totalTokenValue <= 0) {
    return 0
  }
  return Math.min((Math.log10(totalTokenValue + 1) / 6) * 100, 100)
}

function getUsageWidthClass(usagePercent: number): string {
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
  const { className } = props
  const mode = props.mode
  const subjectLabel = mode === 'user' ? '该用户' : '此角色'
  const models = props.models
  const isLoading = Boolean(props.loading)
  const errorMessage = props.error

  const handleModelAccessUpdate = (aiModelId: number, access: AiAccessLevel) => {
    if (props.mode === 'user') {
      props.onUpdateModelAccess(props.user.id, aiModelId, access)
      return
    }
    props.onUpdateModelAccess(aiModelId, access)
  }

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
              您正在为该用户单独配置 AI 模型权限
            </p>
          </div>
        </div>
      )}

      {isLoading && (
        <Card
          variant="default"
          className="border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900"
        >
          <div className="flex items-center gap-2 text-slate-500 dark:text-slate-400">
            <Zap size={14} className="animate-pulse" />
            <span className={cn(TYPOGRAPHY.caption)}>正在加载模型权限…</span>
          </div>
        </Card>
      )}

      {!isLoading && errorMessage && (
        <Card
          variant="default"
          className="border border-rose-200 dark:border-rose-800/40 bg-rose-50/70 dark:bg-rose-950/20"
        >
          <div className="flex items-center justify-between gap-4">
            <div className={cn(TYPOGRAPHY.caption, 'text-rose-700 dark:text-rose-300')}>
              加载模型权限失败：{errorMessage}
            </div>
            {props.onRetry && (
              <Button size="small" variant="outline" onClick={props.onRetry}>
                重试
              </Button>
            )}
          </div>
        </Card>
      )}

      {!isLoading && !errorMessage && (
        <div className="grid grid-cols-1 gap-4">
          {models.length === 0 && (
            <Card
              variant="default"
              className="border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900"
            >
              <p className={cn(TYPOGRAPHY.caption, 'text-slate-500 dark:text-slate-400')}>
                暂无可配置的 AI 模型
              </p>
            </Card>
          )}

          {models.map((model) => {
            const access = model.access
            const disabled = access === 'DISABLE'
            const isUpdating = props.updatingModelId === model.aiModelId
            const active = isModelActive(model.modelStatus)
            const usagePercent = resolveUsagePercent(model)
            const hasUsageData =
              usagePercent > 0 ||
              Boolean(model.callCount && model.callCount !== '0') ||
              Boolean(model.totalCostUsd && model.totalCostUsd !== '0')

            return (
              <Card
                key={`${model.aiModelId}-${model.providerModelId}`}
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
                        'size-10 rounded-xl border border-slate-200 dark:border-slate-700 flex items-center justify-center transition-colors font-black text-body-sm',
                        disabled
                          ? 'bg-slate-100 dark:bg-slate-800 text-slate-400 dark:text-slate-500'
                          : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-200',
                      )}
                    >
                      {resolveModelInitial(model.name)}
                    </div>
                    <div>
                      <div className="flex items-center gap-2 flex-wrap">
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
                          {model.providerName || '-'}
                        </span>
                        <span
                          className={cn(
                            TYPOGRAPHY.nano,
                            'px-2 py-0.5 rounded-full border uppercase tracking-wider font-semibold',
                            active
                              ? 'border-emerald-200 text-emerald-700 bg-emerald-50 dark:border-emerald-500/40 dark:text-emerald-300 dark:bg-emerald-500/10'
                              : 'border-amber-200 text-amber-700 bg-amber-50 dark:border-amber-500/40 dark:text-amber-300 dark:bg-amber-500/10',
                          )}
                        >
                          {normalizeModelStatus(model.modelStatus)}
                        </span>
                      </div>
                      <div
                        className={cn(
                          TYPOGRAPHY.nano,
                          'mt-1 flex items-center gap-4 text-slate-400 dark:text-slate-500 uppercase tracking-wider',
                        )}
                      >
                        <span>{model.modelType?.toUpperCase() ?? 'UNKNOWN'}</span>
                        <span>调用 {model.callCount ?? '0'} 次</span>
                        <span>Token {model.totalTokens ?? '0'}</span>
                        <span>费用 ${model.totalCostUsd ?? '0'}</span>
                      </div>
                    </div>
                  </div>

                  <div className="inline-flex items-center gap-0.5 rounded-lg bg-slate-100 dark:bg-slate-800/80 p-0.5 border border-slate-200 dark:border-slate-700">
                    {ACCESS_LEVELS.map((level) => {
                      const selected = access === level
                      const optionDisabled =
                        isUpdating ||
                        isLoading ||
                        (!active && level !== 'DISABLE')
                      return (
                        <button
                          key={level}
                          type="button"
                          disabled={optionDisabled}
                          onClick={() => handleModelAccessUpdate(model.aiModelId, level)}
                          data-testid={`ai-access-${model.aiModelId}-${level}`}
                          className={cn(
                            TYPOGRAPHY.micro,
                            'px-2.5 py-1 rounded-md font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60',
                            selected
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
                        {model.totalTokens ?? '-'}
                      </span>
                      <span
                        className={cn(
                          TYPOGRAPHY.nano,
                          'font-semibold text-slate-400 dark:text-slate-500',
                        )}
                      >
                        Token 总量
                      </span>
                    </div>
                    <span
                      className={cn(
                        TYPOGRAPHY.nano,
                        'px-2 py-1 rounded-md font-semibold uppercase tracking-wider',
                        hasUsageData
                          ? 'text-slate-600 bg-slate-100 dark:text-slate-300 dark:bg-slate-800'
                          : 'text-amber-700 bg-amber-50 dark:text-amber-300 dark:bg-amber-500/10',
                      )}
                    >
                      调用 {model.callCount ?? '-'} 次
                    </span>
                  </div>
                  <div className="h-1.5 rounded-full bg-slate-100 dark:bg-slate-800 overflow-hidden">
                    <div
                      className={cn(
                        'h-full transition-all duration-500',
                        getUsageWidthClass(usagePercent),
                        hasUsageData
                          ? 'bg-slate-900 dark:bg-slate-200'
                          : 'bg-slate-300 dark:bg-slate-600',
                      )}
                    />
                  </div>
                  <div
                    className={cn(
                      TYPOGRAPHY.nano,
                      'flex items-center justify-between text-slate-500 dark:text-slate-400',
                    )}
                  >
                    <span>费用 ${model.totalCostUsd ?? '-'}</span>
                    <span>{hasUsageData ? '统计已接入（基础版）' : '统计接口待接入'}</span>
                  </div>
                </div>
              </Card>
            )
          })}
        </div>
      )}

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
