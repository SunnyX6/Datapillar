import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link2, Shield, Users } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/stores/authStore'
import {
  createTenantRole,
  deleteTenantRole,
  getTenantRolePermissions,
  getTenantRoleMembers,
  listTenantRoles,
  updateTenantRole,
  updateTenantRolePermissions,
  type StudioRole,
  type StudioRolePermission,
} from '@/services/studioTenantRoleService'
import { RoleList } from './RoleList'
import { MembersList } from './MembersList'
import { FunctionalPermission } from './FunctionalPermission'
import { mapRoleMembersToUserItems } from './memberAdapter'
import type { PermissionLevel } from './permissionConstants'

export type UserStatus = '已激活' | '已邀请' | '已禁用'

interface PermissionResourceBase {
  objectId: number
  objectName: string
  objectPath?: string
  objectType?: string
  location?: string
  categoryName: string
}

export interface PermissionResource extends PermissionResourceBase {
  level: PermissionLevel
  tenantLevel: PermissionLevel
}

export type RoleType = 'ADMIN' | 'USER'

export interface RoleDefinition {
  id: string
  type: RoleType
  name: string
  description: string
  isSystem?: boolean
  memberCount?: number
  permissions: PermissionResource[]
  dataPrivileges?: UserDataPrivilege[]
  aiModelPermissions?: AiModelPermission[]
}

export interface RoleItem extends RoleDefinition {
  userCount: number
}

export interface UserDataPrivilege {
  assetId: string
  privileges: string[]
}

export type AiAccessLevel = 'DISABLE' | 'READ' | 'ADMIN'

export interface AiModelPermission {
  modelId: string
  access: AiAccessLevel
}

export interface UserItem {
  id: string
  name: string
  email: string
  avatarUrl?: string
  roleId: string
  status: UserStatus
  lastActive: string
  department?: string
  dataPrivileges?: UserDataPrivilege[]
  aiModelPermissions?: AiModelPermission[]
}

const ROLE_DEFINITIONS: RoleDefinition[] = []
const INITIAL_USERS: UserItem[] = []

function resolveRoleType(type?: string | null): RoleType {
  return type?.toUpperCase() === 'ADMIN' ? 'ADMIN' : 'USER'
}

function normalizePermissionLevel(
  permissionCode?: string | null,
): PermissionLevel {
  if (permissionCode?.toUpperCase() === 'ADMIN') {
    return 'ADMIN'
  }
  if (permissionCode?.toUpperCase() === 'READ') {
    return 'READ'
  }
  return 'DISABLE'
}

function mapRolePermissionToResource(
  permission: StudioRolePermission,
): PermissionResource {
  return {
    objectId: permission.objectId,
    objectName: permission.objectName,
    objectPath: permission.objectPath?.trim() || undefined,
    objectType: permission.objectType?.trim() || undefined,
    location: permission.location?.trim() || undefined,
    categoryName: permission.categoryName?.trim() || '未分类',
    level: normalizePermissionLevel(permission.permissionCode),
    tenantLevel: normalizePermissionLevel(permission.tenantPermissionCode),
  }
}

function mapStudioRoleToDefinition(role: StudioRole): RoleDefinition {
  return {
    id: String(role.id),
    type: resolveRoleType(role.type),
    name: role.name,
    description: role.description?.trim() ?? '',
    isSystem: role.isBuiltin === 1,
    memberCount: role.memberCount ?? 0,
    permissions: [],
    dataPrivileges: [],
    aiModelPermissions: [],
  }
}

function toRolePermissionAssignments(resources: PermissionResource[]) {
  return resources.map((resource) => ({
    objectId: resource.objectId,
    permissionCode: resource.level,
  }))
}

