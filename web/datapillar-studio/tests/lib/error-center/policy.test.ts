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
  it('默认策略应返回 local，由业务层决定流程', () => {
    const action = decideErrorAction(createError({
      status: 401,
      code: 401,
      errorType: 'UNAUTHORIZED'
    }))

    expect(action).toEqual({ type: 'local', reason: 'handled-by-caller' })
  })

  it('preferToast=true 时返回 toast', () => {
    const action = decideErrorAction(createError({
      status: 503,
      code: 503,
      errorType: 'SERVICE_UNAVAILABLE',
      message: '服务不可用'
    }), {
      preferToast: true
    })

    expect(action).toEqual({ type: 'toast', level: 'error', message: '服务不可用' })
  })

  it('runtime/router 错误不由错误中心主动驱动流程', () => {
    const action = decideErrorAction(createError({
      source: 'runtime',
      severity: 'fatal'
    }))

    expect(action).toEqual({ type: 'none' })
  })
})
