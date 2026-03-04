import { describe, expect, it } from 'vitest'
import { decideErrorAction } from '@/api/errorCenter/policy'
import type { AppError } from '@/api/errorCenter'

function createError(overrides: Partial<AppError> = {}): AppError {
  return {
    source: 'axios',
    module: 'api/client',
    message: 'Request failed',
    severity: 'error',
    retryable: false,
    isCoreRequest: false,
    ...overrides
  }
}

describe('error-center policy', () => {
  it('The default policy should return local，The business layer determines the process', () => {
    const action = decideErrorAction(createError({
      status: 401,
      code: 401,
      errorType: 'UNAUTHORIZED'
    }))

    expect(action).toEqual({ type: 'local', reason: 'handled-by-caller' })
  })

  it('preferToast=true return when toast', () => {
    const action = decideErrorAction(createError({
      status: 503,
      code: 503,
      errorType: 'SERVICE_UNAVAILABLE',
      message: 'Service unavailable'
    }), {
      preferToast: true
    })

    expect(action).toEqual({ type: 'toast', level: 'error', message: 'Service unavailable' })
  })

  it('runtime/router Errors are not actively driven by the error center', () => {
    const action = decideErrorAction(createError({
      source: 'runtime',
      severity: 'fatal'
    }))

    expect(action).toEqual({ type: 'none' })
  })
})
