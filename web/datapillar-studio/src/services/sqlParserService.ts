/**
 * SQL DDL 解析服务
 * 基于 dt-sql-parser 实现，支持 Hive、Spark、Flink、MySQL 等多种 SQL 方言
 */

import {
  MySQL,
  FlinkSQL,
  SparkSQL,
  HiveSQL,
  PostgreSQL,
  TrinoSQL
} from 'dt-sql-parser'
import { EntityContextType } from 'dt-sql-parser'
import type { ParseError } from 'dt-sql-parser'

export type SqlDialect = 'MySQL' | 'HiveSQL' | 'SparkSQL' | 'FlinkSQL' | 'PostgreSQL' | 'TrinoSQL'

export interface ParsedColumn {
  id: string
  name: string
  dataType: string
  comment: string
  nullable: boolean
  isPartition?: boolean
}

export interface ParsedProperty {
  id: string
  key: string
  value: string
}

export interface ParsedDDL {
  tableName: string
  columns: ParsedColumn[]
  properties: ParsedProperty[]
  tableComment: string
}

// Provider 到 SQL 方言的映射
const PROVIDER_DIALECT_MAP: Record<string, SqlDialect> = {
  hive: 'HiveSQL',
  'lakehouse-iceberg': 'HiveSQL',
  'lakehouse-hudi': 'HiveSQL',
  'lakehouse-paimon': 'HiveSQL',
  'jdbc-mysql': 'MySQL',
  'jdbc-doris': 'MySQL',
  'jdbc-oceanbase': 'MySQL',
  'jdbc-starrocks': 'MySQL',
  'jdbc-postgresql': 'PostgreSQL',
  spark: 'SparkSQL',
  databricks: 'SparkSQL',
  trino: 'TrinoSQL',
  flink: 'FlinkSQL'
}

export const DIALECT_LABELS: Record<SqlDialect, string> = {
  HiveSQL: 'Hive SQL',
  MySQL: 'MySQL',
  PostgreSQL: 'PostgreSQL',
  SparkSQL: 'Spark SQL',
  TrinoSQL: 'Trino SQL',
  FlinkSQL: 'Flink SQL'
}

/**
 * 根据 provider 解析对应的 SQL 方言
 */
export function resolveDialect(provider?: string): SqlDialect {
  if (!provider) return 'MySQL'
  if (PROVIDER_DIALECT_MAP[provider]) return PROVIDER_DIALECT_MAP[provider]
  const matched = Object.keys(PROVIDER_DIALECT_MAP).find((key) => provider.startsWith(key))
  return matched ? PROVIDER_DIALECT_MAP[matched] : 'MySQL'
}

/**
 * 获取对应方言的解析器实例
 */
function getParser(dialect: SqlDialect) {
  switch (dialect) {
    case 'HiveSQL':
      return new HiveSQL()
    case 'SparkSQL':
      return new SparkSQL()
    case 'FlinkSQL':
      return new FlinkSQL()
    case 'PostgreSQL':
      return new PostgreSQL()
    case 'TrinoSQL':
      return new TrinoSQL()
    case 'MySQL':
    default:
      return new MySQL()
  }
}

/**
 * 生成唯一 ID
 */
function generateId(prefix: string, index: number): string {
  return `${prefix}_${Date.now()}_${index}`
}

/**
 * 从 WordRange 提取文本
 */
function extractText(ddl: string, range: { startIndex: number; endIndex: number } | null): string {
  if (!range) return ''
  return ddl.substring(range.startIndex, range.endIndex + 1).replace(/^['"`]|['"`]$/g, '')
}

/**
 * 主解析函数 - 使用 dt-sql-parser 的 getAllEntities API
 */
export function parseDDL(ddl: string, provider?: string): ParsedDDL {
  const dialect = resolveDialect(provider)
  const parser = getParser(dialect)

  // 先验证语法，有错误直接抛出
  const errors = parser.validate(ddl)
  if (errors.length > 0) {
    const firstError = errors[0]
    throw new Error(
      `DDL 语法错误 (行 ${firstError.startLine}, 列 ${firstError.startColumn}): ${firstError.message}`
    )
  }

  const result: ParsedDDL = {
    tableName: '',
    columns: [],
    properties: [],
    tableComment: ''
  }

  // 使用 getAllEntities 提取所有实体
  const entities = parser.getAllEntities(ddl)

  if (!entities || entities.length === 0) {
    throw new Error('无法解析 DDL，请检查语句格式。')
  }

  // 检测 PARTITIONED BY 的位置（不区分大小写）
  const partitionMatch = ddl.match(/PARTITIONED\s+BY\s*\(/i)
  const partitionStartIndex = partitionMatch ? partitionMatch.index! : -1

  let colIndex = 0
  let propIndex = 0
  let currentPropKey = ''

  for (const entity of entities) {
    switch (entity.entityContextType) {
      // 表名
      case EntityContextType.TABLE_CREATE: {
        result.tableName = entity.text
        // 提取表注释
        if (entity['_comment']) {
          result.tableComment = extractText(ddl, entity['_comment'] as { startIndex: number; endIndex: number })
        }
        // 提取列（如果 columns 存在）
        const tableEntity = entity as { columns?: Array<{
          text: string
          position?: { startIndex: number }
          _colType?: { text?: string; startIndex: number; endIndex: number }
          _comment?: { text?: string; startIndex: number; endIndex: number }
        }> }
        if (tableEntity.columns) {
          tableEntity.columns.forEach((col) => {
            const colStartIndex = col.position?.startIndex ?? 0
            const isPartition = partitionStartIndex > 0 && colStartIndex > partitionStartIndex
            result.columns.push({
              id: generateId('col', colIndex++),
              name: col.text,
              dataType: col._colType?.text?.toUpperCase() || 'STRING',
              comment: col._comment?.text?.replace(/^['"`]|['"`]$/g, '') || '',
              nullable: true,
              isPartition
            })
          })
        }
        break
      }

      // 列名（独立的列实体）
      case EntityContextType.COLUMN_CREATE: {
        const colEntity = entity as {
          text: string
          position?: { startIndex: number }
          _colType?: { text?: string; startIndex: number; endIndex: number }
          _comment?: { text?: string; startIndex: number; endIndex: number }
        }
        const colStartIndex = colEntity.position?.startIndex ?? 0
        const isPartition = partitionStartIndex > 0 && colStartIndex > partitionStartIndex
        result.columns.push({
          id: generateId('col', colIndex++),
          name: colEntity.text,
          dataType: colEntity._colType?.text?.toUpperCase() || 'STRING',
          comment: colEntity._comment?.text?.replace(/^['"`]|['"`]$/g, '') || '',
          nullable: true,
          isPartition
        })
        break
      }

      // 表属性 key
      case EntityContextType.TABLE_PROPERTY_KEY:
        currentPropKey = entity.text
        break

      // 表属性 value
      case EntityContextType.TABLE_PROPERTY_VALUE:
        if (currentPropKey) {
          result.properties.push({
            id: generateId('prop', propIndex++),
            key: currentPropKey,
            value: entity.text
          })
          currentPropKey = ''
        }
        break
    }
  }

  return result
}

/**
 * 验证 DDL 语法
 */
export function validateDDL(ddl: string, provider?: string): ParseError[] {
  const dialect = resolveDialect(provider)
  const parser = getParser(dialect)
  return parser.validate(ddl)
}