export function PermissionLayout() {
  const tenantId = useAuthStore((state) => state.user?.tenantId)
  const [users, setUsers] = useState<UserItem[]>(INITIAL_USERS)
  const [roleDefinitions, setRoleDefinitions] =
    useState<RoleDefinition[]>(ROLE_DEFINITIONS)
  const [selectedRoleId, setSelectedRoleId] = useState<string>('')
  const [activeTab, setActiveTab] = useState<'members' | 'functional'>(
    'members',
  )
  const [isAddModalOpen, setIsAddModalOpen] = useState(false)

  const roleUserCounts = useMemo(() => {
    return users.reduce<Record<string, number>>((acc, user) => {
      acc[user.roleId] = (acc[user.roleId] ?? 0) + 1
      return acc
    }, {})
  }, [users])

  const roles = useMemo<RoleItem[]>(
    () =>
      roleDefinitions.map((role) => ({
        ...role,
        userCount: role.memberCount ?? roleUserCounts[role.id] ?? 0,
      })),
    [roleDefinitions, roleUserCounts],
  )

  const selectedRole = useMemo(
    () => roles.find((role) => role.id === selectedRoleId) ?? roles[0],
    [roles, selectedRoleId],
  )
  const selectedRoleUsers = useMemo(
    () => users.filter((user) => user.roleId === selectedRole?.id),
    [selectedRole?.id, users],
  )
  const selectedRolePreviewUsers = useMemo(
    () => selectedRoleUsers.slice(0, 5),
    [selectedRoleUsers],
  )
  const hasSelectedRoleUsers = selectedRoleUsers.length > 0

  const syncRoleDefinitions = useCallback((rolesFromBackend: StudioRole[]) => {
    const mappedRoles = rolesFromBackend.map(mapStudioRoleToDefinition)
    setRoleDefinitions(mappedRoles)
    setSelectedRoleId((currentSelectedId) => {
      if (mappedRoles.length === 0) {
        return ''
      }
      return mappedRoles.some((role) => role.id === currentSelectedId)
        ? currentSelectedId
        : mappedRoles[0].id
    })
  }, [])

  const syncRolePermissions = useCallback(
    (roleId: string, permissionsFromBackend: StudioRolePermission[]) => {
      setRoleDefinitions((prev) =>
        prev.map((role) =>
          role.id === roleId
            ? {
                ...role,
                permissions: permissionsFromBackend.map(
                  mapRolePermissionToResource,
                ),
              }
            : role,
        ),
      )
    },
    [],
  )

  useEffect(() => {
    if (!tenantId) {
      return
    }
    let isCancelled = false

    const loadTenantRoles = async () => {
      try {
        const rolesFromBackend = await listTenantRoles(tenantId)
        if (isCancelled) {
          return
        }
        syncRoleDefinitions(rolesFromBackend)
      } catch (error) {
        if (isCancelled) {
          return
        }
        syncRoleDefinitions([])
        const message = error instanceof Error ? error.message : String(error)
        toast.error(`加载角色列表失败：${message}`)
      }
    }

    void loadTenantRoles()
    return () => {
      isCancelled = true
    }
  }, [syncRoleDefinitions, tenantId])

  useEffect(() => {
    if (!tenantId || !selectedRoleId) {
      return
    }
    const roleId = Number(selectedRoleId)
    if (!Number.isFinite(roleId)) {
      return
    }

    let isCancelled = false

    const loadRoleMembers = async () => {
      try {
        const response = await getTenantRoleMembers(tenantId, roleId)
        if (isCancelled) {
          return
        }
        setUsers(mapRoleMembersToUserItems(response.members, selectedRoleId))
        setRoleDefinitions((prev) =>
          prev.map((role) =>
            role.id === selectedRoleId
              ? { ...role, memberCount: response.memberCount }
              : role,
          ),
        )
      } catch (error) {
        if (isCancelled) {
          return
        }
        setUsers([])
        const message = error instanceof Error ? error.message : String(error)
        toast.error(`加载角色成员失败：${message}`)
      }
    }

    void loadRoleMembers()

    return () => {
      isCancelled = true
    }
  }, [selectedRoleId, tenantId])

  useEffect(() => {
    if (!tenantId || !selectedRoleId) {
      return
    }

    const roleId = Number(selectedRoleId)
    if (!Number.isFinite(roleId)) {
      return
    }

    let isCancelled = false

    const loadRolePermissions = async () => {
      try {
        const permissions = await getTenantRolePermissions(tenantId, roleId, 'ALL')
        if (isCancelled) {
          return
        }
        syncRolePermissions(selectedRoleId, permissions)
      } catch (error) {
        if (isCancelled) {
          return
        }
        syncRolePermissions(selectedRoleId, [])
        const message = error instanceof Error ? error.message : String(error)
        toast.error(`加载角色功能权限失败：${message}`)
      }
    }

    void loadRolePermissions()

    return () => {
      isCancelled = true
    }
  }, [selectedRoleId, syncRolePermissions, tenantId])

  const handleRolePermissionUpdate = async (
    objectId: number,
    level: PermissionLevel,
  ) => {
    if (!tenantId || !selectedRole) {
      return
    }
    const roleId = Number(selectedRole.id)
    if (!Number.isFinite(roleId)) {
      toast.error('角色ID无效，无法更新功能权限')
      return
    }

    const nextPermissions = selectedRole.permissions.map((permission) =>
      permission.objectId === objectId ? { ...permission, level } : permission,
    )

    setRoleDefinitions((prev) =>
      prev.map((role) => {
        if (role.id !== selectedRoleId) return role
        return {
          ...role,
          permissions: nextPermissions,
        }
      }),
    )

    try {
      await updateTenantRolePermissions(
        tenantId,
        roleId,
        toRolePermissionAssignments(nextPermissions),
      )
    } catch (error) {
      setRoleDefinitions((prev) =>
        prev.map((role) =>
          role.id === selectedRole.id
            ? { ...role, permissions: selectedRole.permissions }
            : role,
        ),
      )
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`更新角色功能权限失败：${message}`)
    }
  }

  const handleUserAiPermissionUpdate = (
    userId: string,
    modelId: string,
    access: AiAccessLevel,
  ) => {
    setUsers((prev) =>
      prev.map((user) => {
        if (user.id !== userId) return user
        const nextPermissions = (user.aiModelPermissions ?? []).filter(
          (permission) => permission.modelId !== modelId,
        )

        if (access !== 'DISABLE') {
          nextPermissions.push({ modelId, access })
        }

        return {
          ...user,
          aiModelPermissions:
            nextPermissions.length > 0 ? nextPermissions : undefined,
        }
      }),
    )
  }

  const handleCreateRole = async (payload: {
    name: string
    description?: string
    type: RoleType
  }): Promise<boolean> => {
    if (!tenantId) {
      toast.error('当前租户信息缺失，无法创建角色')
      return false
    }
    try {
      await createTenantRole(tenantId, payload)
      const rolesFromBackend = await listTenantRoles(tenantId)
      syncRoleDefinitions(rolesFromBackend)
      setActiveTab('members')
      toast.success(`角色「${payload.name}」创建成功`)
      return true
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`创建角色失败：${message}`)
      return false
    }
  }

  const handleUpdateRole = async (
    roleId: string,
    payload: {
      name: string
      description?: string
      type: RoleType
    },
  ): Promise<boolean> => {
    if (!tenantId) {
      toast.error('当前租户信息缺失，无法更新角色')
      return false
    }
    const parsedRoleId = Number(roleId)
    if (!Number.isFinite(parsedRoleId)) {
      toast.error('角色ID无效，无法更新')
      return false
    }
    try {
      await updateTenantRole(tenantId, parsedRoleId, payload)
      const rolesFromBackend = await listTenantRoles(tenantId)
      syncRoleDefinitions(rolesFromBackend)
      toast.success(`角色「${payload.name}」更新成功`)
      return true
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`更新角色失败：${message}`)
      return false
    }
  }

  const handleDeleteRole = async (roleId: string): Promise<boolean> => {
    if (!tenantId) {
      toast.error('当前租户信息缺失，无法删除角色')
      return false
    }
    const parsedRoleId = Number(roleId)
    if (!Number.isFinite(parsedRoleId)) {
      toast.error('角色ID无效，无法删除')
      return false
    }
    try {
      await deleteTenantRole(tenantId, parsedRoleId)
      const rolesFromBackend = await listTenantRoles(tenantId)
      syncRoleDefinitions(rolesFromBackend)
      setActiveTab('members')
      toast.success('角色删除成功')
      return true
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`删除角色失败：${message}`)
      return false
    }
  }

  if (!selectedRole) {
    return null
  }

  return (
    <section className="flex h-full w-full overflow-hidden bg-slate-50 dark:bg-[#0f172a] @container">
      <div className="flex h-full w-full overflow-hidden">
        <RoleList
          roles={roles}
          selectedRoleId={selectedRoleId}
          onSelectRole={setSelectedRoleId}
          onCreateRole={handleCreateRole}
          onUpdateRole={handleUpdateRole}
          onDeleteRole={handleDeleteRole}
        />
        <div className="flex-1 overflow-hidden">
          <div className="h-full overflow-hidden flex flex-col bg-white dark:bg-slate-900 shadow-sm dark:shadow-[0_16px_40px_-16px_rgba(0,0,0,0.45)]">
            <div className="px-8 py-6 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90">
              <div className="flex items-center justify-between gap-6">
                <div>
                  <div className="flex items-center gap-3">
                    <h2
                      className={cn(
                        TYPOGRAPHY.body,
                        'font-semibold text-slate-900 dark:text-white tracking-tight',
                      )}
                    >
                      {selectedRole.name}
                    </h2>
                  </div>
                  {hasSelectedRoleUsers ? (
                    <div className="flex items-center gap-4 mt-2">
                      <div className="flex -space-x-1.5">
                        {selectedRolePreviewUsers.map((user) => (
                          <div
                            key={user.id}
                            className="w-6 h-6 rounded-full bg-slate-100 dark:bg-slate-800 border-2 border-white dark:border-slate-900 flex items-center justify-center text-tiny font-black text-slate-600 dark:text-slate-200"
                          >
                            {user.name.slice(0, 2).toUpperCase()}
                          </div>
                        ))}
                      </div>
                      <span
                        className={cn(
                          TYPOGRAPHY.caption,
                          'font-semibold text-slate-600 dark:text-slate-300',
                        )}
                      >
                        共 {selectedRoleUsers.length} 名成员
                      </span>
                    </div>
                  ) : null}
                </div>
                <Button
                  size="header"
                  onClick={() => {
                    setActiveTab('members')
                    setIsAddModalOpen(true)
                  }}
                  className="shadow-lg shadow-slate-200/60 dark:shadow-none"
                >
                  <Link2 size={14} />
                  邀请成员
                </Button>
              </div>
            </div>
            <div className="h-12 px-6 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90 flex items-stretch">
              <div className="flex h-full items-stretch justify-between gap-4 w-full">
                <div className="flex h-full items-stretch gap-8">
                  <button
                    type="button"
                    onClick={() => setActiveTab('members')}
                    className={cn(
                      TYPOGRAPHY.bodySm,
                      'h-full -mb-px font-medium border-b-2 transition-all inline-flex items-center gap-2 leading-none',
                      activeTab === 'members'
                        ? 'border-brand-600 text-brand-600'
                        : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
                    )}
                  >
                    <Users size={16} />
                    成员列表
                    <span className="ml-0.5 px-2 py-0.5 rounded-full text-xs font-semibold bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300">
                      {selectedRole.userCount}
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setActiveTab('functional')}
                    className={cn(
                      TYPOGRAPHY.bodySm,
                      'h-full -mb-px font-medium border-b-2 transition-all inline-flex items-center gap-2 leading-none',
                      activeTab === 'functional'
                        ? 'border-brand-600 text-brand-600'
                        : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
                    )}
                  >
                    <Shield size={16} />
                    功能权限
                  </button>
                </div>
              </div>
            </div>
            <div
              className={cn(
                'flex-1 overflow-y-auto bg-slate-50 dark:bg-slate-950/35 custom-scrollbar',
                activeTab === 'functional' ? 'px-0 py-0' : 'px-6 py-6',
              )}
            >
              {activeTab === 'members' && (
                <MembersList
                  role={selectedRole}
                  users={users}
                  onUpdateUserModelAccess={handleUserAiPermissionUpdate}
                  showToolbar={false}
                  isAddModalOpen={isAddModalOpen}
                  onOpenAddModal={() => setIsAddModalOpen(true)}
                  onCloseAddModal={() => setIsAddModalOpen(false)}
                />
              )}
              {activeTab === 'functional' && (
                <FunctionalPermission
                  role={selectedRole}
                  onUpdatePermission={handleRolePermissionUpdate}
                  className="px-6 py-6 animate-in fade-in zoom-in-95 duration-200"
                />
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
