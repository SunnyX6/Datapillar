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
  GravitinoIndexDTO
} from '@/types/oneMeta'
import type { MetricType, Metric, WordRootDTO } from '@/types/metric'

// 重新导出 API 类型供外部使用
export type { MetricType, Metric, WordRootDTO }

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
  const response = await get<GravitinoCatalogListResponse>(
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

// ============================================
// WordRoot 词根管理（语义层默认使用 OneMeta catalog/schema）
// ============================================

/** 语义层默认 Catalog 和 Schema */
const SEMANTIC_CATALOG = 'OneMeta'
const SEMANTIC_SCHEMA = 'OneMeta'

/** 词根列表响应 */
interface WordRootListResponse extends GravitinoBaseResponse {
  identifiers?: Array<{ namespace: string[]; name: string }>
  total: number
  offset: number
  limit: number
}

/** 词根详情响应 */
interface WordRootResponse extends GravitinoBaseResponse {
  root: WordRootDTO
}

/** 创建词根请求 */
export interface CreateWordRootRequest {
  code: string
  nameCn: string
  nameEn: string
  dataType?: string
  comment?: string
}

/**
 * 获取词根列表（分页）
 */
export async function fetchWordRoots(offset = 0, limit = 20): Promise<{
  items: WordRootDTO[]
  total: number
  offset: number
  limit: number
}> {
  const response = await get<WordRootListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/wordroots?offset=${offset}&limit=${limit}`
  )

  // 列表接口只返回标识符，需要逐个获取详情
  const identifiers = response.identifiers || []
  const items: WordRootDTO[] = []

  for (const identifier of identifiers) {
    try {
      const detail = await getWordRoot(identifier.name)
      items.push(detail)
    } catch {
      // 获取详情失败时跳过
    }
  }

  return {
    items,
    total: response.total,
    offset: response.offset,
    limit: response.limit
  }
}

/**
 * 获取词根详情
 */
export async function getWordRoot(code: string): Promise<WordRootDTO> {
  const response = await get<WordRootResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/wordroots/${encodeURIComponent(code)}`
  )
  return response.root
}

/**
 * 创建词根
 */
export async function createWordRoot(data: CreateWordRootRequest): Promise<WordRootDTO> {
  const response = await post<WordRootResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/wordroots`,
    data
  )
  return response.root
}

/**
 * 删除词根
 */
export async function deleteWordRoot(code: string): Promise<void> {
  await del<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/wordroots/${encodeURIComponent(code)}`
  )
}

/** 更新词根请求 */
export interface UpdateWordRootRequest {
  nameCn: string
  nameEn: string
  dataType?: string
  comment?: string
}

/**
 * 更新词根
 */
export async function updateWordRoot(code: string, data: UpdateWordRootRequest): Promise<WordRootDTO> {
  const response = await put<WordRootResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/wordroots/${encodeURIComponent(code)}`,
    data
  )
  return response.root
}

// ============================================
// Metric 指标管理（语义层默认使用 OneMeta catalog/schema）
// ============================================

/** 指标列表响应 */
interface MetricListResponse extends GravitinoBaseResponse {
  identifiers?: Array<{ namespace: string[]; name: string }>
  total: number
  offset: number
  limit: number
}

/** 指标详情响应 */
interface MetricResponse extends GravitinoBaseResponse {
  metric: Metric
}

/** 注册指标请求 */
export interface RegisterMetricRequest {
  name: string
  code: string
  type: MetricType
  dataType?: string
  comment?: string
  properties?: Record<string, string>
  unit?: string
  aggregationLogic?: string
  parentMetricIds?: number[]
  calculationFormula?: string
  // 原子指标数据源配置
  refCatalogName?: string
  refSchemaName?: string
  refTableName?: string
  refColumnName?: string
}

/**
 * 获取指标列表（分页）
 */
export async function fetchMetrics(offset = 0, limit = 20): Promise<{
  items: Metric[]
  total: number
  offset: number
  limit: number
}> {
  const response = await get<MetricListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics?offset=${offset}&limit=${limit}`
  )

  // 列表接口只返回标识符，需要逐个获取详情
  const identifiers = response.identifiers || []
  const items: Metric[] = []

  for (const identifier of identifiers) {
    try {
      const detail = await getMetric(identifier.name)
      items.push(detail)
    } catch {
      // 获取详情失败时跳过
    }
  }

  return {
    items,
    total: response.total,
    offset: response.offset,
    limit: response.limit
  }
}

/**
 * 获取指标详情
 */
export async function getMetric(code: string): Promise<Metric> {
  const response = await get<MetricResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/${encodeURIComponent(code)}`
  )
  return response.metric
}

/**
 * 注册指标
 */
export async function registerMetric(data: RegisterMetricRequest): Promise<Metric> {
  const response = await post<MetricResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics`,
    data
  )
  return response.metric
}

/**
 * 删除指标
 */
export async function deleteMetric(code: string): Promise<void> {
  await del<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/${encodeURIComponent(code)}`
  )
}

/** 更新指标请求 */
export interface UpdateMetricRequest {
  name?: string
  comment?: string
}

/**
 * 更新指标
 */
export async function updateMetric(code: string, data: UpdateMetricRequest): Promise<Metric> {
  // 构建 updates 数组
  const updates: Array<{ '@type': string; newName?: string; newComment?: string }> = []

  if (data.name) {
    updates.push({ '@type': 'rename', newName: data.name })
  }
  if (data.comment !== undefined) {
    updates.push({ '@type': 'updateComment', newComment: data.comment || '' })
  }

  const response = await put<MetricResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/${encodeURIComponent(code)}`,
    { updates }
  )
  return response.metric
}

/** 指标版本详情 */
export interface MetricVersionItem {
  version: number
  name: string
  code: string
  type: MetricType
  dataType?: string
  comment?: string
  unit?: string
  aggregationLogic?: string
  calculationFormula?: string
  refCatalogName?: string
  refSchemaName?: string
  refTableName?: string
  refColumnName?: string
  parentMetricIds?: number[]
  properties?: Record<string, string>
  audit?: {
    creator?: string
    createTime?: string
    lastModifier?: string
    lastModifiedTime?: string
  }
}

/** 指标版本详情响应 */
interface MetricVersionResponse extends GravitinoBaseResponse {
  version: MetricVersionItem
}

/** 指标版本列表响应 */
interface MetricVersionListResponse extends GravitinoBaseResponse {
  versions: number[]
}

/**
 * 获取指标版本列表
 */
export async function fetchMetricVersions(code: string): Promise<number[]> {
  const response = await get<MetricVersionListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/${encodeURIComponent(code)}/versions`
  )
  return response.versions || []
}

/**
 * 获取指标版本详情
 */
export async function fetchMetricVersion(code: string, version: number): Promise<MetricVersionItem> {
  const response = await get<MetricVersionResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/${encodeURIComponent(code)}/versions/${version}`
  )
  return response.version
}

/**
 * 更新指标版本
 */
export interface AlterMetricVersionRequest {
  comment?: string
  unit?: string
  aggregationLogic?: string
  calculationFormula?: string
  parentMetricIds?: number[]
}

export async function alterMetricVersion(
  code: string,
  version: number,
  data: AlterMetricVersionRequest
): Promise<MetricVersionItem> {
  const response = await put<MetricVersionResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/${encodeURIComponent(code)}/versions/${version}`,
    data
  )
  return response.version
}
