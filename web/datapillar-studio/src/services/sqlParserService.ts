/**
 * SQL DDL parsing service
 * Based on dt-sql-parser realize,support Hive,Spark,Flink,MySQL etc.SQL dialect
 */

import {
 MySQL,FlinkSQL,SparkSQL,HiveSQL,PostgreSQL,TrinoSQL
} from 'dt-sql-parser'
import { EntityContextType } from 'dt-sql-parser'
import type { ParseError } from 'dt-sql-parser'

export type SqlDialect = 'MySQL' | 'HiveSQL' | 'SparkSQL' | 'FlinkSQL' | 'PostgreSQL' | 'TrinoSQL'

export interface ParsedColumn {
 id:string
 name:string
 dataType:string
 comment:string
 nullable:boolean
 isPartition?: boolean
}

export interface ParsedProperty {
 id:string
 key:string
 value:string
}

export interface ParsedDDL {
 tableName:string
 columns:ParsedColumn[]
 properties:ParsedProperty[]
 tableComment:string
}

// Provider Arrive SQL Dialect mapping
const PROVIDER_DIALECT_MAP:Record<string,SqlDialect> = {
 hive:'HiveSQL','lakehouse-iceberg':'HiveSQL','lakehouse-hudi':'HiveSQL','lakehouse-paimon':'HiveSQL','jdbc-mysql':'MySQL','jdbc-doris':'MySQL','jdbc-oceanbase':'MySQL','jdbc-starrocks':'MySQL','jdbc-postgresql':'PostgreSQL',spark:'SparkSQL',databricks:'SparkSQL',trino:'TrinoSQL',flink:'FlinkSQL'
}

export const DIALECT_LABELS:Record<SqlDialect,string> = {
 HiveSQL:'Hive SQL',MySQL:'MySQL',PostgreSQL:'PostgreSQL',SparkSQL:'Spark SQL',TrinoSQL:'Trino SQL',FlinkSQL:'Flink SQL'
}

/**
 * According to provider Analyze the corresponding SQL dialect
 */
export function resolveDialect(provider?: string):SqlDialect {
 if (!provider) return 'MySQL'
 if (PROVIDER_DIALECT_MAP[provider]) return PROVIDER_DIALECT_MAP[provider]
 const matched = Object.keys(PROVIDER_DIALECT_MAP).find((key) => provider.startsWith(key))
 return matched?PROVIDER_DIALECT_MAP[matched]:'MySQL'
}

/**
 * Get the parser instance of the corresponding dialect
 */
function getParser(dialect:SqlDialect) {
 switch (dialect) {
 case 'HiveSQL':return new HiveSQL()
 case 'SparkSQL':return new SparkSQL()
 case 'FlinkSQL':return new FlinkSQL()
 case 'PostgreSQL':return new PostgreSQL()
 case 'TrinoSQL':return new TrinoSQL()
 case 'MySQL':default:return new MySQL()
 }
}

/**
 * generate unique ID
 */
function generateId(prefix:string,index:number):string {
 return `${prefix}_${Date.now()}_${index}`
}

/**
 * from WordRange Extract text
 */
function extractText(ddl:string,range:{ startIndex:number;endIndex:number } | null):string {
 if (!range) return ''
 return ddl.substring(range.startIndex,range.endIndex + 1).replace(/^['"`]|['"`]$/g,'')
}

/**
 * Main analytical function - use dt-sql-parser of getAllEntities API
 */
export function parseDDL(ddl:string,provider?: string):ParsedDDL {
 const dialect = resolveDialect(provider)
 const parser = getParser(dialect)

 // Verify the syntax first,Throw an error directly
 const errors = parser.validate(ddl)
 if (errors.length > 0) {
 const firstError = errors[0]
 throw new Error(`DDL syntax error (OK ${firstError.startLine},Column ${firstError.startColumn}):${firstError.message}`)
 }

 const result:ParsedDDL = {
 tableName:'',columns:[],properties:[],tableComment:''
 }

 // use getAllEntities Extract all entities
 const entities = parser.getAllEntities(ddl)

 if (!entities || entities.length === 0) {
 throw new Error('Unable to parse DDL,Please check the statement format.')
 }

 // Detection PARTITIONED BY location(Not case sensitive)
 const partitionMatch = ddl.match(/PARTITIONED\s+BY\s*\(/i)
 const partitionStartIndex = partitionMatch?partitionMatch.index!: -1

 let colIndex = 0
 let propIndex = 0
 let currentPropKey = ''

 for (const entity of entities) {
 switch (entity.entityContextType) {
 // table name
 case EntityContextType.TABLE_CREATE:{
 result.tableName = entity.text
 // Extract table comments
 if (entity['_comment']) {
 result.tableComment = extractText(ddl,entity['_comment'] as { startIndex:number;endIndex:number })
 }
 // Extract columns(if columns exist)
 const tableEntity = entity as { columns?: Array<{
 text:string
 position?: { startIndex:number }
 _colType?: { text?: string;startIndex:number;endIndex:number }
 _comment?: { text?: string;startIndex:number;endIndex:number }
 }> }
 if (tableEntity.columns) {
 tableEntity.columns.forEach((col) => {
 const colStartIndex = col.position?.startIndex?? 0
 const isPartition = partitionStartIndex > 0 && colStartIndex > partitionStartIndex
 result.columns.push({
 id:generateId('col',colIndex++),name:col.text,dataType:col._colType?.text?.toUpperCase() || 'STRING',comment:col._comment?.text?.replace(/^['"`]|['"`]$/g,'') || '',nullable:true,isPartition
 })
 })
 }
 break
 }

 // List(independent column entity)
 case EntityContextType.COLUMN_CREATE:{
 const colEntity = entity as {
 text:string
 position?: { startIndex:number }
 _colType?: { text?: string;startIndex:number;endIndex:number }
 _comment?: { text?: string;startIndex:number;endIndex:number }
 }
 const colStartIndex = colEntity.position?.startIndex?? 0
 const isPartition = partitionStartIndex > 0 && colStartIndex > partitionStartIndex
 result.columns.push({
 id:generateId('col',colIndex++),name:colEntity.text,dataType:colEntity._colType?.text?.toUpperCase() || 'STRING',comment:colEntity._comment?.text?.replace(/^['"`]|['"`]$/g,'') || '',nullable:true,isPartition
 })
 break
 }

 // table properties key
 case EntityContextType.TABLE_PROPERTY_KEY:currentPropKey = entity.text
 break

 // table properties value
 case EntityContextType.TABLE_PROPERTY_VALUE:if (currentPropKey) {
 result.properties.push({
 id:generateId('prop',propIndex++),key:currentPropKey,value:entity.text
 })
 currentPropKey = ''
 }
 break
 }
 }

 return result
}

/**
 * Verify DDL Grammar
 */
export function validateDDL(ddl:string,provider?: string):ParseError[] {
 const dialect = resolveDialect(provider)
 const parser = getParser(dialect)
 return parser.validate(ddl)
}
