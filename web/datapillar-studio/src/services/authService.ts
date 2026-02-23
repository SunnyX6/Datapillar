import { API_BASE, API_PATH, requestData, requestEnvelope } from '@/lib/api'
import type {
  LoginResult,
  LoginTenantRequest,
  PasswordLoginRequest,
  SsoLoginRequest
} from '@/types/auth'

export async function login(request: PasswordLoginRequest): Promise<LoginResult> {
  return requestData<LoginResult, {
    stage: 'AUTH'
    loginAlias: string
    password: string
    rememberMe?: boolean
    tenantCode?: string
  }>({
    baseURL: API_BASE.login,
    url: API_PATH.login.root,
    method: 'POST',
    data: {
      stage: 'AUTH',
      loginAlias: request.loginAlias,
      password: request.password,
      rememberMe: request.rememberMe,
      tenantCode: request.tenantCode
    }
  })
}

export async function loginSso(request: SsoLoginRequest): Promise<LoginResult> {
  return requestData<LoginResult, {
    stage: 'AUTH'
    provider: string
    code: string
    state: string
    rememberMe?: boolean
    tenantCode?: string
  }>({
    baseURL: API_BASE.login,
    url: API_PATH.login.sso,
    method: 'POST',
    data: {
      stage: 'AUTH',
      provider: request.provider,
      code: request.code,
      state: request.state,
      rememberMe: request.rememberMe,
      tenantCode: request.tenantCode
    }
  })
}

export async function loginTenant(request: LoginTenantRequest): Promise<LoginResult> {
  return requestData<LoginResult, { stage: 'TENANT_SELECT'; tenantId: number }>({
    baseURL: API_BASE.login,
    url: API_PATH.login.root,
    method: 'POST',
    data: {
      stage: 'TENANT_SELECT',
      tenantId: request.tenantId
    }
  })
}

export async function logout(): Promise<void> {
  await requestEnvelope<void>({
    baseURL: API_BASE.login,
    url: API_PATH.login.logout,
    method: 'POST'
  })
}
