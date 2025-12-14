/**
 * One Meta (Gravitino) API 服务
 *
 * 通过 Gateway 调用 Gravitino API
 */

import axios from 'axios'
import type {
  GravitinoBaseResponse,
  GravitinoEntityListResponse,
  GravitinoCatalogResponse,
  GravitinoSchemaResponse,
  GravitinoTableResponse,
  GravitinoStatisticListResponse,
  GravitinoIndexDTO
} from '@/types/oneMeta'

/**
 * Gravitino API 客户端
 */
const gravitinoClient = axios.create({
  baseURL: '/api/onemeta',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * 从错误中提取 Gravitino 错误信息
 */
function extractErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as { response?: { data?: { message?: string } } }
    if (axiosError.response?.data?.message) {
      return axiosError.response.data.message
    }
  }
  if (error instanceof Error) {
    return error.message
  }
  return '未知错误'
}

/**
 * 检查 Gravitino 响应，code != 0 时抛出错误
 */
function checkResponse<T extends GravitinoBaseResponse>(response: T): T {
  if (response.code !== 0) {
    const msg = (response as GravitinoBaseResponse & { message?: string }).message
    throw new Error(msg || `Gravitino 错误 (code: ${response.code})`)
  }
  return response
}

/**
 * Gravitino GET 请求
 */
