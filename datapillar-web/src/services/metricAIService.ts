/**
 * 指标 AI 治理服务
 *
 * 通过 Gateway 调用 AI 服务
 */

import { createApiClient } from '@/lib/api/client'
import type { AIFillRequest, AIFillResponse } from '@/types/metric'

export type { AIFillRequest, AIFillResponse }

const aiClient = createApiClient({
  baseURL: '/api/ai/governance/metric',
  timeout: 60000,
  validateResponse: false
})

/**
 * AI 填写指标表单
 */
export async function aiFillMetric(request: AIFillRequest): Promise<AIFillResponse> {
  const response = await aiClient.post<AIFillResponse>('/fill', request)
  return response.data
}
