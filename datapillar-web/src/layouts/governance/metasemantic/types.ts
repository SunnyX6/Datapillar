/**
 * 语义层组件专用类型
 */

// 从统一类型文件重新导出
export type { MetricType, Metric, WordRootDTO } from '@/types/metric'

// 兼容别名
export type { WordRootDTO as WordRoot } from '@/types/metric'

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
