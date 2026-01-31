import { useMemo } from 'react'
import { AlertCircle, CornerDownRight } from 'lucide-react'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { PermissionLevel, RoleDefinition, UserItem } from './Permission'

export const PERMISSION_RESOURCES: Array<{
  id: string
  name: string
  category: string
  description: string
}> = [
  { id: 'asset.catalog', name: '元数据目录', category: '数据资产', description: '浏览数据目录与资产信息' },
  { id: 'asset.lineage', name: '数据血缘', category: '数据资产', description: '查看上下游血缘关系' },
  { id: 'asset.quality', name: '质量规则', category: '数据资产', description: '管理质量规则与校验' },
  { id: 'asset.security', name: '敏感字段策略', category: '数据资产', description: '配置敏感字段与访问策略' },
  { id: 'build.workflow', name: '工作流编排', category: '开发与发布', description: '编排与调度数据工作流' },
  { id: 'build.ide', name: 'SQL IDE', category: '开发与发布', description: '在线 SQL 开发与调试' },
  { id: 'build.release', name: '发布审批', category: '开发与发布', description: '提交与审批发布流程' },
  { id: 'ai.assistant', name: 'AI 辅助修复', category: 'AI 能力', description: '使用 AI 辅助修复与生成' },
  { id: 'ai.models', name: '模型管理', category: 'AI 能力', description: '查看与管理模型资源' },
  { id: 'ai.cost', name: '成本卫士', category: 'AI 能力', description: '成本分析与预算控制' }
]

export const buildPermissions = (defaultLevel: PermissionLevel, overrides: Record<string, PermissionLevel> = {}) =>
  PERMISSION_RESOURCES.map((resource) => ({
    ...resource,
    level: overrides[resource.id] ?? defaultLevel
  }))

const PERMISSION_LEVELS: PermissionLevel[] = ['NONE', 'READ', 'WRITE']

const PERMISSION_LABELS: Record<PermissionLevel, string> = {
  NONE: '禁止',
  READ: '查看',
  WRITE: '管理'
}

type RoleModeProps = {
  mode: 'role'
  role: RoleDefinition
  onUpdatePermission: (resourceId: string, level: PermissionLevel) => void
  className?: string
}

type UserModeProps = {
  mode: 'user'
  role: RoleDefinition
  user: UserItem
  onUpdatePermission: (userId: string, resourceId: string, level: PermissionLevel) => void
  className?: string
}

type PermissionItem = RoleDefinition['permissions'][number] & {
  isCustom?: boolean
  inheritedLevel?: PermissionLevel
}

export function FunctionalPermission(props: RoleModeProps | UserModeProps) {
  const isUserMode = props.mode === 'user'
  const rolePermissions = props.role.permissions
  const customPermissions = props.mode === 'user' ? props.user.customPermissions : undefined

  const permissions = useMemo<PermissionItem[]>(() => {
    if (!isUserMode) return rolePermissions
    const overrides = customPermissions ?? []
    return rolePermissions.map((rolePermission) => {
      const custom = overrides.find((permission) => permission.id === rolePermission.id)
      return {
        ...rolePermission,
        level: custom ? custom.level : rolePermission.level,
        isCustom: !!custom,
        inheritedLevel: rolePermission.level
      }
    })
  }, [customPermissions, isUserMode, rolePermissions])

  const groupedPermissions = useMemo(() => {
    return permissions.reduce<Record<string, PermissionItem[]>>((acc, permission) => {
      if (!acc[permission.category]) acc[permission.category] = []
      acc[permission.category].push(permission)
      return acc
    }, {})
  }, [permissions])

  const handlePermissionChange = (resourceId: string, level: PermissionLevel) => {
    if (props.mode === 'user') {
      props.onUpdatePermission(props.user.id, resourceId, level)
      return
    }
    props.onUpdatePermission(resourceId, level)
  }

  return (
    <div className={cn('space-y-4', props.className)}>
      {props.mode === 'user' && (
        <div className="mb-6 bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-100 dark:border-indigo-500/30 rounded-lg p-4 flex gap-3">
          <AlertCircle size={20} className="text-indigo-600 shrink-0" />
          <div className={cn(TYPOGRAPHY.bodySm, 'text-indigo-900 dark:text-indigo-200')}>
            <p className="font-medium">独立权限配置模式</p>
            <p className={cn(TYPOGRAPHY.caption, 'opacity-80 mt-0.5')}>
              您正在为该用户单独配置功能权限。此处的修改将覆盖 <span className="font-semibold">{props.role.name}</span>{' '}
              角色的默认设置。
            </p>
          </div>
        </div>
      )}

      {Object.entries(groupedPermissions).map(([category, categoryPermissions]) => (
        <div key={category}>
          <h3 className={cn(TYPOGRAPHY.micro, 'font-bold text-slate-400 uppercase tracking-wider mb-2 px-1')}>
            {category}
          </h3>
          <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm overflow-hidden divide-y divide-slate-100 dark:divide-slate-800">
            {categoryPermissions.map((permission) => (
              <div
                key={permission.id}
                className="py-2.5 px-3 flex items-center justify-between group hover:bg-slate-50 transition-colors"
              >
                <div className="flex-1 pr-4">
                  <div className={cn(TYPOGRAPHY.bodySm, 'font-medium text-slate-900 dark:text-white mb-0.5')}>
                    {permission.name}
                  </div>
                  <div className={cn(TYPOGRAPHY.caption, 'text-slate-500 dark:text-slate-400 flex items-center gap-2')}>
                    {isUserMode ? (
                      permission.isCustom ? (
                        <span
                          className={cn(
                            TYPOGRAPHY.micro,
                            'text-amber-600 font-medium bg-amber-50 px-1.5 rounded flex items-center gap-1 dark:bg-amber-500/10 dark:text-amber-300'
                          )}
                        >
                          覆盖继承
                        </span>
                      ) : (
                        <span className={cn(TYPOGRAPHY.caption, 'text-slate-400 flex items-center gap-1')}>
                          <CornerDownRight size={10} /> 继承自角色 ({PERMISSION_LABELS[permission.inheritedLevel ?? permission.level]})
                        </span>
                      )
                    ) : (
                      permission.description
                    )}
                  </div>
                </div>

                <div className="flex bg-slate-100 p-0.5 rounded-lg border border-slate-200">
                  {PERMISSION_LEVELS.map((level) => {
                    const isActive = permission.level === level
                    let activeClass = ''
                    if (isActive) {
                      if (level === 'WRITE') activeClass = 'bg-white text-brand-600 shadow-sm font-bold ring-1 ring-black/5'
                      else if (level === 'READ') activeClass = 'bg-white text-indigo-600 shadow-sm font-bold ring-1 ring-black/5'
                      else activeClass = 'bg-white text-slate-600 shadow-sm font-bold ring-1 ring-black/5'
                    } else {
                      activeClass = 'text-slate-500 hover:text-slate-700 hover:bg-white/50'
                    }

                    return (
                      <button
                        key={level}
                        type="button"
                        onClick={() => handlePermissionChange(permission.id, level)}
                        className={`px-2 py-0.5 rounded text-xs transition-all ${activeClass}`}
                      >
                        {PERMISSION_LABELS[level]}
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
