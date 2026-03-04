/**
 * Data type utility functions
 * for parsing and building Gravitino data type string
 */

/** data type value */
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
 * parse dataType The string is DataTypeValue object
 * For example: "DECIMAL(18,4)" -> { type: "DECIMAL", precision: 18, scale: 4 }
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
 * will DataTypeValue The object is constructed as dataType string
 * For example: { type: "DECIMAL", precision: 18, scale: 4 } -> "DECIMAL(18,4)"
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
