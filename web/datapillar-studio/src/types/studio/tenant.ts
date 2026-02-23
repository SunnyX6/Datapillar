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

export interface ListTenantSsoIdentitiesParams {
  provider?: string
  userId?: number
}
