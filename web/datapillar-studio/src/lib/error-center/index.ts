import { dispatchErrorAction, getLastFatalError } from './dispatch'
import {
  normalizeApiPayloadError,
  normalizeAxiosError,
  normalizeFetchResponseError,
  normalizeRouteError,
  normalizeRuntimeError,
  normalizeUnknownError
} from './normalize'
import { decideErrorAction } from './policy'
import { reportAppError } from './report'
import type { AppError, HandleAppErrorOptions } from './types'

export * from './types'
export {
  getLastFatalError,
  normalizeApiPayloadError,
  normalizeAxiosError,
  normalizeFetchResponseError,
  normalizeRouteError,
  normalizeRuntimeError,
  normalizeUnknownError
}

export function handleAppError(
  error: AppError,
  options: HandleAppErrorOptions = {}
) {
  const action = decideErrorAction(error, options)
  reportAppError(error, action)
  dispatchErrorAction(action, error, options)
  return action
}
