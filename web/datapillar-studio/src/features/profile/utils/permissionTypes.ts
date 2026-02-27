import type { PermissionLevel } from './permissionConstants'

export type UserStatus = '已激活' | '已邀请' | '已禁用'

interface PermissionResourceBase {
  objectId: number
  parentId?: number
  objectName: string
  objectPath?: string
  objectType?: string
  location?: string
  sort: number
  categoryName: string
}

export interface PermissionResource extends PermissionResourceBase {
  level: PermissionLevel
  tenantLevel: PermissionLevel
  children: PermissionResource[]
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
  aiModelId: number
  access: AiAccessLevel
}

export interface UserItem {
  id: string
  name: string
  email: string
  avatarUrl?: string
  roleId: string
  level?: number
  status: UserStatus
  lastActive: string
  department?: string
  dataPrivileges?: UserDataPrivilege[]
  aiModelPermissions?: AiModelPermission[]
}

export type PermissionTab = 'members' | 'functional'

interface RoleSource {
  id: number
  type?: string | null
  name: string
  description?: string | null
  level?: number | null
  memberCount?: number | null
}

interface RolePermissionSource {
  objectId: number
  parentId?: number | null
  objectName: string
  objectPath?: string | null
  objectType?: string | null
  location?: string | null
  sort?: number | null
  categoryName?: string | null
  permissionCode?: string | null
  tenantPermissionCode?: string | null
  children?: RolePermissionSource[] | null
}

export function resolveRoleType(type?: string | null): RoleType {
  return type?.toUpperCase() === 'ADMIN' ? 'ADMIN' : 'USER'
}

export function normalizePermissionLevel(permissionCode?: string | null): PermissionLevel {
  if (permissionCode?.toUpperCase() === 'ADMIN') {
    return 'ADMIN'
  }
  if (permissionCode?.toUpperCase() === 'READ') {
    return 'READ'
  }
  return 'DISABLE'
}

export function mapRolePermissionToResource(permission: RolePermissionSource): PermissionResource {
  return {
    objectId: permission.objectId,
    parentId: permission.parentId ?? undefined,
    objectName: permission.objectName,
    objectPath: permission.objectPath?.trim() || undefined,
    objectType: permission.objectType?.trim() || undefined,
    location: permission.location?.trim() || undefined,
    sort: permission.sort ?? 0,
    categoryName: permission.categoryName?.trim() || '未分类',
    level: normalizePermissionLevel(permission.permissionCode),
    tenantLevel: normalizePermissionLevel(permission.tenantPermissionCode),
    children: (permission.children ?? []).map(mapRolePermissionToResource)
  }
}

export function mapStudioRoleToDefinition(role: RoleSource): RoleDefinition {
  return {
    id: String(role.id),
    type: resolveRoleType(role.type),
    name: role.name,
    description: role.description?.trim() ?? '',
    isSystem: role.level === 0,
    memberCount: role.memberCount ?? 0,
    permissions: [],
    dataPrivileges: [],
    aiModelPermissions: []
  }
}
