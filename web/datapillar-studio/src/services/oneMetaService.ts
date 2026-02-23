/**
 * One Metadata API 服务
 *
 * 负责catalog、schema、table等物理层资产管理
 */

import { API_BASE, requestRaw } from '@/lib/api'
import type { ApiError, ApiResponse } from '@/types/api'
import type {
  GravitinoBaseResponse,
  GravitinoEntityListResponse,
  GravitinoCatalogResponse,
  GravitinoSchemaResponse,
  GravitinoTableResponse,
  GravitinoIndexDTO,
  GravitinoErrorResponse
} from '@/types/onemeta/metadata'

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
    dataType: string
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

function extractOneMetaMessage(response: GravitinoBaseResponse): string {
  const candidate = response as GravitinoErrorResponse & { message?: string }
  if (typeof candidate.message === 'string' && candidate.message.trim().length > 0) {
    return candidate.message
  }
  return `操作失败 (code: ${response.code})`
}

function ensureOneMetaSuccess<T extends GravitinoBaseResponse>(response: T): T {
  if (response.code !== 0) {
    const error = new Error(extractOneMetaMessage(response)) as ApiError
    error.code = response.code
    error.response = response as unknown as ApiResponse<unknown>
    throw error
  }
  return response
}

async function oneMetaGet<T extends GravitinoBaseResponse>(url: string): Promise<T> {
  const response = await requestRaw<T>({
    baseURL: API_BASE.oneMeta,
    url
  })
  return ensureOneMetaSuccess(response)
}

async function oneMetaPost<T extends GravitinoBaseResponse>(url: string, data?: unknown): Promise<T> {
  const response = await requestRaw<T, unknown>({
    baseURL: API_BASE.oneMeta,
    url,
    method: 'POST',
    data
  })
  return ensureOneMetaSuccess(response)
}

async function oneMetaPut<T extends GravitinoBaseResponse>(url: string, data?: unknown): Promise<T> {
  const response = await requestRaw<T, unknown>({
    baseURL: API_BASE.oneMeta,
    url,
    method: 'PUT',
    data
  })
  return ensureOneMetaSuccess(response)
}

async function oneMetaDelete<T extends GravitinoBaseResponse>(url: string): Promise<T> {
  const response = await requestRaw<T>({
    baseURL: API_BASE.oneMeta,
    url,
    method: 'DELETE'
  })
  return ensureOneMetaSuccess(response)
}

/**
 * Metalake 名称（硬编码，需要 URL 编码）
 */
const METALAKE_NAME = 'OneMeta'

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
  const response = await oneMetaGet<GravitinoCatalogListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs?details=true`
  )

  const catalogs = response.catalogs || []

  // 过滤掉 dataset（二开新增）和 model（gravitino 模型管理）类型的 catalog
  const filteredCatalogs = catalogs.filter(
    (catalog) => catalog.provider !== 'dataset' && catalog.provider !== 'model'
  )

  return filteredCatalogs.map((catalog) => ({
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
  const response = await oneMetaGet<GravitinoEntityListResponse>(
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
  const response = await oneMetaGet<GravitinoSchemaResponse>(
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
  const response = await oneMetaGet<GravitinoEntityListResponse>(
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
  const response = await oneMetaGet<GravitinoTableResponse>(
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
      dataType: col.type,
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
  await oneMetaPost<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/testConnection`,
    data
  )
}

/**
 * 创建 Catalog
 */
export async function createCatalog(data: CreateCatalogRequest): Promise<CatalogItem> {
  const response = await oneMetaPost<GravitinoCatalogResponse>(
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
  const response = await oneMetaPut<GravitinoCatalogResponse>(
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
  await oneMetaDelete<GravitinoBaseResponse>(
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
  const response = await oneMetaPost<GravitinoSchemaResponse>(
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
  const response = await oneMetaPut<GravitinoSchemaResponse>(
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
  await oneMetaDelete<GravitinoBaseResponse>(
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
  updates: Array<{ '@type': string; [key: string]: unknown }>
}

/**
 * 创建 Table
 */
export async function createTable(
  catalogName: string,
  schemaName: string,
  data: CreateTableRequest
): Promise<TableItem> {
  const response = await oneMetaPost<GravitinoTableResponse>(
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
      dataType: col.type,
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
  const response = await oneMetaPut<GravitinoTableResponse>(
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
      dataType: col.type,
      comment: col.comment
    }))
  }
}

/**
 * 删除 Table
 */
export async function deleteTable(catalogName: string, schemaName: string, tableName: string): Promise<void> {
  await oneMetaDelete<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables/${encodeURIComponent(tableName)}`
  )
}

// ============================================
// Tag (标签) 管理
// ============================================

/** 标签响应 */
interface TagResponse extends GravitinoBaseResponse {
  tag: {
    name: string
    comment?: string
    properties?: Record<string, string>
  }
}

/** 标签列表响应 */
interface TagListResponse extends GravitinoBaseResponse {
  tags: Array<{
    name: string
    comment?: string
    properties?: Record<string, string>
  }>
}

/** 对象类型 */
export type MetadataObjectType = 'CATALOG' | 'SCHEMA' | 'TABLE' | 'COLUMN'

/**
 * 获取对象的标签列表
 * @param objectType 对象类型 (COLUMN, TABLE, SCHEMA, CATALOG)
 * @param fullName 完整名称 (如: catalog.schema.table.column)
 */
export async function getObjectTags(objectType: MetadataObjectType, fullName: string): Promise<string[]> {
  const response = await oneMetaGet<{ code: number; names?: string[] }>(
    `/metalakes/${METALAKE_NAME}/objects/${objectType}/${encodeURIComponent(fullName)}/tags`
  )
  return response.names || []
}

/**
 * 获取对象的标签详情
 * @param objectType 对象类型
 * @param fullName 完整名称
 * @param tagName 标签名称
 */
export async function getObjectTag(objectType: MetadataObjectType, fullName: string, tagName: string): Promise<TagResponse['tag']> {
  const response = await oneMetaGet<TagResponse>(
    `/metalakes/${METALAKE_NAME}/objects/${objectType}/${encodeURIComponent(fullName)}/tags/${encodeURIComponent(tagName)}`
  )
  return response.tag
}

/**
 * 关联/解除标签
 * @param objectType 对象类型
 * @param fullName 完整名称
 * @param tagsToAdd 要添加的标签名称列表
 * @param tagsToRemove 要移除的标签名称列表
 */
export async function associateObjectTags(
  objectType: MetadataObjectType,
  fullName: string,
  tagsToAdd: string[],
  tagsToRemove: string[]
): Promise<string[]> {
  const response = await oneMetaPost<GravitinoEntityListResponse>(
    `/metalakes/${METALAKE_NAME}/objects/${objectType}/${encodeURIComponent(fullName)}/tags`,
    { tagsToAdd, tagsToRemove }
  )
  return response.identifiers?.map((id) => id.name) || []
}

/**
 * 获取所有标签
 */
export async function fetchAllTags(): Promise<Array<{ name: string; comment?: string }>> {
  const response = await oneMetaGet<TagListResponse>(
    `/metalakes/${METALAKE_NAME}/tags?details=true`
  )
  return response.tags || []
}

/**
 * 创建标签
 */
export async function createTag(name: string, comment?: string, properties?: Record<string, string>): Promise<TagResponse['tag']> {
  const response = await oneMetaPost<TagResponse>(
    `/metalakes/${METALAKE_NAME}/tags`,
    { name, comment, properties }
  )
  return response.tag
}
