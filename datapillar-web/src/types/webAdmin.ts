/**
 * 统一的API响应类型定义
 * 与后端 WebAdminResponse 格式保持一致
 */

/**
 * Web Admin 统一响应格式
 * 与后端 com.sunny.admin.response.WebAdminResponse 保持一致
 */
export interface WebAdminResponse<T = unknown> {
  /** 响应状态码，成功时为 'OK' */
  code: string
  /** 响应消息 */
  message: string
  /** 响应数据 */
  data: T
}

/**
 * 分页响应数据结构
 */
export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/**
 * 通用的分页请求参数
 */
export interface PageRequest {
  current?: number
  size?: number
  [key: string]: unknown
}

/**
 * API错误响应
 */
export interface ApiError {
  code: string
  message: string
  details?: unknown
}

/**
 * 检查响应是否成功
 */
export function isApiSuccess<T>(response: WebAdminResponse<T>): boolean {
  return response.code === 'OK'
}

/**
 * 从ApiResponse中提取数据
 */
export function extractApiData<T>(response: WebAdminResponse<T>): T {
  if (!isApiSuccess(response)) {
    throw new Error(response.message || 'API请求失败')
  }
  return response.data
}

/**
 * 创建成功的API响应
 */
export function createSuccessResponse<T>(data: T, message = '操作成功'): WebAdminResponse<T> {
  return {
    code: 'OK',
    message,
    data
  }
}

/**
 * 创建错误的API响应
 */
export function createErrorResponse(code: string, message: string): WebAdminResponse<null> {
  return {
    code,
    message,
    data: null
  }
}
