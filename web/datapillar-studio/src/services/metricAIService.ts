/**
 * 指标 AI 治理服务
 *
 * 通过 Gateway 调用 AI 服务
 */

import { API_BASE, API_PATH, requestData } from '@/lib/api'
import type { AIFillRequest, AIFillResponse } from '@/types/ai/metric'

export type { AIFillRequest, AIFillResponse }

/**
 * AI 填写指标表单
 */
export async function aiFillMetric(request: AIFillRequest): Promise<AIFillResponse> {
  return requestData<AIFillResponse, AIFillRequest>({
    baseURL: API_BASE.aiMetric,
    url: API_PATH.metric.fill,
    method: 'POST',
    timeout: 60000,
    data: request
  })
}
