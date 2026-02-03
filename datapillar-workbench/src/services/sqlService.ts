/**
 * SQL 执行服务
 *
 * 负责 SQL 执行相关的 API 调用
 */

import { createApiClient } from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'

/**
 * SQL API 客户端
 */
const sqlClient = createApiClient({
  baseURL: '/api/workbench/sql',
  timeout: 300000
})

/**
 * 列定义
 */
export interface ColumnSchema {
  name: string
  type: string
  nullable: boolean
}

/**
 * SQL 执行请求
 */
export interface ExecuteRequest {
  sql: string
  catalog?: string
  database?: string
  maxRows?: number
}

/**
 * SQL 执行结果
 */
export interface ExecuteResult {
  success: boolean
  error?: string
  columns?: ColumnSchema[]
  rows?: unknown[][]
  rowCount: number
  hasMore: boolean
  executionTime: number
  message?: string
}

/**
 * 从错误中提取错误信息
 */
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
 * 执行 SQL
 */
export async function executeSql(request: ExecuteRequest): Promise<ExecuteResult> {
  try {
    const response = await sqlClient.post<ApiResponse<ExecuteResult>>('/execute', request)
    return response.data.data
  } catch (error) {
    return {
      success: false,
      error: extractErrorMessage(error),
      rowCount: 0,
      hasMore: false,
      executionTime: 0
    }
  }
}