async function get<T extends GravitinoBaseResponse>(url: string): Promise<T> {
  try {
    const response = await gravitinoClient.get<T>(url)
    return checkResponse(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * Gravitino POST 请求
 */
async function post<T extends GravitinoBaseResponse>(url: string, data?: unknown): Promise<T> {
  try {
    const response = await gravitinoClient.post<T>(url, data)
    return checkResponse(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * Gravitino PUT 请求
 */
async function put<T extends GravitinoBaseResponse>(url: string, data?: unknown): Promise<T> {
  try {
    const response = await gravitinoClient.put<T>(url, data)
    return checkResponse(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * Gravitino DELETE 请求
 */
async function del<T extends GravitinoBaseResponse>(url: string): Promise<T> {
  try {
    const response = await gravitinoClient.delete<T>(url)
    return checkResponse(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * 前端 Catalog 类型
 */
export interface CatalogItem {
  id: string
  name: string
  icon: string
  provider: string
  comment?: string
}

/**
 * 前端 Schema 类型
 */
export interface SchemaItem {
  id: string
  name: string
  catalogId: string
  comment?: string
}

/**
 * 前端 Table 类型
 */
export interface TableItem {
  id: string
  name: string
  schemaId: string
  comment?: string
  columns?: Array<{
    name: string
    type: string
    comment?: string
  }>
  audit?: {
    creator?: string
    createTime?: string
    lastModifier?: string
    lastModifiedTime?: string
  }
  properties?: Record<string, string>
  indexes?: GravitinoIndexDTO[]
}

/**
 * Metalake 名称（硬编码，需要 URL 编码）
 */
const METALAKE_NAME = encodeURIComponent('One Meta')

/**
 * 映射 Gravitino provider 到图标名称
 */
export function mapProviderToIcon(provider: string): string {
  const mapping: Record<string, string> = {
    // Hive
    hive: 'hive',

    // Lakehouse
    'lakehouse-iceberg': 'iceberg',
    'lakehouse-hudi': 'hadoop',
    'lakehouse-paimon': 'database',

    // JDBC
    'jdbc-mysql': 'mysql',
    'jdbc-postgresql': 'postgresql',
    'jdbc-doris': 'clickhouse',
    'jdbc-oceanbase': 'database',
    'jdbc-starrocks': 'clickhouse',

    // Messaging
    kafka: 'kafka',

    // Other
    fileset: 'folder',
    model: 'model',
    metric: 'metric'
  }
  return mapping[provider.toLowerCase()] || 'database'
}

/**
 * 获取 Catalog 列表（包含完整信息）
 */
export async function fetchCatalogs(): Promise<CatalogItem[]> {
  const response = await get<GravitinoCatalogListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs?details=true`
  )

  const catalogs = response.catalogs || []

  return catalogs.map((catalog) => ({
    id: catalog.name,
    name: catalog.name,
    icon: catalog.provider, // 直接存储 provider，前端动态计算图标
    provider: catalog.provider,
    comment: catalog.comment
  }))
}

/**
 * 获取 Schema 列表
 */
export async function fetchSchemas(catalogName: string): Promise<SchemaItem[]> {
  const response = await get<GravitinoEntityListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas`
  )

  const identifiers = response.identifiers || []

  return identifiers.map((identifier) => ({
    id: `${catalogName}.${identifier.name}`,
    name: identifier.name,
    catalogId: catalogName,
    comment: undefined
  }))
}

/**
 * 获取 Schema 详情（底层 schema 后端会自动同步）
 */
export async function getSchema(catalogName: string, schemaName: string): Promise<SchemaItem> {
  const response = await get<GravitinoSchemaResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}`
  )

  const schema = response.schema
  return {
    id: `${catalogName}.${schema.name}`,
    name: schema.name,
    catalogId: catalogName,
    comment: schema.comment
  }
}

/**
 * 获取 Table 列表
 */
export async function fetchTables(catalogName: string, schemaName: string): Promise<TableItem[]> {
  const response = await get<GravitinoEntityListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables`
  )

  const identifiers = response.identifiers || []

  return identifiers.map((identifier) => ({
    id: `${catalogName}.${schemaName}.${identifier.name}`,
    name: identifier.name,
    schemaId: `${catalogName}.${schemaName}`,
    comment: undefined,
    columns: []
  }))
}

/**
 * 获取 Table 详情（后端会自动将表列同步到 gravitino）
 */
export async function getTable(catalogName: string, schemaName: string, tableName: string): Promise<TableItem> {
  const response = await get<GravitinoTableResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables/${encodeURIComponent(tableName)}`
  )

  const table = response.table
  return {
    id: `${catalogName}.${schemaName}.${table.name}`,
    name: table.name,
    schemaId: `${catalogName}.${schemaName}`,
    comment: table.comment,
    columns: table.columns?.map((col) => ({
      name: col.name,
      type: col.type,
      comment: col.comment
    })),
    audit: table.audit,
    properties: table.properties,
    indexes: table.indexes
  }
}

// ============================================
// Catalog 创建、更新、删除
// ============================================

/**
 * 创建 Catalog 请求参数
 */
export interface CreateCatalogRequest {
  name: string
  type: string
  provider: string
  comment?: string
  properties?: Record<string, string>
}

/**
 * 更新 Catalog 请求参数
 */
export interface UpdateCatalogRequest {
  comment?: string
  properties?: Record<string, string>
}

/**
 * 测试 Catalog 连接
 */
export async function testCatalogConnection(data: CreateCatalogRequest): Promise<void> {
  await post<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/testConnection`,
    data
  )
}

/**
 * 创建 Catalog
 */
export async function createCatalog(data: CreateCatalogRequest): Promise<CatalogItem> {
  const response = await post<GravitinoCatalogResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs`,
    data
  )

  const catalog = response.catalog
  return {
    id: catalog.name,
    name: catalog.name,
    icon: mapProviderToIcon(catalog.provider),
    provider: catalog.provider,
    comment: catalog.comment
  }
}

/**
 * 更新 Catalog
 */
export async function updateCatalog(catalogName: string, data: UpdateCatalogRequest): Promise<CatalogItem> {
  const response = await put<GravitinoCatalogResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}`,
    data
  )

  const catalog = response.catalog
  return {
    id: catalog.name,
    name: catalog.name,
    icon: mapProviderToIcon(catalog.provider),
    provider: catalog.provider,
    comment: catalog.comment
  }
}

/**
 * 删除 Catalog
 */
export async function deleteCatalog(catalogName: string): Promise<void> {
  await del<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}`
  )
}

// ============================================
// Schema 创建、更新、删除
// ============================================

/**
 * 创建 Schema 请求参数
 */
export interface CreateSchemaRequest {
  name: string
  comment?: string
  properties?: Record<string, string>
}

/**
 * 更新 Schema 请求参数
 */
export interface UpdateSchemaRequest {
  comment?: string
  properties?: Record<string, string>
}

/**
 * 创建 Schema
 */
export async function createSchema(catalogName: string, data: CreateSchemaRequest): Promise<SchemaItem> {
  const response = await post<GravitinoSchemaResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas`,
    data
  )

  const schema = response.schema
  return {
    id: `${catalogName}.${schema.name}`,
    name: schema.name,
    catalogId: catalogName,
    comment: schema.comment
  }
}

/**
 * 更新 Schema
 */
export async function updateSchema(
  catalogName: string,
  schemaName: string,
  data: UpdateSchemaRequest
): Promise<SchemaItem> {
  const response = await put<GravitinoSchemaResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}`,
    data
  )

  const schema = response.schema
  return {
    id: `${catalogName}.${schema.name}`,
    name: schema.name,
    catalogId: catalogName,
    comment: schema.comment
  }
}

/**
 * 删除 Schema
 */
export async function deleteSchema(catalogName: string, schemaName: string): Promise<void> {
  await del<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}`
  )
}

// ============================================
// Table 创建、更新、删除
// ============================================

/**
 * 创建 Table 请求参数
 */
export interface CreateTableRequest {
  name: string
  comment?: string
  columns: Array<{
    name: string
    type: string
    comment?: string
    nullable?: boolean
    autoIncrement?: boolean
    defaultValue?: string
  }>
  properties?: Record<string, string>
  partitioning?: Array<{
    strategy: string
    fieldName: string[]
  }>
  sortOrders?: Array<{
    sortTerm: string
    direction: 'ASC' | 'DESC'
    nullOrdering: 'NULLS_FIRST' | 'NULLS_LAST'
  }>
  distribution?: {
    strategy: string
    number: number
    expressions: string[]
  }
}

/**
 * 更新 Table 请求参数
 */
export interface UpdateTableRequest {
  comment?: string
  properties?: Record<string, string>
  updates?: Array<{
    type: 'addColumn' | 'deleteColumn' | 'renameColumn' | 'updateColumnType' | 'updateColumnComment'
    fieldName?: string[]
    newFieldName?: string
    dataType?: string
    comment?: string
    nullable?: boolean
  }>
}

/**
 * 创建 Table
 */
export async function createTable(
  catalogName: string,
  schemaName: string,
  data: CreateTableRequest
): Promise<TableItem> {
  const response = await post<GravitinoTableResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables`,
    data
  )

  const table = response.table
  return {
    id: `${catalogName}.${schemaName}.${table.name}`,
    name: table.name,
    schemaId: `${catalogName}.${schemaName}`,
    comment: table.comment,
    columns: table.columns?.map((col) => ({
      name: col.name,
      type: col.type,
      comment: col.comment
    }))
  }
}

/**
 * 更新 Table
 */
export async function updateTable(
  catalogName: string,
  schemaName: string,
  tableName: string,
  data: UpdateTableRequest
): Promise<TableItem> {
  const response = await put<GravitinoTableResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables/${encodeURIComponent(tableName)}`,
    data
  )

  const table = response.table
  return {
    id: `${catalogName}.${schemaName}.${table.name}`,
    name: table.name,
    schemaId: `${catalogName}.${schemaName}`,
    comment: table.comment,
    columns: table.columns?.map((col) => ({
      name: col.name,
      type: col.type,
      comment: col.comment
    }))
  }
}

/**
 * 删除 Table
 */
export async function deleteTable(catalogName: string, schemaName: string, tableName: string): Promise<void> {
  await del<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables/${encodeURIComponent(tableName)}`
  )
}
