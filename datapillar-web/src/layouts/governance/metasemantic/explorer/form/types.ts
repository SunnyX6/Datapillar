/**
 * 指标表单组件专用类型
 */

import type { MetricType } from '@/types/metric'

/** 度量列 */
export interface MeasureColumn {
  name: string
  type: string
  comment?: string
}

/** 过滤列（含值域） */
export interface FilterColumn {
  name: string
  type: string
  comment?: string
  values: Array<{
    key: string
    label: string
  }>
}

/** 指标表单数据 */
export interface MetricFormData {
  name: string
  code: string
  baseCode: string
  modifiers: string[]
  type: MetricType
  dataType: string
  precision: number
  scale: number
  comment: string
  unit: string
  formula: string
  measureColumns: MeasureColumn[]
  filterColumns: FilterColumn[]
}
