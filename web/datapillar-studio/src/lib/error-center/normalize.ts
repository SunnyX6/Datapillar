import axios, { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse, ErrorResponse } from '@/types/api'
import type { AppError, AppErrorSource } from './types'

type HeaderMap = Record<string, unknown>

interface NormalizeOptions {
  module: string
  isCoreRequest?: boolean
  requestUrl?: string
  method?: string
  route?: string
  raw?: unknown
}

function toStringValue(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim().length > 0) {
    return value
  }
  if (typeof value === 'number') {
    return String(value)
  }
  return undefined
}

function pickHeader(headers: unknown, key: string): string | undefined {
  if (!headers) {
    return undefined
  }

  if (headers instanceof Headers) {
    return toStringValue(headers.get(key))
  }

  if (headers instanceof AxiosHeaders) {
    return toStringValue(headers.get(key))
  }

  const candidate = headers as HeaderMap
  const normalizedKey = key.toLowerCase()
  for (const [headerKey, value] of Object.entries(candidate)) {
    if (headerKey.toLowerCase() === normalizedKey) {
      return toStringValue(Array.isArray(value) ? value[0] : value)
    }
  }
  return undefined
}

function extractTraceContext(headers: unknown): { requestId?: string; traceId?: string } {
  const requestId = pickHeader(headers, 'x-request-id') ?? pickHeader(headers, 'request-id')
  const traceId = pickHeader(headers, 'x-trace-id') ??
    pickHeader(headers, 'trace-id') ??
    pickHeader(headers, 'traceparent')
  return { requestId, traceId }
}

function extractMessage(payload: unknown): string | undefined {
  if (!payload || typeof payload !== 'object') {
    return undefined
  }
  const candidate = payload as { message?: unknown }
  return toStringValue(candidate.message)
}

function extractType(payload: unknown): string | undefined {
  if (!payload || typeof payload !== 'object') {
    return undefined
  }
  const candidate = payload as { type?: unknown }
  return toStringValue(candidate.type)
}

function extractContext(payload: unknown): Record<string, string> | undefined {
  if (!payload || typeof payload !== 'object') {
    return undefined
  }
  const candidate = payload as { context?: unknown }
  if (!candidate.context || typeof candidate.context !== 'object' || Array.isArray(candidate.context)) {
    return undefined
  }
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(candidate.context as Record<string, unknown>)) {
    const normalized = toStringValue(value)
    if (normalized !== undefined) {
      result[key] = normalized
    }
  }
  return Object.keys(result).length > 0 ? result : undefined
}

function extractRetryable(payload: unknown): boolean | undefined {
  if (!payload || typeof payload !== 'object') {
    return undefined
  }
  const candidate = payload as { retryable?: unknown }
  return typeof candidate.retryable === 'boolean' ? candidate.retryable : undefined
}

function buildRequestUrl(config?: InternalAxiosRequestConfig): string | undefined {
  if (!config) {
    return undefined
  }
  const baseURL = config.baseURL ?? ''
  const url = config.url ?? ''
  return `${baseURL}${url}`
}

function normalizeErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }
  if (typeof error === 'string' && error.trim().length > 0) {
    return error
  }
  return '请求失败，请稍后重试'
}

function resolveRoute(route?: string): string | undefined {
  if (route && route.trim().length > 0) {
    return route
  }
  if (typeof window === 'undefined') {
    return undefined
  }
  const pathname = window.location.pathname ?? ''
  if (!pathname) {
    return undefined
  }
  const search = window.location.search ?? ''
  return `${pathname}${search}`
}

function resolveModule(module: string, route?: string): string {
  const resolvedRoute = resolveRoute(route)
  if (!resolvedRoute) {
    return module
  }
  return `${resolvedRoute} · ${module}`
}

function buildAppError(
  source: AppErrorSource,
  message: string,
  options: NormalizeOptions
): AppError {
  return {
    source,
    module: resolveModule(options.module, options.route),
    message,
    retryable: false,
    severity: source === 'runtime' || source === 'router' ? 'fatal' : 'error',
    isCoreRequest: options.isCoreRequest ?? false,
    url: options.requestUrl,
    method: options.method,
    raw: options.raw
  }
}

