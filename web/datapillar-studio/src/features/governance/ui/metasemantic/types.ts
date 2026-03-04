/**
 * Semantic layer component-specific types
 */

// Re-export from uniform type file
export type { MetricType, Metric, WordRootDTO } from '@/services/types/onemeta/semantic'
export type { AIRecommendation, AITableRecommendation, AIMetricRecommendation } from '@/services/types/ai/metric'

// Compatible with aliases
export type { WordRootDTO as WordRoot } from '@/services/types/onemeta/semantic'

export type SemanticCategory = 'HOME' | 'METRICS' | 'GLOSSARY' | 'STANDARDS' | 'MODELS' | 'APIS'

export type ViewMode = 'CARD' | 'LIST'

/** Subentry configuration */
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
