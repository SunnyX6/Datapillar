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

export interface CreateTenantInvitationRequest {
  roleId: number
  expiresAt: string
}

export interface CreateTenantInvitationResponse {
  invitationId: number
  inviteCode: string
  inviteUri: string
  expiresAt: string
  tenantName: string
  roleId: number
  roleName: string
  inviterName: string
}

export interface InvitationRegisterRequest {
  inviteCode: string
  username: string
  email: string
  password: string
}

export interface InvitationDetailResponse {
  inviteCode: string
  tenantName: string
  roleId: number
  roleName: string
  inviterName: string
  expiresAt: string | null
  status: number
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
