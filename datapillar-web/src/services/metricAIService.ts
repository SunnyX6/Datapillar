/**
 * 指标 AI 治理服务
 *
 * 通过 Gateway 调用 AI 服务
 */

import axios from 'axios'
import type { AIFillRequest, AIFillResponse } from '@/types/metric'

export type { AIFillRequest, AIFillResponse }

const aiClient = axios.create({
  baseURL: '/api/ai/governance/metric',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * AI 填写指标表单
 */
export async function aiFillMetric(request: AIFillRequest): Promise<AIFillResponse> {
  const response = await aiClient.post<AIFillResponse>('/fill', request)
  return response.data
}
