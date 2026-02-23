export type MetricType = 'ATOMIC' | 'DERIVED' | 'COMPOSITE'

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
