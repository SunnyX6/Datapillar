/**
 * Special type of indicator form component
 */

import type { MetricType } from '@/services/types/onemeta/semantic'

/** measure column */
export interface MeasureColumn {
  id?: number
  name: string
  type: string
  comment?: string
}

/** Filter columns（Contains value range） */
export interface FilterColumn {
  id?: number
  name: string
  type: string
  comment?: string
  values: Array<{
    key: string
    label: string
  }>
}

/** Metric form data */
export interface MetricFormData {
  name: string
  code: string
  /** Custom suffix */
  customSuffix: string
  /** root group */
  wordRoots: string[]
  /** aggregate function */
  aggregation: string
  /** modifier（For derived indicators） */
  modifiers: string[]
  /** Basic indicators code（For derived indicators） */
  baseCode?: string
  type: MetricType
  dataType: string
  precision: number
  scale: number
  comment: string
  unit: string
  unitName?: string
  unitSymbol?: string
  formula: string
  measureColumns: MeasureColumn[]
  filterColumns: FilterColumn[]
  /** Physical table reference for atomic indicators */
  refTableId?: number
  /** quoted Catalog code（Compatible with history fields） */
  refCatalog?: string
  /** quoted Schema code（Compatible with history fields） */
  refSchema?: string
  /** quoted Table code（Compatible with history fields） */
  refTable?: string
  /** quoted Catalog Name（read only，for display） */
  refCatalogName?: string
  /** quoted Schema Name（read only，for display） */
  refSchemaName?: string
  /** quoted Table Name（read only，for display） */
  refTableName?: string
  /** List of indicators referenced by the composite indicator */
  compositeMetrics?: Array<{ code: string; name: string; comment?: string }>
}
