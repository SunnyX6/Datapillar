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
  unit?: string
  unitName?: string
  unitSymbol?: string
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
  name: string
  dataType?: string
  comment?: string
  audit?: {
    creator?: string
    createTime?: string
  }
}

/** 单位 DTO - 与后端 UnitDTO 一致 */
export interface UnitDTO {
  code: string
  name: string
  symbol?: string
  comment?: string
  audit?: {
    creator?: string
    createTime?: string
  }
}

/** 修饰符 DTO - 与后端 MetricModifierDTO 一致 */
export interface MetricModifierDTO {
  code: string
  name: string
  comment?: string
  modifierType?: string
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
  wordRoots?: WordRoot[]
  modifiers?: Modifier[]
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
  wordRoots: string[]         // 词根组，从词根库选择
  aggregation: string         // 聚合函数
  modifiersSelected: string[] // 修饰符，派生指标用
  type: MetricType
  dataType: string
  unit?: string
  calculationFormula: string
  comment: string
  measureColumns?: string[]   // AI 推荐使用的度量列
  filterColumns?: string[]    // AI 推荐使用的过滤列
  warning?: string            // 语义不匹配时的警告信息
}

