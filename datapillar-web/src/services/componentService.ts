/**
 * 组件服务
 *
 * 调用后端获取工作流组件信息
 */

import axios from 'axios'

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

interface WebAdminResponse<T> {
  code: string
  message: string
  data: T
}

const componentClient = axios.create({
  baseURL: '/api/web-admin/components',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * 获取所有可用组件
 */
export async function getAllComponents(): Promise<JobComponent[]> {
  const response = await componentClient.get<WebAdminResponse<JobComponent[]>>('')
  if (response.data.code !== 'OK') {
    throw new Error(response.data.message || '获取组件列表失败')
  }
  return response.data.data
}

/**
 * 根据 code 获取组件信息
 */
export async function getComponentByCode(code: string): Promise<JobComponent> {
  const response = await componentClient.get<WebAdminResponse<JobComponent>>(`/code/${code}`)
  if (response.data.code !== 'OK') {
    throw new Error(response.data.message || '获取组件信息失败')
  }
  return response.data.data
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
