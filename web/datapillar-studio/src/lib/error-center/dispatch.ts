import { toast } from 'sonner'
import type { AppError, ErrorAction, FatalErrorSnapshot } from './types'
import { FATAL_ERROR_STORAGE_KEY } from './types'

function isBrowser(): boolean {
  return typeof window !== 'undefined'
}

function buildFatalSnapshot(error: AppError): FatalErrorSnapshot {
  return {
    message: error.message,
    status: error.status,
    module: error.module,
    requestId: error.requestId,
    traceId: error.traceId,
    url: error.url,
    method: error.method,
    timestamp: new Date().toISOString()
  }
}

export function persistFatalError(error: AppError): void {
  if (!isBrowser()) {
    return
  }
  const snapshot = buildFatalSnapshot(error)
  sessionStorage.setItem(FATAL_ERROR_STORAGE_KEY, JSON.stringify(snapshot))
}

export function getLastFatalError(): FatalErrorSnapshot | null {
  if (!isBrowser()) {
    return null
  }
  const rawValue = sessionStorage.getItem(FATAL_ERROR_STORAGE_KEY)
  if (!rawValue) {
    return null
  }
  try {
    const parsed = JSON.parse(rawValue) as FatalErrorSnapshot
    return parsed
  } catch {
    return null
  }
}

export function dispatchErrorAction(action: ErrorAction): void {
  if (action.type !== 'toast') {
    return
  }
  if (action.level === 'warn') {
    toast(action.message)
    return
  }
  toast.error(action.message)
}
