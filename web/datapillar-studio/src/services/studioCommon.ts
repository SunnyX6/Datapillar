import type { ApiResponse } from '@/types/api'

export function toPageResult<T>(payload: ApiResponse<T[]>) {
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
