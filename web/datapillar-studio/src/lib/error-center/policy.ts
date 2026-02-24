import type { AppError, ErrorAction, ErrorDecisionOptions } from './types'

export function decideErrorAction(
  error: AppError,
  options: ErrorDecisionOptions = {}
): ErrorAction {
  const preferToast = options.preferToast ?? false
  const isServerError = typeof error.status === 'number' && error.status >= 500

  if (error.source === 'runtime' || error.source === 'router') {
    return { type: 'none' }
  }

  if (preferToast) {
    return {
      type: 'toast',
      level: isServerError ? 'error' : 'warn',
      message: error.message
    }
  }

  return { type: 'local', reason: 'handled-by-caller' }
}
