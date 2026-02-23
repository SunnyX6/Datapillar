import { describe, expect, it } from 'vitest'
import { decideErrorAction } from '@/lib/error-center/policy'
import type { AppError } from '@/lib/error-center'

function createError(overrides: Partial<AppError> = {}): AppError {
  return {
    source: 'axios',
    module: 'api/client',
    message: '请求失败',
    severity: 'error',
    retryable: false,
    isCoreRequest: false,
    ...overrides
  }
}

describe('error-center policy', () => {
  it('setup required 错误码时重定向到 /setup', () => {
    const action = decideErrorAction(createError({
      url: '/api/studio/setup/status',
      method: 'GET',
      status: 503,
      code: 503,
      errorType: 'REQUIRED',
      isCoreRequest: true
    }))

    expect(action).toEqual({ type: 'redirect', to: '/setup', replace: true })
  })

  it('setup 状态接口 503 非 setup-required 错误码时重定向到 /500', () => {
    const action = decideErrorAction(createError({
      url: '/api/studio/setup/status',
      method: 'GET',
      status: 503,
      code: 503,
      errorType: 'SERVICE_UNAVAILABLE',
      isCoreRequest: true
    }))

    expect(action).toEqual({ type: 'redirect', to: '/500', replace: true })
  })

  it('非 setup 接口 401 仍走 logout', () => {
    const action = decideErrorAction(createError({
      url: '/api/studio/projects',
      method: 'GET',
      status: 401,
      code: 401,
      isCoreRequest: false
    }))

    expect(action).toEqual({ type: 'logout' })
  })
})
