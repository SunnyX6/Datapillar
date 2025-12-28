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
  /** 自定义后缀 */
  customSuffix: string
  /** 词根组 */
  wordRoots: string[]
  /** 聚合函数 */
  aggregation: string
  /** 修饰符（派生指标用） */
  modifiers: string[]
  /** 基础指标 code（派生指标用） */
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
  /** 原子指标的物理表引用 */
  refCatalog?: string
  refSchema?: string
  refTable?: string
  /** 复合指标引用的指标列表 */
  compositeMetrics?: Array<{ code: string; name: string; comment?: string }>
}
