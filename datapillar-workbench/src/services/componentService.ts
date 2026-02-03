/**
 * 组件服务
 *
 * 调用后端获取工作流组件信息
 */

import { createApiClient } from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'

export interface JobComponent {
  id: number
  componentCode: string
  componentName: string
  componentType: 'SQL' | 'SCRIPT' | 'SYNC'
  jobParams: Record<string, unknown>
  description: string
  icon: string | null
  color: string | null
  sortOrder: number
}

const componentClient = createApiClient({
  baseURL: '/api/workbench/components',
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

/**
 * 获取所有可用组件
 */
export async function getAllComponents(): Promise<JobComponent[]> {
  try {
    const response = await componentClient.get<ApiResponse<JobComponent[]>>('')
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * 根据 code 获取组件信息
 */
export async function getComponentByCode(code: string): Promise<JobComponent> {
  try {
    const response = await componentClient.get<ApiResponse<JobComponent>>(`/code/${code}`)
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * 组件类型默认样式
 */
export const COMPONENT_TYPE_DEFAULTS: Record<string, { icon: string; color: string }> = {
  SQL: { icon: 'Database', color: '#3b82f6' },
  SCRIPT: { icon: 'Terminal', color: '#10b981' },
  SYNC: { icon: 'ArrowRightLeft', color: '#f59e0b' }
}

/**
 * 通用默认样式
 */
export const DEFAULT_COMPONENT_STYLE = { icon: 'Box', color: '#6b7280' }

/**
 * 获取组件样式（优先级：组件配置 > 类型默认 > 通用默认）
 */
export function getComponentStyle(component: JobComponent | undefined): { icon: string; color: string } {
  if (component?.icon && component?.color) {
    return { icon: component.icon, color: component.color }
  }

  if (component?.componentType && COMPONENT_TYPE_DEFAULTS[component.componentType]) {
    return COMPONENT_TYPE_DEFAULTS[component.componentType]
  }

  return DEFAULT_COMPONENT_STYLE
}
