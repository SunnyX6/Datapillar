import type { MetricType } from '@/services/types/onemeta/semantic'

export interface WordRoot {
  code: string
  name: string
}

export interface Modifier {
  code: string
  name: string
}

export interface FormOptions {
  dataTypes: string[]
  units: string[]
  wordRoots?: WordRoot[]
  modifiers?: Modifier[]
}

export interface AIFillContext {
  metricType: MetricType
  payload: Record<string, unknown>
  formOptions: FormOptions
}

export interface AIFillRequest {
  userInput: string
  context: AIFillContext
}

export interface AITableRecommendation {
  msgType: 'table'
  catalog: string
  schema: string
  name: string
  fullPath: string
  description?: string
  score: number
  columns: Array<{
    name: string
    dataType?: string
    description?: string
    score: number
  }>
}

export interface AIMetricRecommendation {
  msgType: 'metric'
  code: string
  name: string
  metricType: string
  description?: string
  score: number
}

export type AIRecommendation = AITableRecommendation | AIMetricRecommendation

export interface AIFillResponse {
  success: boolean
  message: string
  recommendations?: AIRecommendation[]
  name?: string
  wordRoots?: string[]
  aggregation?: string
  modifiersSelected?: string[]
  type?: MetricType
  dataType?: string
  unit?: string
  calculationFormula?: string
  comment?: string
  measureColumns?: string[]
  filterColumns?: string[]
}
