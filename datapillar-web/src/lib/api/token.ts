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
    const response = await tokenClient.get<ApiResponse<TokenInfo>>('/token-info')
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export async function isAuthenticated(): Promise<boolean> {
  try {
    const tokenInfo = await getTokenInfo()
    return tokenInfo.valid
  } catch {
    return false
  }
}
