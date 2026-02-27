import type { AxiosInstance, Method } from 'axios'
import { createApiClient } from './client'
import type { ApiResponse } from '@/api/types/api'

interface BaseRequestOptions {
  baseURL: string
  url: string
  method?: Method
  timeout?: number
  headers?: Record<string, string>
  signal?: AbortSignal
}

interface JsonRequestOptions<TData = unknown, TParams = Record<string, unknown>> extends BaseRequestOptions {
  data?: TData
  params?: TParams
}

type SseParamValue = string | number | boolean | null | undefined

interface OpenSseOptions {
  baseURL: string
  url: string
  params?: Record<string, SseParamValue | SseParamValue[]>
  withCredentials?: boolean
}

const DEFAULT_TIMEOUT = 30000

const clientCache = new Map<string, AxiosInstance>()

function getClient(baseURL: string, timeout: number, validateResponse: boolean): AxiosInstance {
  const cacheKey = `${baseURL}::${timeout}::${validateResponse}`
  const cached = clientCache.get(cacheKey)
  if (cached) {
    return cached
  }
  const client = createApiClient({
    baseURL,
    timeout,
    validateResponse
  })
  clientCache.set(cacheKey, client)
  return client
}

function requireApiData<T>(payload: ApiResponse<T>): T {
  if (typeof payload.data === 'undefined') {
    throw new Error('接口响应缺少 data 字段')
  }
  return payload.data
}

function normalizeMethod(method?: Method): Method {
  if (!method) {
    return 'GET'
  }
  return method.toUpperCase() as Method
}

export async function requestEnvelope<T, TData = unknown, TParams = Record<string, unknown>>(
  options: JsonRequestOptions<TData, TParams>
): Promise<ApiResponse<T>> {
  const client = getClient(options.baseURL, options.timeout ?? DEFAULT_TIMEOUT, true)
  const response = await client.request<ApiResponse<T>>({
    url: options.url,
    method: normalizeMethod(options.method),
    params: options.params,
    data: options.data,
    headers: options.headers,
    signal: options.signal
  })
  return response.data
}

export async function requestData<T, TData = unknown, TParams = Record<string, unknown>>(
  options: JsonRequestOptions<TData, TParams>
): Promise<T> {
  const payload = await requestEnvelope<T, TData, TParams>(options)
  return requireApiData(payload)
}

export async function requestRaw<T, TData = unknown, TParams = Record<string, unknown>>(
  options: JsonRequestOptions<TData, TParams>
): Promise<T> {
  const client = getClient(options.baseURL, options.timeout ?? DEFAULT_TIMEOUT, false)
  const response = await client.request<T>({
    url: options.url,
    method: normalizeMethod(options.method),
    params: options.params,
    data: options.data,
    headers: options.headers,
    signal: options.signal
  })
  return response.data
}

export async function requestUploadEnvelope<T>(
  options: JsonRequestOptions<FormData>
): Promise<ApiResponse<T>> {
  return requestEnvelope<T, FormData>({
    ...options,
    headers: {
      ...options.headers
    }
  })
}

export async function requestUploadData<T>(
  options: JsonRequestOptions<FormData>
): Promise<T> {
  const payload = await requestUploadEnvelope<T>(options)
  return requireApiData(payload)
}

function appendSseParams(url: URL, params: OpenSseOptions['params']): void {
  if (!params) {
    return
  }
  for (const [key, value] of Object.entries(params)) {
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item === null || typeof item === 'undefined') {
          continue
        }
        url.searchParams.append(key, String(item))
      }
      continue
    }
    if (value === null || typeof value === 'undefined') {
      continue
    }
    url.searchParams.set(key, String(value))
  }
}

function joinUrl(baseURL: string, path: string): URL {
  const normalizedBase = baseURL.endsWith('/') ? baseURL : `${baseURL}/`
  const normalizedPath = path.startsWith('/') ? path.slice(1) : path
  return new URL(normalizedPath, window.location.origin + normalizedBase)
}

export function openSse(options: OpenSseOptions): EventSource {
  const url = joinUrl(options.baseURL, options.url)
  appendSseParams(url, options.params)
  return new EventSource(url.toString(), {
    withCredentials: options.withCredentials ?? true
  })
}
