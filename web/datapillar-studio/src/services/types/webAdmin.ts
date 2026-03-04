/**
 * unifiedAPIResponse type definition
 * with backend ApiResponse Keep the format consistent
 */

import type { ApiResponse } from '@/api/types/api'

export type { ApiError, ApiResponse, PageRequest } from '@/api/types/api'
export { createErrorResponse, createSuccessResponse, extractApiData, isApiSuccess } from '@/api/types/api'

export type WebAdminResponse<T = unknown> = ApiResponse<T>
export type PageResponse<T> = ApiResponse<T[]>
