import { createApiClient } from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'
import { pickDefinedParams, requireApiData } from './studioCommon'

const studioAdminClient = createApiClient({
  baseURL: '/api/studio/admin',
  timeout: 30000
})

export interface StudioTenant {
  id: number
  code: string
  name: string
  type: string
  status: number
  createdAt: string
  updatedAt: string
}

export interface StudioTenantUser {
  id: number
  tenantId: number
  username: string
  nickname?: string | null
  email?: string | null
  phone?: string | null
  status: number
  createdAt: string
  updatedAt: string
}

export interface StudioTenantInvitation {
  id: number
  tenantId: number
  inviteeEmail?: string | null
  inviteeMobile?: string | null
  status: number
  inviteCode: string
  expiresAt: string
  createdAt: string
}

export interface StudioTenantFeatureAudit {
  id: number
  tenantId: number
  objectId: number
  action: string
  beforeStatus?: number | null
  afterStatus?: number | null
  beforePermissionId?: number | null
  afterPermissionId?: number | null
  operatorUserId?: number | null
  operatorTenantId?: number | null
  requestId?: string | null
  createdAt: string
}

export interface StudioTenantSsoIdentity {
  id: number
  userId: number
  provider: string
  externalUserId: string
  createdAt: string
  updatedAt: string
}

export async function listTenants(status?: number): Promise<StudioTenant[]> {
  const response = await studioAdminClient.get<ApiResponse<StudioTenant[]>>('/tenants', {
    params: pickDefinedParams({ status })
  })
  return requireApiData(response.data)
}

export async function listTenantUsers(
  tenantId: number,
  status?: number
): Promise<StudioTenantUser[]> {
  const response = await studioAdminClient.get<ApiResponse<StudioTenantUser[]>>(
    `/tenants/${tenantId}/users`,
    {
      params: pickDefinedParams({ status })
    }
  )
  return requireApiData(response.data)
}

export async function listTenantInvitations(
  tenantId: number,
  status?: number
): Promise<StudioTenantInvitation[]> {
  const response = await studioAdminClient.get<ApiResponse<StudioTenantInvitation[]>>(
    `/tenants/${tenantId}/invitations`,
    {
      params: pickDefinedParams({ status })
    }
  )
  return requireApiData(response.data)
}

export async function listTenantFeatureAudits(
  tenantId: number
): Promise<StudioTenantFeatureAudit[]> {
  const response = await studioAdminClient.get<ApiResponse<StudioTenantFeatureAudit[]>>(
    `/tenants/${tenantId}/features/audits`
  )
  return requireApiData(response.data)
}

export interface ListTenantSsoIdentitiesParams {
  provider?: string
  userId?: number
}

export async function listTenantSsoIdentities(
  tenantId: number,
  params: ListTenantSsoIdentitiesParams = {}
): Promise<StudioTenantSsoIdentity[]> {
  const response = await studioAdminClient.get<ApiResponse<StudioTenantSsoIdentity[]>>(
    `/tenants/${tenantId}/sso/identities`,
    {
      params: pickDefinedParams({
        provider: params.provider,
        userId: params.userId
      })
    }
  )
  return requireApiData(response.data)
}
