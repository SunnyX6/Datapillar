import { API_BASE, API_PATH, requestData, requestEnvelope } from '@/lib/api'
import { pickDefinedParams } from './studioCommon'
import type {
  CreateStudioRoleRequest,
  StudioRole,
  StudioRolePermission,
  StudioRolePermissionScope,
  StudioRoleMembersResponse,
  UpdateStudioRolePermissionAssignment,
  UpdateStudioRoleRequest
} from '@/types/studio/role'

export type {
  CreateStudioRoleRequest,
  StudioRole,
  StudioRoleMember,
  StudioRolePermission,
  StudioRolePermissionScope,
  StudioRoleMembersResponse,
  StudioRoleType,
  UpdateStudioRolePermissionAssignment,
  UpdateStudioRoleRequest
} from '@/types/studio/role'

export async function listTenantRoles(_tenantId: number): Promise<StudioRole[]> {
  return requestData<StudioRole[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.roles
  })
}

export async function createTenantRole(
  _tenantId: number,
  request: CreateStudioRoleRequest
): Promise<void> {
  await requestEnvelope<void, CreateStudioRoleRequest>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.role,
    method: 'POST',
    data: request
  })
}

export async function updateTenantRole(
  _tenantId: number,
  roleId: number,
  request: UpdateStudioRoleRequest
): Promise<void> {
  await requestEnvelope<void, UpdateStudioRoleRequest>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.roleDetail(roleId),
    method: 'PATCH',
    data: request
  })
}

export async function deleteTenantRole(
  _tenantId: number,
  roleId: number
): Promise<void> {
  await requestEnvelope<void>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.roleDetail(roleId),
    method: 'DELETE'
  })
}

export async function getTenantRoleMembers(
  _tenantId: number,
  roleId: number,
  status?: number
): Promise<StudioRoleMembersResponse> {
  return requestData<StudioRoleMembersResponse>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.roleMembers(roleId),
    params: pickDefinedParams({ status })
  })
}

export async function getTenantRolePermissions(
  _tenantId: number,
  roleId: number,
  scope: StudioRolePermissionScope = 'ALL'
): Promise<StudioRolePermission[]> {
  return requestData<StudioRolePermission[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.rolePermissions(roleId),
    params: pickDefinedParams({ scope })
  })
}

export async function updateTenantRolePermissions(
  _tenantId: number,
  roleId: number,
  assignments: UpdateStudioRolePermissionAssignment[]
): Promise<void> {
  await requestEnvelope<void, UpdateStudioRolePermissionAssignment[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.rolePermissions(roleId),
    method: 'PUT',
    data: assignments
  })
}
