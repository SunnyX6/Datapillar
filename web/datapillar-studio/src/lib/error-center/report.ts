import type { AppError, ErrorAction } from './types'

const REPORT_DEDUP_WINDOW_MS = 10000
const lastReportMap = new Map<string, number>()

function buildFingerprint(error: AppError, action: ErrorAction): string {
  return [
    error.source,
    error.module,
    error.status ?? 'na',
    error.code ?? 'na',
    error.errorType ?? 'na',
    action.type,
    error.message
  ].join('|')
}

export function reportAppError(error: AppError, action: ErrorAction): void {
  const now = Date.now()
  const fingerprint = buildFingerprint(error, action)
  const lastReportAt = lastReportMap.get(fingerprint)
  if (typeof lastReportAt === 'number' && now - lastReportAt < REPORT_DEDUP_WINDOW_MS) {
    return
  }

  lastReportMap.set(fingerprint, now)
  console.error('[ErrorCenter]', {
    action,
    source: error.source,
    module: error.module,
    status: error.status,
    code: error.code,
    errorType: error.errorType,
    message: error.message,
    url: error.url,
    method: error.method,
    requestId: error.requestId,
    traceId: error.traceId
  })
}
