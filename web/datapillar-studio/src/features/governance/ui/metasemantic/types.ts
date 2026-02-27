/**
 * 语义层组件专用类型
 */

// 从统一类型文件重新导出
export type { MetricType, Metric, WordRootDTO } from '@/services/types/onemeta/semantic'
export type { AIRecommendation, AITableRecommendation, AIMetricRecommendation } from '@/services/types/ai/metric'

// 兼容别名
export type { WordRootDTO as WordRoot } from '@/services/types/onemeta/semantic'

export type SemanticCategory = 'HOME' | 'METRICS' | 'GLOSSARY' | 'STANDARDS' | 'MODELS' | 'APIS'

export type ViewMode = 'CARD' | 'LIST'

/** 子入口配置 */
export interface SubEntry {
  id: string
  label: string
  icon: React.ElementType
  route: string
}

export interface CategoryConfig {
  id: SemanticCategory
  label: string
  icon: React.ElementType
  color: string
  description: string
  count: number
  trend?: string
  subEntries?: SubEntry[]
}

export interface DataTypeItem {
  id: string
  name: string
  label: string
  category: 'INTEGRAL' | 'FRACTION' | 'STRING' | 'DATETIME' | 'COMPLEX'
  icon: string
  description: string
  badge?: string
  hasPrecision?: boolean
  hasScale?: boolean
  maxPrecision?: number
  maxScale?: number
  hasLength?: boolean
  maxLength?: number
}
