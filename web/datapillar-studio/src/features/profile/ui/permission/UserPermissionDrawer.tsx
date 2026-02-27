import { useCallback, useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { toast } from 'sonner'
import { Database, Save, Shield, Sparkles, X } from 'lucide-react'
import { Button } from '@/components/ui'
import {
  deleteAdminUserModelGrant,
  listAdminModels,
  listAdminUserModels,
  upsertAdminUserModelGrant,
  type StudioAdminUserModelPermission,
  type StudioLlmModel,
} from '@/services/studioLlmService'
import { drawerWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import type { AiAccessLevel, RoleDefinition, UserItem } from '../../utils/permissionTypes'
import { AiPermission, type AiPermissionModelItem } from './AiPermission'
import { DataPermission } from './DataPermission'
import { PermissionAvatar } from './PermissionAvatar'

interface UserPermissionDrawerProps {
  isOpen: boolean
  onClose: () => void
  user: UserItem
  role: RoleDefinition
  onUpdateModelAccess: (
    userId: string,
    aiModelId: number,
    access: AiAccessLevel,
  ) => void
}

function resolveErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message
  }
  return '未知错误'
}

function normalizeAccess(permissionCode?: string | null): AiAccessLevel {
  const normalized = permissionCode?.trim().toUpperCase()
  if (normalized === 'ADMIN') {
    return 'ADMIN'
  }
  if (normalized === 'READ') {
    return 'READ'
  }
  return 'DISABLE'
}