export function normalizeApiPayloadError(
  payload: ApiResponse<unknown> | ErrorResponse,
  options: NormalizeOptions & { status?: number; headers?: unknown }
): AppError {
  const code = typeof payload.code === 'number' ? payload.code : undefined
  const errorType = extractType(payload)
  const context = extractContext(payload)
  const payloadRetryable = extractRetryable(payload)
  const message = extractMessage(payload) ?? (code !== undefined ? `Request failed (code: ${code})` : '请求失败')
  const { requestId: headerRequestId, traceId: headerTraceId } = extractTraceContext(options.headers)
  const payloadRequestId = toStringValue((payload as { requestId?: unknown }).requestId)
  const payloadTraceId = toStringValue((payload as { traceId?: unknown }).traceId)

  return {
    ...buildAppError('axios', message, options),
    code,
    errorType,
    status: options.status,
    retryable: payloadRetryable ?? (typeof options.status === 'number' ? options.status >= 500 : false),
    severity: typeof options.status === 'number' && options.status >= 500 ? 'error' : 'warn',
    context,
    requestId: headerRequestId ?? payloadRequestId,
    traceId: headerTraceId ?? payloadTraceId,
    raw: payload
  }
}

export function normalizeAxiosError(error: unknown, options: NormalizeOptions): AppError {
  if (!axios.isAxiosError(error)) {
    return normalizeUnknownError(error, options)
  }

  const status = error.response?.status
  const message = extractMessage(error.response?.data) ?? normalizeErrorMessage(error)
  const requestUrl = options.requestUrl ?? buildRequestUrl(error.config)
  const method = options.method ?? error.config?.method?.toUpperCase()
  const { requestId, traceId } = extractTraceContext(error.response?.headers)
  const code = toStringValue(error.code) ?? (typeof status === 'number' ? status : undefined)
  const retryable = error.code === 'ECONNABORTED' ||
    error.code === 'ERR_NETWORK' ||
    (typeof status === 'number' && status >= 500)

  return {
    ...buildAppError('axios', message, { ...options, requestUrl, method }),
    code,
    status,
    retryable,
    severity: typeof status === 'number'
      ? (status >= 500 ? 'error' : 'warn')
      : 'error',
    requestId,
    traceId,
    raw: error
  }
}

export function normalizeFetchResponseError(
  response: Response,
  options: NormalizeOptions
): AppError {
  const status = response.status
  const message = `HTTP ${status}`
  const requestId = pickHeader(response.headers, 'x-request-id') ?? pickHeader(response.headers, 'request-id')
  const traceId = pickHeader(response.headers, 'x-trace-id') ??
    pickHeader(response.headers, 'trace-id') ??
    pickHeader(response.headers, 'traceparent')

  return {
    ...buildAppError('fetch', message, options),
    status,
    code: status,
    retryable: status >= 500,
    severity: status >= 500 ? 'error' : 'warn',
    requestId,
    traceId,
    raw: response
  }
}

export function normalizeUnknownError(error: unknown, options: NormalizeOptions): AppError {
  return {
    ...buildAppError('unknown', normalizeErrorMessage(error), options),
    retryable: false,
    severity: 'error',
    raw: options.raw ?? error
  }
}

export function normalizeRuntimeError(
  error: unknown,
  options: NormalizeOptions
): AppError {
  return {
    ...buildAppError('runtime', normalizeErrorMessage(error), options),
    severity: 'fatal',
    retryable: false,
    raw: options.raw ?? error
  }
}

export function normalizeRouteError(
  error: unknown,
  options: NormalizeOptions
): AppError {
  if (error && typeof error === 'object' && 'status' in error) {
    const candidate = error as { status?: unknown; statusText?: unknown; data?: unknown }
    const status = typeof candidate.status === 'number' ? candidate.status : undefined
    const fallbackMessage = toStringValue(candidate.statusText) ?? '路由加载失败'
    const message = extractMessage(candidate.data) ?? fallbackMessage
    return {
      ...buildAppError('router', message, options),
      status,
      code: status,
      severity: 'fatal',
      retryable: status !== undefined ? status >= 500 : false,
      raw: options.raw ?? error
    }
  }

  return {
    ...buildAppError('router', normalizeErrorMessage(error), options),
    severity: 'fatal',
    retryable: false,
    raw: options.raw ?? error
  }
}
