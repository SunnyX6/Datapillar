import type { AppError, ErrorAction, ErrorDecisionOptions } from './types'

const SETUP_REQUIRED_CODE = 503
const SETUP_REQUIRED_TYPE = 'REQUIRED'

function shouldRedirectToSetup(error: AppError): boolean {
  if (error.errorType === SETUP_REQUIRED_TYPE) {
    if (typeof error.code === 'number') {
      return error.code === SETUP_REQUIRED_CODE
    }
    if (typeof error.status === 'number') {
      return error.status === SETUP_REQUIRED_CODE
    }
    return true
  }
  return typeof error.code === 'number' && error.code === SETUP_REQUIRED_CODE && error.errorType === SETUP_REQUIRED_TYPE
}

function isNetworkFailure(error: AppError): boolean {
  if (error.status !== undefined) {
    return false
  }
  if (typeof error.code === 'number') {
    return false
  }
  return error.code === 'ERR_NETWORK' ||
    error.code === 'ECONNABORTED' ||
    error.source === 'runtime'
}

export function decideErrorAction(
  error: AppError,
  options: ErrorDecisionOptions = {}
): ErrorAction {
  const preferToast = options.preferToast ?? false
  const allowServerErrorRedirect = options.allowServerErrorRedirect ?? true

  if (shouldRedirectToSetup(error)) {
    return { type: 'redirect', to: '/setup', replace: true }
  }

  if (error.status === 401) {
    return { type: 'logout' }
  }

  if ((error.source === 'router' || error.source === 'runtime') && allowServerErrorRedirect) {
    return { type: 'redirect', to: '/500', replace: true }
  }

  const isServerError = typeof error.status === 'number' && error.status >= 500
  if ((isServerError || isNetworkFailure(error)) && error.isCoreRequest && allowServerErrorRedirect) {
    return { type: 'redirect', to: '/500', replace: true }
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
