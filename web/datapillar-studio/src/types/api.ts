export interface ApiResponse<T = unknown> {
  code: number
  data?: T
  limit?: number
  offset?: number
  total?: number
}

export interface ErrorResponse {
  code: number
  type: string
  message: string
  stack?: string[]
}

export interface PageRequest {
  limit?: number
  offset?: number
  [key: string]: unknown
}

export interface ApiError extends Error {
  code?: number
  status?: number
  response?: ApiResponse<unknown> | ErrorResponse
}

export function isApiResponse<T = unknown>(response: unknown): response is ApiResponse<T> {
  if (!response || typeof response !== 'object') {
    return false
  }
  const candidate = response as Record<string, unknown>
  return typeof candidate.code === 'number'
}

export function isErrorResponse(response: unknown): response is ErrorResponse {
  if (!response || typeof response !== 'object') {
    return false
  }
  const candidate = response as Record<string, unknown>
  return typeof candidate.code === 'number' &&
    typeof candidate.type === 'string' &&
    typeof candidate.message === 'string'
}

export function isApiSuccess(response: { code: number }): boolean {
  return response.code === 0
}

export function extractApiData<T>(response: ApiResponse<T>): T {
  if (!isApiSuccess(response)) {
    throw new Error(`API request failed (code: ${response.code})`)
  }
  if (typeof response.data === 'undefined') {
    throw new Error('API response missing data')
  }
  return response.data
}

export function createSuccessResponse<T>(data: T): ApiResponse<T> {
  return {
    code: 0,
    data
  }
}

export function createErrorResponse(
  code: number,
  type: string,
  message: string,
  stack?: string[]
): ErrorResponse {
  return {
    code,
    type,
    message,
    stack
  }
}
