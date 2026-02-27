export type StudioRoleType = 'ADMIN' | 'USER'

export interface StudioRole {
  id: number
  tenantId: number
  type: string
  name: string
  description?: string | null
  level?: number | null
  status?: number | null
  sort?: number | null
  memberCount?: number | null
}

export interface StudioRoleMember {
  userId: number
  username: string
  nickname?: string | null
  email?: string | null
  phone?: string | null
  userLevel?: number | null
  memberStatus: number
  joinedAt: string
  assignedAt: string
}

export interface StudioRoleMembersResponse {
  roleId: number
  roleName: string
  roleType: string
  roleLevel: number
  roleStatus: number
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

export interface StudioRolePermission {
  objectId: number
  parentId?: number | null
  objectName: string
  objectPath?: string | null
  objectType?: string | null
  location?: string | null
  categoryId?: number | null
  categoryName?: string | null
  sort?: number | null
  permissionCode?: string | null
  tenantPermissionCode?: string | null
  children?: StudioRolePermission[] | null
}

export interface UpdateStudioRolePermissionAssignment {
  objectId: number
  permissionId?: number
  permissionCode?: string
}
