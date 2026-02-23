import { API_BASE, API_PATH, requestData } from '@/lib/api'
import { pickDefinedParams } from './studioCommon'
import type {
  ListTenantSsoIdentitiesParams,
  StudioTenant,
  StudioTenantFeatureAudit,
  StudioTenantInvitation,
  StudioTenantSsoIdentity,
  StudioTenantUser
} from '@/types/studio/tenant'

export type {
  ListTenantSsoIdentitiesParams,
  StudioTenant,
  StudioTenantFeatureAudit,
  StudioTenantInvitation,
  StudioTenantSsoIdentity,
  StudioTenantUser
} from '@/types/studio/tenant'

export async function listTenants(status?: number): Promise<StudioTenant[]> {
  return requestData<StudioTenant[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.tenants,
    params: pickDefinedParams({ status })
  })
}

export async function listTenantUsers(
  _tenantId: number,
  status?: number
): Promise<StudioTenantUser[]> {
  return requestData<StudioTenantUser[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.users,
    params: pickDefinedParams({ status })
  })
}

export async function listTenantInvitations(
  _tenantId: number,
  status?: number
): Promise<StudioTenantInvitation[]> {
  return requestData<StudioTenantInvitation[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.invitations,
    params: pickDefinedParams({ status })
  })
}

export async function listTenantFeatureAudits(
  _tenantId: number
): Promise<StudioTenantFeatureAudit[]> {
  return requestData<StudioTenantFeatureAudit[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.featureAudits
  })
}

export async function listTenantSsoIdentities(
  _tenantId: number,
  params: ListTenantSsoIdentitiesParams = {}
): Promise<StudioTenantSsoIdentity[]> {
  return requestData<StudioTenantSsoIdentity[]>({
    baseURL: API_BASE.studioAdmin,
    url: API_PATH.tenantAdmin.ssoIdentities,
    params: pickDefinedParams({
      provider: params.provider,
      userId: params.userId
    })
  })
}
