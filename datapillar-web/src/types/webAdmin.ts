/**
 * 统一的API响应类型定义
 * 与后端 ApiResponse 格式保持一致
 */

import type { ApiResponse } from './api'

export type { ApiError, ApiResponse, PageRequest } from './api'
export { createErrorResponse, createSuccessResponse, extractApiData, isApiSuccess } from './api'

export type WebAdminResponse<T = unknown> = ApiResponse<T>
export type PageResponse<T> = ApiResponse<T[]>
