/**
 * One Meta (Gravitino) 服务的响应类型定义
 * 基于 Apache Gravitino REST API
 */

/**
 * Gravitino 基础响应
 */
export interface GravitinoBaseResponse {
  /** HTTP 状态码 */
  code: number
}

/**
 * Gravitino 错误响应
 */
export interface GravitinoErrorResponse extends GravitinoBaseResponse {
  /** 异常类型 */
  type: string
  /** 错误消息 */
  message: string
  /** 堆栈信息（可选） */
  stack?: string[]
}

/**
 * Gravitino 实体标识符
 */
export interface GravitinoNameIdentifier {
  name: string
}

/**
 * Gravitino 实体列表响应
 */
export interface GravitinoEntityListResponse extends GravitinoBaseResponse {
  /** 实体标识符列表 */
  identifiers: GravitinoNameIdentifier[]
}

/**
 * Gravitino Catalog DTO
 */
export interface GravitinoCatalogDTO {
  name: string
  type: string
  provider: string
  comment?: string
  properties?: Record<string, string>
}

/**
 * Gravitino Catalog 列表响应
 */
export interface GravitinoCatalogListResponse extends GravitinoBaseResponse {
  catalogs: GravitinoCatalogDTO[]
}

/**
 * Gravitino Catalog 单个响应
 */
export interface GravitinoCatalogResponse extends GravitinoBaseResponse {
  catalog: GravitinoCatalogDTO
}

/**
 * Gravitino Schema DTO
 */
export interface GravitinoSchemaDTO {
  name: string
  comment?: string
  properties?: Record<string, string>
}

/**
 * Gravitino Schema 列表响应
 */
export interface GravitinoSchemaListResponse extends GravitinoBaseResponse {
  schemas: GravitinoSchemaDTO[]
}

/**
 * Gravitino Schema 单个响应
 */
export interface GravitinoSchemaResponse extends GravitinoBaseResponse {
  schema: GravitinoSchemaDTO
}

/**
 * Gravitino Audit DTO
 */
export interface GravitinoAuditDTO {
  creator?: string
  createTime?: string
  lastModifier?: string
  lastModifiedTime?: string
}

/**
 * Gravitino Statistic DTO
 */
export interface GravitinoStatisticDTO {
  name: string
  value?: unknown
  reserved: boolean
  modifiable: boolean
  audit?: GravitinoAuditDTO
}

/**
 * Gravitino Statistic 列表响应
 */
export interface GravitinoStatisticListResponse extends GravitinoBaseResponse {
  statistics: GravitinoStatisticDTO[]
}

/**
 * Gravitino Column DTO
 */
export interface GravitinoColumnDTO {
  name: string
  type: string
  comment?: string
  nullable?: boolean
}

/**
 * Gravitino Index DTO
 */
export interface GravitinoIndexDTO {
  indexType: 'PRIMARY_KEY' | 'UNIQUE_KEY'
  name?: string
  fieldNames: string[][]
}

/**
 * Gravitino Table DTO
 */
export interface GravitinoTableDTO {
  name: string
  comment?: string
  columns?: GravitinoColumnDTO[]
  properties?: Record<string, string>
  audit?: GravitinoAuditDTO
  indexes?: GravitinoIndexDTO[]
}

/**
 * Gravitino Table 列表响应
 */
export interface GravitinoTableListResponse extends GravitinoBaseResponse {
  tables: GravitinoTableDTO[]
}

/**
 * Gravitino Table 单个响应
 */
export interface GravitinoTableResponse extends GravitinoBaseResponse {
  table: GravitinoTableDTO
}

/**
 * 提取 Gravitino 错误信息
 */
export function extractGravitinoError(error: unknown): string {
  // 检查是否是 axios 错误
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as { response?: { data?: unknown; status?: number }; message?: string }
    const data = axiosError.response?.data as GravitinoErrorResponse | undefined

    // 1. 使用 Gravitino 返回的错误信息
    if (data?.message) {
      return data.message
    }

    // 2. 使用 HTTP 状态码兜底
    const status = axiosError.response?.status
    if (status === 404) {
      return '请求的资源不存在'
    }
    if (status === 500) {
      return 'Gravitino 服务内部错误'
    }

    // 3. 使用 axios 错误信息
    if (axiosError.message) {
      return axiosError.message
    }
  }

  if (error instanceof Error) {
    return error.message
  }

  return '未知错误'
}
