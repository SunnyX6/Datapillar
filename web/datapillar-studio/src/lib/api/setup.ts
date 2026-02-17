/**
 * 系统初始化 Setup API
 */

import { createApiClient } from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'

export type SetupStepStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'

export interface SetupStep {
  code: string
  name: string
  description: string
  status: SetupStepStatus
}

export interface SetupStatusResponse {
  schemaReady: boolean
  initialized: boolean
  currentStep: string
  steps: SetupStep[]
}

export interface SetupInitializeRequest {
  organizationName: string
  adminName: string
  username: string
  email: string
  password: string
}

export interface SetupInitializeResponse {
  tenantId: number
  userId: number
}

const setupClient = createApiClient({
  baseURL: '/api/studio/setup',
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
  return '未知错误'
}

function requireApiData<T>(payload: ApiResponse<T>): T {
  if (typeof payload.data === 'undefined') {
    throw new Error('接口响应缺少 data 字段')
  }
  return payload.data
}

export async function getSetupStatus(): Promise<SetupStatusResponse> {
  try {
    const response = await setupClient.get<ApiResponse<SetupStatusResponse>>('/status')
    return requireApiData(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export async function initializeSetup(
  request: SetupInitializeRequest
): Promise<SetupInitializeResponse> {
  try {
    const response = await setupClient.post<ApiResponse<SetupInitializeResponse>>('', request)
    return requireApiData(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
