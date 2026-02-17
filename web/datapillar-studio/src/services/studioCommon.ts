import type { ApiResponse } from '@/types/api'

export interface StudioPageParams {
  limit?: number
  offset?: number
  maxLimit?: number
}

export interface StudioPageResult<T> {
  items: T[]
  total: number
  limit: number
  offset: number
}

export function requireApiData<T>(payload: ApiResponse<T>): T {
  if (typeof payload.data === 'undefined') {
    throw new Error('接口响应缺少 data 字段')
  }
  return payload.data
}

export function toPageResult<T>(payload: ApiResponse<T[]>): StudioPageResult<T> {
  return {
    items: payload.data ?? [],
    total: payload.total ?? 0,
    limit: payload.limit ?? 20,
    offset: payload.offset ?? 0
  }
}

export function pickDefinedParams<T extends Record<string, unknown>>(params: T): Partial<T> {
  const entries = Object.entries(params).filter(([, value]) => typeof value !== 'undefined')
  return Object.fromEntries(entries) as Partial<T>
}
