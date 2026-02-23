export type StudioRoleType = 'ADMIN' | 'USER'

export interface StudioRole {
  id: number
  tenantId: number
  type: string
  name: string
  description?: string | null
  status?: number | null
  sort?: number | null
  isBuiltin?: number | null
  memberCount?: number | null
}

export interface StudioRoleMember {
  userId: number
  username: string
  nickname?: string | null
  email?: string | null
  phone?: string | null
  memberStatus: number
  joinedAt: string
  assignedAt: string
}

export interface StudioRoleMembersResponse {
  roleId: number
  roleName: string
  roleType: string
  roleStatus: number
  roleBuiltin: number
  memberCount: number
  members: StudioRoleMember[]
}

export interface CreateStudioRoleRequest {
  name: string
  description?: string
  type: StudioRoleType
}

export interface UpdateStudioRoleRequest {
  name?: string
  description?: string
  type?: StudioRoleType
}

export type StudioRolePermissionScope = 'ALL' | 'ASSIGNED'

export interface StudioRolePermissionSource {
  objectId: number
  roleId: number
  roleName: string
  permissionCode: string
}

export interface StudioRolePermission {
  objectId: number
  objectName: string
  objectPath?: string | null
  objectType?: string | null
  location?: string | null
  categoryId?: number | null
  categoryName?: string | null
  permissionCode?: string | null
  tenantPermissionCode?: string | null
  roleSources?: StudioRolePermissionSource[] | null
}

export interface UpdateStudioRolePermissionAssignment {
  objectId: number
  permissionId?: number
  permissionCode?: string
}
