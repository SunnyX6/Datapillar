/**
 * 认证 API
 *
 * 封装认证相关的 API 调用
 */

import axios from 'axios'
import type { WebAdminResponse } from '@/types/webAdmin'
import type { LoginRequest, LoginResponse } from '@/types/auth'

/**
 * Auth API 客户端
 */
const authClient = axios.create({
  baseURL: '/api/auth',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
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
    const response = await authClient.post<WebAdminResponse<LoginResponse>>('/login', request)

    if (response.data.code !== 'OK') {
      throw new Error(response.data.message || '登录失败')
    }

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
    await authClient.post<WebAdminResponse<void>>('/logout')
  } catch (error) {
    console.error('登出失败:', error)
  }
}
