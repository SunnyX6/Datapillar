export interface ApiResponse<T = unknown> {
  status: number
  code: string
  message: string
  data: T
  timestamp: string
  path?: string
  traceId?: string
  limit?: number
  offset?: number
  total?: number
}

export interface PageRequest {
  limit?: number
  offset?: number
  [key: string]: unknown
}

export interface ApiError extends Error {
  code?: string
  status?: number
  response?: ApiResponse<unknown>
}

export function isApiSuccess(response: ApiResponse<unknown>): boolean {
  return response.status === 200 && response.code === 'OK'
}

export function extractApiData<T>(response: ApiResponse<T>): T {
  if (!isApiSuccess(response)) {
    throw new Error(response.message || 'API request failed')
  }
  return response.data
}

export function createSuccessResponse<T>(data: T, message = 'Success'): ApiResponse<T> {
  return {
    status: 200,
    code: 'OK',
    message,
    data,
    timestamp: new Date().toISOString()
  }
}

export function createErrorResponse(code: string, message: string, status = 500): ApiResponse<null> {
  return {
    status,
    code,
    message,
    data: null,
    timestamp: new Date().toISOString()
  }
}
