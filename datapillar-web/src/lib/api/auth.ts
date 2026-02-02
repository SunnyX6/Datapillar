/**
 * 认证 API
 *
 * 封装认证相关的 API 调用
 */

import { createApiClient } from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'
import type { LoginRequest, LoginResponse, SsoLoginRequest, SsoQrResponse } from '@/types/auth'

/**
 * Auth API 客户端
 */
const authClient = createApiClient({
  baseURL: '/api/auth',
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

/**
 * 调用登录接口
 */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  try {
    const response = await authClient.post<ApiResponse<LoginResponse>>('/login', request)
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * 获取 SSO 扫码配置
 */
export async function getSsoQr(tenantCode: string, provider: string): Promise<SsoQrResponse> {
  try {
    const response = await authClient.get<ApiResponse<SsoQrResponse>>('/sso/qr', {
      params: { tenantCode, provider }
    })
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * SSO 登录
 */
export async function ssoLogin(request: SsoLoginRequest): Promise<LoginResponse> {
  try {
    const response = await authClient.post<ApiResponse<LoginResponse>>('/sso/login', request)
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * 调用登出接口
 */
export async function logout(): Promise<void> {
  try {
    await authClient.post<ApiResponse<void>>('/logout')
  } catch (error) {
    console.error('登出失败:', error)
  }
}
