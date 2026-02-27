import { ArrowLeftRight, ChevronLeft, ChevronRight } from 'lucide-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/utils'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import type { TenantOption } from '@/services/types/auth'

interface WorkspaceSelectPanelProps {
  tenants: TenantOption[]
  onBack: () => void
  onSelect: (tenantId: number) => void
  loading?: boolean
}

type WorkspaceItem = {
  id: number
  name: string
  iconText: string
  metaPrimary: string
  metaSecondary: string
  metaPrimaryTone: 'accent' | 'muted'
  metaSecondaryTone: 'accent' | 'muted'
  tone: 'primary' | 'dark' | 'muted'
  highlight?: boolean
  disabled?: boolean
}

const toneClassMap: Record<WorkspaceItem['tone'], string> = {
  primary: 'bg-indigo-500 text-white',
  dark: 'bg-slate-900 text-white',
  muted: 'bg-slate-200 text-slate-600 dark:bg-slate-800 dark:text-slate-400'
}

export function WorkspaceSelectPanel({ tenants, onBack, onSelect, loading = false }: WorkspaceSelectPanelProps) {
  const { t } = useTranslation('login')
  const workspaces = useMemo<WorkspaceItem[]>(() => {
    return tenants.map((tenant) => {
      const name = tenant.tenantName?.trim() || tenant.tenantCode?.trim() || `租户-${tenant.tenantId}`
      const iconText = name.slice(0, 1)
      const isDefault = tenant.isDefault === 1
      return {
        id: tenant.tenantId,
        name,
        iconText,
        metaPrimary: tenant.tenantCode || `ID:${tenant.tenantId}`,
        metaSecondary: isDefault ? '默认' : '成员',
        metaPrimaryTone: 'accent',
        metaSecondaryTone: isDefault ? 'accent' : 'muted',
        tone: isDefault ? 'primary' : 'dark',
        highlight: isDefault,
        disabled: tenant.status !== 1
      }
    })
  }, [tenants])

  return (
    <div className="flex flex-col gap-6 h-full min-h-0">
      <button
        type="button"
        onClick={onBack}
        data-testid="workspace-select-back"
        className="inline-flex items-center gap-2 text-xs font-semibold text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300"
      >
        <ChevronLeft size={16} />
        返回登录页
      </button>

      <div>
        <h2 className="text-xl font-semibold text-slate-900 dark:text-white">选择工作空间</h2>
        <p className="mt-2 text-xs text-slate-400 dark:text-slate-500">您的账号关联了以下多个组织节点</p>
      </div>

      <div
        data-testid="workspace-select-list"
        className="h-[200px] overflow-y-auto overscroll-contain scrollbar-invisible py-1 flex flex-col"
      >
        {workspaces.length === 0 ? (
          <div className="px-4 py-6 text-xs text-slate-400 dark:text-slate-500">
            暂无可选租户
          </div>
        ) : (
          workspaces.map((workspace) => {
            const isMuted = workspace.tone === 'muted'
            return (
              <button
                key={workspace.id}
                type="button"
                disabled={loading || workspace.disabled}
                onClick={() => onSelect(workspace.id)}
                data-testid={`workspace-select-item-${workspace.id}`}
                className={cn(
                  'group relative flex w-full items-center gap-3 px-4 py-3 text-left transition-colors cursor-pointer rounded-2xl',
                  'bg-transparent text-slate-600 hover:bg-white dark:text-slate-300 dark:hover:bg-slate-900/80',
                  'hover:shadow-[inset_0_0_0_1px_rgba(226,232,240,0.9)] dark:hover:shadow-[inset_0_0_0_1px_rgba(51,65,85,0.8)] hover:z-10',
                  (loading || workspace.disabled) && 'opacity-60 cursor-not-allowed'
                )}
              >
                <div className={cn('flex h-10 w-10 items-center justify-center rounded-2xl text-sm font-semibold', toneClassMap[workspace.tone])}>
                  {workspace.iconText}
                </div>
                <div className="flex-1 min-w-0">
                  <div className={cn(TYPOGRAPHY.bodySm, 'font-semibold', isMuted ? 'text-slate-500' : 'text-slate-900 dark:text-slate-100')}>
                    {workspace.name}
                  </div>
                  <div className={`mt-1 ${TYPOGRAPHY.legal}`}>
                    <span className={cn(isMuted || workspace.metaPrimaryTone === 'muted' ? 'text-slate-400' : 'text-indigo-600 dark:text-indigo-300')}>
                      {workspace.metaPrimary}
                    </span>
                    <span className="mx-1 text-slate-300">·</span>
                    <span className={cn(isMuted || workspace.metaSecondaryTone === 'muted' ? 'text-slate-400' : 'text-indigo-600 dark:text-indigo-300')}>
                      {workspace.metaSecondary}
                    </span>
                  </div>
                </div>
                <div className="ml-auto flex h-8 w-8 items-center justify-center rounded-full bg-indigo-50 text-indigo-600 border border-indigo-100 opacity-0 transition-opacity duration-150 group-hover:opacity-100 dark:bg-indigo-500/10 dark:text-indigo-300 dark:border-indigo-500/30">
                  <ChevronRight size={16} />
                </div>
              </button>
            )
          })
        )}
      </div>

      <div className="mt-2 flex items-start gap-3 rounded-2xl border border-indigo-200 bg-indigo-50/60 px-4 py-3 text-xs text-indigo-600 dark:border-indigo-500/30 dark:bg-indigo-500/10 dark:text-indigo-300">
        <div className="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-100 text-indigo-600 dark:bg-indigo-500/20 dark:text-indigo-300">
          <ArrowLeftRight size={16} />
        </div>
        <p className="text-xs leading-relaxed">
          跨租户登录已启用。进入后您可以通过头像下拉快速切换。
        </p>
      </div>

      <div className="text-legal text-slate-400 dark:text-slate-500 text-center mt-2 select-none min-h-[48px]">
        <p className="font-mono tracking-wide">© {new Date().getFullYear()} {t('brand.name')} {t('brand.tagline')}</p>
        <p className="mt-1 text-slate-500 dark:text-slate-400">{t('brand.slogan')}</p>
      </div>
    </div>
  )
}
