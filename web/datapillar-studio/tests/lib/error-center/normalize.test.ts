import { describe, expect, it } from 'vitest'
import { normalizeApiPayloadError } from '@/lib/error-center'
import type { ErrorResponse } from '@/types/api'

describe('error-center normalize', () => {
  it('从响应头提取 requestId 和 traceId', () => {
    const payload: ErrorResponse = {
      code: 401,
      type: 'UNAUTHORIZED',
      message: '系统尚未完成初始化'
    }

    const error = normalizeApiPayloadError(payload, {
      module: 'api/client',
      status: 401,
      requestUrl: '/api/studio/setup/status',
      method: 'GET',
      isCoreRequest: true,
      headers: {
        'x-request-id': 'req-123',
        'x-trace-id': 'trace-456'
      }
    })

    expect(error.requestId).toBe('req-123')
    expect(error.traceId).toBe('trace-456')
  })

  it('模块字段包含来源页面与技术模块', () => {
    const payload: ErrorResponse = {
      code: 503,
      type: 'SERVICE_UNAVAILABLE',
      message: '系统初始化数据未就绪'
    }

    const error = normalizeApiPayloadError(payload, {
      module: 'api/client',
      route: '/projects',
      status: 503,
      requestUrl: '/api/studio/setup/status',
      method: 'GET',
      isCoreRequest: true
    })

    expect(error.module).toBe('/projects · api/client')
  })
})
