import { useMemo } from 'react'
import { AlertCircle, CornerDownRight } from 'lucide-react'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { RoleDefinition, UserItem } from './Permission'
import type { PermissionLevel } from './permissionConstants'
import { PERMISSION_LEVELS } from './permissionConstants'

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
          <AlertCircle size={20} className="text-indigo-600 dark:text-indigo-300 shrink-0" />
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
          <h3 className={cn(TYPOGRAPHY.micro, 'font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider mb-2 px-1')}>
            {category}
          </h3>
          <div className="bg-white dark:bg-slate-900/90 rounded-xl border border-slate-200 dark:border-slate-800 shadow-none dark:shadow-none overflow-hidden divide-y divide-slate-100 dark:divide-slate-800">
            {categoryPermissions.map((permission) => (
              <div
                key={permission.id}
                className="py-2.5 px-3 flex items-center justify-between group hover:bg-slate-50 dark:hover:bg-slate-800/70 transition-colors"
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
                        <span className={cn(TYPOGRAPHY.caption, 'text-slate-400 dark:text-slate-500 flex items-center gap-1')}>
                          <CornerDownRight size={10} /> 继承自角色 ({PERMISSION_LABELS[permission.inheritedLevel ?? permission.level]})
                        </span>
                      )
                    ) : (
                      permission.description
                    )}
                  </div>
                </div>

                <div className="flex bg-slate-100 dark:bg-slate-800/80 p-0.5 rounded-lg border border-slate-200 dark:border-slate-700">
                  {PERMISSION_LEVELS.map((level) => {
                    const isActive = permission.level === level
                    let activeClass = ''
                    if (isActive) {
                      if (level === 'WRITE') activeClass = 'bg-white dark:bg-slate-900 text-brand-600 dark:text-brand-300 shadow-sm font-bold ring-1 ring-black/5 dark:ring-white/10'
                      else if (level === 'READ') activeClass = 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-300 shadow-sm font-bold ring-1 ring-black/5 dark:ring-white/10'
                      else activeClass = 'bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 shadow-sm font-bold ring-1 ring-black/5 dark:ring-white/10'
                    } else {
                      activeClass = 'text-slate-500 hover:text-slate-700 hover:bg-white/50 dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-800/60'
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
