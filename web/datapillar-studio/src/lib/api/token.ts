/**
 * Token API helpers
 *
 * Token is stored in HttpOnly cookies. The client only queries token state.
 */

import { createApiClient } from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'
import type { TokenInfo } from '@/types/auth'

const tokenClient = createApiClient({
  baseURL: '/api/auth',
  timeout: 30000
})

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
  return 'Unknown error'
}

export async function getTokenInfo(): Promise<TokenInfo> {
  try {
    const response = await tokenClient.get<ApiResponse<TokenInfo>>('/validate')
    if (typeof response.data.data === 'undefined') {
      throw new Error('接口响应缺少 data 字段')
    }
    return response.data.data
  } catch (error) {
    if (error instanceof Error) {
      throw error
    }
    throw new Error(extractErrorMessage(error))
  }
}

export async function isAuthenticated(): Promise<boolean> {
  try {
    await getTokenInfo()
    return true
  } catch {
    return false
  }
}
