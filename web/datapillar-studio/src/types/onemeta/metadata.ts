/**
 * One Meta (Gravitino) 服务的响应类型定义
 * 基于 Apache Gravitino REST API
 */

export interface GravitinoBaseResponse {
  code: number
}

export interface GravitinoErrorResponse extends GravitinoBaseResponse {
  type: string
  message: string
  stack?: string[]
}

export interface GravitinoNameIdentifier {
  name: string
}

export interface GravitinoEntityListResponse extends GravitinoBaseResponse {
  identifiers: GravitinoNameIdentifier[]
}

export interface GravitinoCatalogDTO {
  name: string
  type: string
  provider: string
  comment?: string
  properties?: Record<string, string>
}

export interface GravitinoCatalogListResponse extends GravitinoBaseResponse {
  catalogs: GravitinoCatalogDTO[]
}

export interface GravitinoCatalogResponse extends GravitinoBaseResponse {
  catalog: GravitinoCatalogDTO
}

export interface GravitinoSchemaDTO {
  name: string
  comment?: string
  properties?: Record<string, string>
}

export interface GravitinoSchemaListResponse extends GravitinoBaseResponse {
  schemas: GravitinoSchemaDTO[]
}

export interface GravitinoSchemaResponse extends GravitinoBaseResponse {
  schema: GravitinoSchemaDTO
}

export interface GravitinoAuditDTO {
  creator?: string
  createTime?: string
  lastModifier?: string
  lastModifiedTime?: string
}

export interface GravitinoStatisticDTO {
  name: string
  value?: unknown
  reserved: boolean
  modifiable: boolean
  audit?: GravitinoAuditDTO
}

export interface GravitinoStatisticListResponse extends GravitinoBaseResponse {
  statistics: GravitinoStatisticDTO[]
}

export interface GravitinoColumnDTO {
  name: string
  type: string
  comment?: string
  nullable?: boolean
}

export interface GravitinoIndexDTO {
  indexType: PRIMARY_KEY | UNIQUE_KEY
  name?: string
  fieldNames: string[][]
}

export interface GravitinoTableDTO {
  name: string
  comment?: string
  columns?: GravitinoColumnDTO[]
  properties?: Record<string, string>
  audit?: GravitinoAuditDTO
  indexes?: GravitinoIndexDTO[]
}

export interface GravitinoTableListResponse extends GravitinoBaseResponse {
  tables: GravitinoTableDTO[]
}

export interface GravitinoTableResponse extends GravitinoBaseResponse {
  table: GravitinoTableDTO
}
