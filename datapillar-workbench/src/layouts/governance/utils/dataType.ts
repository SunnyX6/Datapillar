/**
 * 数据类型工具函数
 * 用于解析和构建 Gravitino 数据类型字符串
 */

/** 数据类型值 */
export interface DataTypeValue {
  type: string
  precision?: number
  scale?: number
  length?: number
}

export const DEFAULT_LENGTH = 1
export const DEFAULT_MAX_LENGTH = 65535

const MAX_LENGTH_BY_TYPE = {
  VARCHAR: DEFAULT_MAX_LENGTH,
  FIXEDCHAR: 255,
  FIXED: DEFAULT_MAX_LENGTH
} as const

export function getMaxLengthForType(type: string): number {
  return MAX_LENGTH_BY_TYPE[type as keyof typeof MAX_LENGTH_BY_TYPE] ?? DEFAULT_MAX_LENGTH
}

/**
 * 解析 dataType 字符串为 DataTypeValue 对象
 * 例如: "DECIMAL(18,4)" -> { type: "DECIMAL", precision: 18, scale: 4 }
 */
export function parseDataTypeString(dataType: string | undefined): DataTypeValue {
  if (!dataType) return { type: '' }

  const match = dataType.match(/^(\w+)(?:\((\d+)(?:,(\d+))?\))?$/)
  if (!match) return { type: dataType }

  const [, baseType, arg1, arg2] = match

  if (baseType === 'DECIMAL' && arg1) {
    return { type: 'DECIMAL', precision: Number(arg1), scale: arg2 ? Number(arg2) : 0 }
  }
  if ((baseType === 'VARCHAR' || baseType === 'FIXEDCHAR' || baseType === 'FIXED') && arg1) {
    return { type: baseType, length: Number(arg1) }
  }
  return { type: baseType }
}

/**
 * 将 DataTypeValue 对象构建为 dataType 字符串
 * 例如: { type: "DECIMAL", precision: 18, scale: 4 } -> "DECIMAL(18,4)"
 */
export function buildDataTypeString(value: DataTypeValue): string {
  if (value.type === 'DECIMAL' && value.precision !== undefined) {
    return `DECIMAL(${value.precision},${value.scale ?? 0})`
  }
  if ((value.type === 'VARCHAR' || value.type === 'FIXEDCHAR' || value.type === 'FIXED') && value.length !== undefined) {
    return `${value.type}(${value.length})`
  }
  return value.type
}
