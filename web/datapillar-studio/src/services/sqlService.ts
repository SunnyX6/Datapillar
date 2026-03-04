/**
 * SQL execution service
 *
 * responsible SQL execution related API call
 */

import { API_BASE, API_PATH, requestData } from '@/api'

/**
 * Column definition
 */
export interface ColumnSchema {
  name: string
  type: string
  nullable: boolean
}

/**
 * SQL Execute request
 */
export interface ExecuteRequest {
  sql: string
  catalog?: string
  database?: string
  maxRows?: number
}

/**
 * SQL Execution result
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
