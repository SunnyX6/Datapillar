/**
 * SQL 执行服务
 *
 * 负责 SQL 执行相关的 API 调用
 */

import { API_BASE, API_PATH, requestData } from '@/api'

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

export async function executeSql(request: ExecuteRequest): Promise<ExecuteResult> {
  return requestData<ExecuteResult, ExecuteRequest>({
    baseURL: API_BASE.studioSql,
    url: API_PATH.sql.execute,
    method: 'POST',
    timeout: 300000,
    data: request
  })
}
