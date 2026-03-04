/**
 * indicator AI Governance services
 *
 * Pass Gateway call AI service
 */

import { API_BASE, API_PATH, requestData } from '@/api'
import type { AIFillRequest, AIFillResponse } from '@/services/types/ai/metric'

export type { AIFillRequest, AIFillResponse }

/**
 * AI Fill out the indicator form
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
