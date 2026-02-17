/**
 * 认证 API
 *
 * 封装认证相关的 API 调用
 */

import { createApiClient } from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'
import type {
  LoginResult,
  LoginTenantRequest,
  PasswordLoginRequest,
  SsoLoginRequest
} from '@/types/auth'

/**
 * Auth API 客户端
 */
const loginClient = createApiClient({
  baseURL: '/api/login',
  timeout: 30000
})

/**
 * 从错误中提取错误信息
 */
function extractErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as { response?: { data?: { message?: string } } }
    if (axiosError.response?.data?.message) {
      return axiosError.response.data.message
    }
  }
  if (error instanceof Error) {
    return error.message
  }
  return '未知错误'
}

function extractApiData<T>(response: ApiResponse<T>): T {
  if (typeof response.data === 'undefined') {
    throw new Error('接口响应缺少 data 字段')
  }
  return response.data
}

/**
 * 账号密码登录（stage=AUTH）
 */
export async function login(request: PasswordLoginRequest): Promise<LoginResult> {
  try {
    const response = await loginClient.post<ApiResponse<LoginResult>>('', {
      stage: 'AUTH',
      loginAlias: request.loginAlias,
      password: request.password,
      rememberMe: request.rememberMe,
      tenantCode: request.tenantCode
    })
    return extractApiData(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * SSO 登录（stage=AUTH）
 */
export async function loginSso(request: SsoLoginRequest): Promise<LoginResult> {
  try {
    const response = await loginClient.post<ApiResponse<LoginResult>>('/sso', {
      stage: 'AUTH',
      provider: request.provider,
      code: request.code,
      state: request.state,
      rememberMe: request.rememberMe,
      tenantCode: request.tenantCode
    })
    return extractApiData(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * 完成租户选择登录（stage=TENANT_SELECT）
 */
export async function loginTenant(request: LoginTenantRequest): Promise<LoginResult> {
  try {
    const response = await loginClient.post<ApiResponse<LoginResult>>('', {
      stage: 'TENANT_SELECT',
      tenantId: request.tenantId
    })
    return extractApiData(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * 调用登出接口
 */
export async function logout(): Promise<void> {
  try {
    await loginClient.post<ApiResponse<void>>('/logout')
  } catch (error) {
    console.error('登出失败:', error)
  }
}
