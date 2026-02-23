export const FATAL_ERROR_STORAGE_KEY = 'dp:last-fatal-error'

export type AppErrorSource = 'axios' | 'fetch' | 'runtime' | 'router' | 'unknown'

export type AppErrorSeverity = 'info' | 'warn' | 'error' | 'fatal'

export interface AppError {
  source: AppErrorSource
  module: string
  status?: number
  code?: number | string
  errorType?: string
  message: string
  severity: AppErrorSeverity
  context?: Record<string, string>
  requestId?: string
  traceId?: string
  url?: string
  method?: string
  retryable: boolean
  isCoreRequest: boolean
  raw?: unknown
}

export type ErrorAction =
  | { type: 'toast'; level: 'warn' | 'error'; message: string }
  | { type: 'redirect'; to: '/setup' | '/500'; replace: boolean }
  | { type: 'logout' }
  | { type: 'local'; reason: string }
  | { type: 'none' }

export interface ErrorDecisionOptions {
  preferToast?: boolean
  allowServerErrorRedirect?: boolean
}

export interface ErrorDispatchOptions {
  onUnauthorized?: (() => void) | null
}

export interface HandleAppErrorOptions extends ErrorDecisionOptions, ErrorDispatchOptions {}

export interface FatalErrorSnapshot {
  message: string
  status?: number
  module: string
  requestId?: string
  traceId?: string
  url?: string
  method?: string
  timestamp: string
}
