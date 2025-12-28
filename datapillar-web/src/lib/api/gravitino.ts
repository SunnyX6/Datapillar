/**
 * Gravitino API 统一客户端
 *
 * 提供统一的错误处理和请求封装，自动通过 toast 显示错误信息
 */

import axios, { type AxiosInstance, type AxiosError } from 'axios'
import { toast } from 'sonner'
import type { GravitinoBaseResponse } from '@/types/oneMeta'

/**
 * Gravitino 错误响应结构
 * 对应后端 ErrorResponse 类
 */
export interface GravitinoErrorResponse {
  code: number
  type: string
  message: string
  stack?: string[]
}

/**
 * Gravitino API 错误
 */
export class GravitinoError extends Error {
  code: number
  type: string
  stack?: string[]

  constructor(response: GravitinoErrorResponse) {
    super(response.message)
    this.name = 'GravitinoError'
    this.code = response.code
    this.type = response.type
    this.stack = response.stack?.join('\n')
  }
}

/**
 * 从 axios 错误中提取 Gravitino 错误信息
 */
function extractGravitinoError(error: AxiosError): GravitinoErrorResponse {
  const responseData = error.response?.data as GravitinoErrorResponse | undefined

  // 后端返回的 Gravitino 错误响应
  if (responseData?.message) {
    return {
      code: responseData.code ?? error.response?.status ?? -1,
      type: responseData.type ?? 'UnknownError',
      message: responseData.message,
      stack: responseData.stack
    }
  }

  // 网络错误或其他错误
  if (error.code === 'ECONNABORTED') {
    return { code: -1, type: 'TimeoutError', message: '请求超时，请检查网络连接' }
  }

  if (!error.response) {
    return { code: -1, type: 'NetworkError', message: '网络连接失败，请检查网络状态' }
  }

  // HTTP 状态码错误
  const status = error.response.status
  const statusMessages: Record<number, string> = {
    400: '请求参数错误',
    401: '未授权，请重新登录',
    403: '无权限访问',
    404: '请求的资源不存在',
    500: '服务器内部错误',
    502: '网关错误',
    503: '服务暂时不可用'
  }

  return {
    code: status,
    type: 'HttpError',
    message: statusMessages[status] ?? `请求失败 (${status})`
  }
}

/**
 * 检查 Gravitino 响应，code !== 0 时抛出错误
 */
function checkResponse<T extends GravitinoBaseResponse>(response: T): T {
  if (response.code !== 0) {
    const errorResponse: GravitinoErrorResponse = {
      code: response.code,
      type: 'GravitinoError',
      message: (response as GravitinoBaseResponse & { message?: string }).message ?? `操作失败 (code: ${response.code})`
    }
    throw new GravitinoError(errorResponse)
  }
  return response
}

/**
 * 创建 Gravitino API 客户端
 */
function createGravitinoClient(): AxiosInstance {
  const client = axios.create({
    baseURL: '/api/onemeta',
    timeout: 30000,
    headers: { 'Content-Type': 'application/json' }
  })

  return client
}

const gravitinoClient = createGravitinoClient()

/**
 * 封装 GET 请求
 * 自动处理错误并通过 toast 显示，同时抛出异常供调用方处理
 */
export async function gravitinoGet<T extends GravitinoBaseResponse>(url: string): Promise<T> {
  try {
    const response = await gravitinoClient.get<T>(url)
    return checkResponse(response.data)
  } catch (error) {
    const gravitinoError = handleError(error)
    throw gravitinoError
  }
}

/**
 * 封装 POST 请求
 */
export async function gravitinoPost<T extends GravitinoBaseResponse>(
  url: string,
  data?: unknown
): Promise<T> {
  try {
    const response = await gravitinoClient.post<T>(url, data)
    return checkResponse(response.data)
  } catch (error) {
    const gravitinoError = handleError(error)
    throw gravitinoError
  }
}

/**
 * 封装 PUT 请求
 */
export async function gravitinoPut<T extends GravitinoBaseResponse>(
  url: string,
  data?: unknown
): Promise<T> {
  try {
    const response = await gravitinoClient.put<T>(url, data)
    return checkResponse(response.data)
  } catch (error) {
    const gravitinoError = handleError(error)
    throw gravitinoError
  }
}

/**
 * 封装 DELETE 请求
 */
export async function gravitinoDelete<T extends GravitinoBaseResponse>(url: string): Promise<T> {
  try {
    const response = await gravitinoClient.delete<T>(url)
    return checkResponse(response.data)
  } catch (error) {
    const gravitinoError = handleError(error)
    throw gravitinoError
  }
}

/**
 * 统一错误处理
 * 1. 解析错误信息
 * 2. 通过 toast 显示给用户
 * 3. 返回 GravitinoError 供调用方处理
 */
function handleError(error: unknown): GravitinoError {
  // 如果已经是 GravitinoError，直接使用
  if (error instanceof GravitinoError) {
    toast.error(error.message)
    return error
  }

  // 解析 axios 错误
  if (axios.isAxiosError(error)) {
    const errorResponse = extractGravitinoError(error)
    toast.error(errorResponse.message)
    return new GravitinoError(errorResponse)
  }

  // 其他错误
  const message = error instanceof Error ? error.message : '未知错误'
  toast.error(message)
  return new GravitinoError({ code: -1, type: 'UnknownError', message })
}

/**
 * 导出客户端实例（供特殊场景使用）
 */
export { gravitinoClient }