function parsePositiveNumber(value: string): number | null {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function buildAiPermissionModels(
  models: StudioLlmModel[],
  permissions: StudioAdminUserModelPermission[],
): AiPermissionModelItem[] {
  const permissionByModelPk = new Map<number, StudioAdminUserModelPermission>()
  for (const permission of permissions) {
    if (!Number.isInteger(permission.aiModelId) || permission.aiModelId <= 0) {
      continue
    }
    permissionByModelPk.set(permission.aiModelId, permission)
  }

  const modelPkSet = new Set<number>()
  const rows: AiPermissionModelItem[] = models.map((model) => {
    modelPkSet.add(model.aiModelId)
    const permission = permissionByModelPk.get(model.aiModelId)
    return {
      aiModelId: model.aiModelId,
      providerModelId: model.providerModelId,
      name: model.name,
      providerName: model.providerName ?? model.providerCode ?? '-',
      modelType: model.modelType,
      modelStatus: model.status,
      access: normalizeAccess(permission?.permissionCode),
    }
  })

  for (const permission of permissions) {
    if (
      !Number.isInteger(permission.aiModelId) ||
      permission.aiModelId <= 0 ||
      modelPkSet.has(permission.aiModelId)
    ) {
      continue
    }
    rows.push({
      aiModelId: permission.aiModelId,
      providerModelId: permission.providerModelId,
      name: permission.modelName,
      providerName: permission.providerName ?? permission.providerCode ?? '-',
      modelType: permission.modelType,
      modelStatus: permission.modelStatus,
      access: normalizeAccess(permission.permissionCode),
    })
  }

  return rows
}

export function UserPermissionDrawer({
  isOpen,
  onClose,
  user,
  role,
  onUpdateModelAccess,
}: UserPermissionDrawerProps) {
  const [activeTab, setActiveTab] = useState<'data' | 'ai'>('data')
  const [aiModels, setAiModels] = useState<AiPermissionModelItem[]>([])
  const [aiLoading, setAiLoading] = useState(false)
  const [aiError, setAiError] = useState<string | null>(null)
  const [updatingModelId, setUpdatingModelId] = useState<number | null>(null)

  const parsedUserId = useMemo(() => parsePositiveNumber(user.id), [user.id])
  const hasEnabledAiPermission = useMemo(
    () => aiModels.some((model) => model.access !== 'DISABLE'),
    [aiModels],
  )

  const loadAiPermissionModels = useCallback(async () => {
    if (parsedUserId === null) {
      setAiModels([])
      setAiError('用户ID无效')
      return
    }

    setAiLoading(true)
    setAiError(null)
    try {
      const [models, usages] = await Promise.all([
        listAdminModels(),
        listAdminUserModels(parsedUserId),
      ])
      setAiModels(buildAiPermissionModels(models, usages))
    } catch (error) {
      setAiError(resolveErrorMessage(error))
      setAiModels([])
    } finally {
      setAiLoading(false)
    }
  }, [parsedUserId])

  const handleAiPermissionUpdate = useCallback(
    async (userId: string, aiModelId: number, access: AiAccessLevel) => {
      if (parsedUserId === null) {
        toast.error('用户ID无效，无法配置模型权限')
        return
      }

      const targetModel = aiModels.find((model) => model.aiModelId === aiModelId)
      if (!targetModel) {
        toast.error('模型不存在，无法配置权限')
        return
      }

      if (targetModel.access === access) {
        return
      }

      setUpdatingModelId(aiModelId)
      try {
        if (access === 'DISABLE') {
          await deleteAdminUserModelGrant(parsedUserId, targetModel.aiModelId)
        } else {
          await upsertAdminUserModelGrant(parsedUserId, targetModel.aiModelId, {
            permissionCode: access,
          })
        }

        setAiModels((prev) =>
          prev.map((model) =>
            model.aiModelId === aiModelId ? { ...model, access } : model,
          ),
        )
        onUpdateModelAccess(userId, aiModelId, access)
      } catch (error) {
        toast.error(`更新用户 AI 模型权限失败：${resolveErrorMessage(error)}`)
      } finally {
        setUpdatingModelId(null)
      }
    },
    [aiModels, onUpdateModelAccess, parsedUserId],
  )

  useEffect(() => {
    void loadAiPermissionModels()
  }, [loadAiPermissionModels])

  if (!isOpen) return null
  if (typeof document === 'undefined') return null

  return createPortal(
    <aside
      className={cn(
        'fixed right-0 top-14 bottom-0 z-30 bg-white dark:bg-slate-900/90 shadow-2xl border-l border-slate-200 dark:border-slate-800 flex flex-col animate-in slide-in-from-right duration-500',
        drawerWidthClassMap.wide,
      )}
    >
      <div className="px-6 py-6 bg-white dark:bg-slate-900/90 flex-shrink-0">
        <div className="flex justify-between items-start">
          <div className="flex items-center gap-4">
            <PermissionAvatar
              name={user.name}
              src={user.avatarUrl}
              size="lg"
              className="ring-4 ring-white dark:ring-slate-800 shadow-sm"
            />
            <div>
              <h2
                className={cn(
                  TYPOGRAPHY.heading,
                  'font-bold text-slate-900 dark:text-white',
                )}
              >
                {user.name} 的权限配置
              </h2>
              <div
                className={cn(
                  TYPOGRAPHY.caption,
                  'flex items-center gap-2 mt-1 text-slate-500 dark:text-slate-400',
                )}
              >
                <span>所属角色:</span>
                <span
                  className={cn(
                    TYPOGRAPHY.caption,
                    'inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full font-medium bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200',
                  )}
                >
                  <Shield size={10} /> {role.name}
                </span>
                {user.department && (
                  <>
                    <span className="w-1 h-1 bg-slate-300 dark:bg-slate-600 rounded-full mx-1" />
                    <span>部门: {user.department}</span>
                  </>
                )}
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 hover:bg-slate-200 dark:hover:bg-slate-800 rounded-full text-slate-400 dark:text-slate-500 transition-colors"
            aria-label="关闭"
          >
            <X size={20} />
          </button>
        </div>
      </div>

      <div className="px-6 pt-3 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90 flex-shrink-0">
        <div className="flex gap-8">
          <button
            type="button"
            onClick={() => setActiveTab('data')}
            className={cn(
              TYPOGRAPHY.bodySm,
              'pb-3 font-medium border-b-2 transition-all flex items-center gap-2',
              activeTab === 'data'
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
            )}
          >
            <Database size={16} />
            数据权限 (Gravitino)
            {user.dataPrivileges && user.dataPrivileges.length > 0 && (
              <span className="w-2 h-2 rounded-full bg-brand-500" />
            )}
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('ai')}
            className={cn(
              TYPOGRAPHY.bodySm,
              'pb-3 font-medium border-b-2 transition-all flex items-center gap-2',
              activeTab === 'ai'
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
            )}
          >
            <Sparkles size={16} />
            AI 模型权限
            {hasEnabledAiPermission && (
              <span className="w-2 h-2 rounded-full bg-brand-500" />
            )}
          </button>
        </div>
      </div>

      <div className="flex-1 min-h-0 bg-slate-50/30 dark:bg-slate-950/35 overflow-hidden">
        {activeTab === 'data' && (
          <div className="h-full p-6 animate-in fade-in duration-200">
            <DataPermission key={user.id} subject={user} />
          </div>
        )}

        {activeTab === 'ai' && (
          <div className="h-full overflow-y-auto p-6 custom-scrollbar animate-in fade-in duration-200">
            <AiPermission
              mode="user"
              role={role}
              user={user}
              models={aiModels}
              loading={aiLoading}
              error={aiError}
              updatingModelId={updatingModelId}
              onRetry={() => {
                void loadAiPermissionModels()
              }}
              onUpdateModelAccess={(userId, aiModelId, access) => {
                void handleAiPermissionUpdate(userId, aiModelId, access)
              }}
            />
          </div>
        )}
      </div>

      <div className="px-6 py-4 bg-white dark:bg-slate-900/90 border-t border-slate-200 dark:border-slate-800 flex justify-between items-center z-10 shrink-0">
        <div
          className={cn(
            TYPOGRAPHY.caption,
            'text-slate-400 dark:text-slate-500',
          )}
        >
          修改即时生效
        </div>
        <div className="flex gap-3">
          <Button
            size="small"
            onClick={onClose}
            className="bg-brand-600 text-white hover:bg-brand-700"
          >
            <Save size={16} />
            完成配置
          </Button>
        </div>
      </div>
    </aside>,
    document.body,
  )
}
