/**
 * 指标表单组件专用类型
 */

import type { MetricType } from '@/services/types/onemeta/semantic'

/** 度量列 */
export interface MeasureColumn {
  id?: number
  name: string
  type: string
  comment?: string
}

/** 过滤列（含值域） */
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
  refTableId?: number
  /** 引用的 Catalog 代码（兼容历史字段） */
  refCatalog?: string
  /** 引用的 Schema 代码（兼容历史字段） */
  refSchema?: string
  /** 引用的 Table 代码（兼容历史字段） */
  refTable?: string
  /** 引用的 Catalog 名称（只读，用于显示） */
  refCatalogName?: string
  /** 引用的 Schema 名称（只读，用于显示） */
  refSchemaName?: string
  /** 引用的 Table 名称（只读，用于显示） */
  refTableName?: string
  /** 复合指标引用的指标列表 */
  compositeMetrics?: Array<{ code: string; name: string; comment?: string }>
}
