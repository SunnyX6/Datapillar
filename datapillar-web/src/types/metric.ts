/**
 * 指标 API 类型定义
 */

/** 指标类型 */
export type MetricType = 'ATOMIC' | 'DERIVED' | 'COMPOSITE'

// ============================================================
// 后端 DTO 类型
// ============================================================

/** 指标 DTO - 与后端 MetricDTO 一致 */
export interface Metric {
  name: string
  code: string
  type: MetricType
  dataType?: string
  comment?: string
  properties?: Record<string, string>
  currentVersion: number
  lastVersion: number
  audit?: {
    creator?: string
    createTime?: string
    lastModifier?: string
    lastModifiedTime?: string
  }
}

/** 词根 DTO - 与后端 WordRoot 一致 */
export interface WordRootDTO {
  code: string
  nameCn: string
  nameEn: string
  dataType?: string
  comment?: string
  audit?: {
    creator?: string
    createTime?: string
  }
}

// ============================================================
// AI 服务 API 类型
// ============================================================

/** 词根（AI 服务用） */
export interface WordRoot {
  code: string
  name: string
}

/** 修饰符 */
export interface Modifier {
  code: string
  name: string
}

/** 表单可选项 */
export interface FormOptions {
  dataTypes: string[]
  units: string[]
  wordRoots: WordRoot[]
  modifiers: Modifier[]
}

/** AI Fill 上下文 */
export interface AIFillContext {
  metricType: MetricType
  payload: Record<string, unknown>
  formOptions: FormOptions
}

/** AI Fill 请求 */
export interface AIFillRequest {
  userInput: string
  context: AIFillContext
}

/** AI Fill 响应 */
export interface AIFillResponse {
  name: string
  code: string
  type: MetricType
  dataType: string
  unit?: string
  calculationFormula: string
  comment: string
}

/** AI Check 表单 */
export interface AICheckForm {
  name: string
  code: string
  type: MetricType
  dataType: string
  unit?: string
  calculationFormula: string
  comment: string
}

/** AI Check 请求 */
export interface AICheckRequest {
  form: AICheckForm
}

/** 语义问题 */
export interface SemanticIssue {
  field: string
  severity: 'error' | 'warning'
  message: string
}

/** AI Check 响应 */
export interface AICheckResponse {
  valid: boolean
  issues: SemanticIssue[]
  suggestions: Record<string, string>
}
