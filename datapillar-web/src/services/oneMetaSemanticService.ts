/**
 * One Meta Semantic (元语义) API 服务
 *
 * 负责词根、指标、修饰符、单位、值域等语义层资产管理
 */

import {
  gravitinoGet as get,
  gravitinoPost as post,
  gravitinoPut as put,
  gravitinoDelete as del
} from '@/lib/api/gravitino'
import type { GravitinoBaseResponse } from '@/types/oneMeta'
import type { MetricType, Metric, WordRootDTO, UnitDTO, MetricModifierDTO } from '@/types/metric'

// 重新导出 API 类型供外部使用
export type { MetricType, Metric, WordRootDTO, UnitDTO, MetricModifierDTO }

/** Metalake 名称 */
export const METALAKE_NAME = 'OneMeta'

/** 语义层默认 Catalog 和 Schema */
export const SEMANTIC_CATALOG = 'OneDS'
export const SEMANTIC_SCHEMA = 'OneDS'

// ============================================
// WordRoot 词根管理
// ============================================

/** 词根列表响应 */
interface WordRootListResponse extends GravitinoBaseResponse {
  roots: WordRootDTO[]
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
  name: string
  dataType?: string
  comment?: string
}

/** 更新词根请求 */
export interface UpdateWordRootRequest {
  name: string
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

  return {
    items: response.roots || [],
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
// Metric 指标管理
// ============================================

/** 指标列表响应 */
interface MetricListResponse extends GravitinoBaseResponse {
  metrics: Metric[]
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
  parentMetricCodes?: string[]
  calculationFormula?: string
  // 原子指标数据源配置（使用 ID 引用）
  refTableId?: number
  refCatalogName?: string
  refSchemaName?: string
  refTableName?: string
  measureColumnIds?: string
  filterColumnIds?: string
}

/** 更新指标请求 */
export interface UpdateMetricRequest {
  name?: string
  comment?: string
  dataType?: string
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

  return {
    items: response.metrics || [],
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

/**
 * 更新指标
 */
export async function updateMetric(code: string, data: UpdateMetricRequest): Promise<Metric> {
  // 构建 updates 数组
  const updates: Array<{ '@type': string; newName?: string; newComment?: string; newDataType?: string }> = []

  if (data.name) {
    updates.push({ '@type': 'rename', newName: data.name })
  }
  if (data.comment !== undefined) {
    updates.push({ '@type': 'updateComment', newComment: data.comment || '' })
  }
  if (data.dataType !== undefined) {
    updates.push({ '@type': 'updateDataType', newDataType: data.dataType })
  }

  const response = await put<MetricResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/${encodeURIComponent(code)}`,
    { updates }
  )
  return response.metric
}

// ============================================
// Metric Version 指标版本管理
// ============================================

/** 指标版本详情 */
export interface MetricVersionItem {
  version: number
  name: string
  code: string
  type: MetricType
  dataType?: string
  comment?: string
  unit?: string
  unitName?: string
  unitSymbol?: string
  aggregationLogic?: string
  calculationFormula?: string
  refTableId?: number
  refCatalogName?: string
  refSchemaName?: string
  refTableName?: string
  measureColumnIds?: string
  filterColumnIds?: string
  parentMetricCodes?: string[]
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
 * 获取指标版本号列表
 */
export async function fetchMetricVersionNumbers(code: string): Promise<number[]> {
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

/** 更新指标版本请求 */
export interface AlterMetricVersionRequest {
  metricName: string
  metricCode: string
  metricType: string
  dataType?: string
  comment?: string
  unit?: string
  unitName?: string
  parentMetricCodes?: string[]
  calculationFormula?: string
  refTableId?: number
  measureColumnIds?: string
  filterColumnIds?: string
}

/**
 * 更新指标版本
 */
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

/**
 * 切换指标当前版本
 */
export async function switchMetricVersion(code: string, version: number): Promise<MetricVersionItem> {
  const response = await put<MetricVersionResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/${encodeURIComponent(code)}/switch/versions/${version}`,
    {}
  )
  return response.version
}

// ============================================
// Unit 单位管理
// ============================================

/** 单位列表响应 */
interface UnitListResponse extends GravitinoBaseResponse {
  units: UnitDTO[]
  total: number
  offset: number
  limit: number
}

/** 单位详情响应 */
interface UnitResponse extends GravitinoBaseResponse {
  unit: UnitDTO
}

/** 创建单位请求 */
export interface CreateUnitRequest {
  code: string
  name: string
  symbol?: string
  comment?: string
}

/** 更新单位请求 */
export interface UpdateUnitRequest {
  name: string
  symbol?: string
  comment?: string
}

/**
 * 获取单位列表（分页）
 */
export async function fetchUnits(offset = 0, limit = 50): Promise<{
  items: UnitDTO[]
  total: number
  offset: number
  limit: number
}> {
  const response = await get<UnitListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/units?offset=${offset}&limit=${limit}`
  )

  return {
    items: response.units || [],
    total: response.total,
    offset: response.offset,
    limit: response.limit
  }
}

/**
 * 获取单位详情
 */
export async function getUnit(code: string): Promise<UnitDTO> {
  const response = await get<UnitResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/units/${encodeURIComponent(code)}`
  )
  return response.unit
}

/**
 * 创建单位
 */
export async function createUnit(data: CreateUnitRequest): Promise<UnitDTO> {
  const response = await post<UnitResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/units`,
    data
  )
  return response.unit
}

/**
 * 删除单位
 */
export async function deleteUnit(code: string): Promise<void> {
  await del<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/units/${encodeURIComponent(code)}`
  )
}

/**
 * 更新单位
 */
export async function updateUnit(code: string, data: UpdateUnitRequest): Promise<UnitDTO> {
  const response = await put<UnitResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/units/${encodeURIComponent(code)}`,
    data
  )
  return response.unit
}

// ============================================
// MetricModifier 修饰符管理
// ============================================

/** 修饰符列表响应 */
interface ModifierListResponse extends GravitinoBaseResponse {
  modifiers: MetricModifierDTO[]
  total: number
  offset: number
  limit: number
}

/** 修饰符详情响应 */
interface ModifierResponse extends GravitinoBaseResponse {
  modifier: MetricModifierDTO
}

/** 创建修饰符请求 */
export interface CreateModifierRequest {
  code: string
  name: string
  comment?: string
  modifierType?: string
}

/** 更新修饰符请求 */
export interface UpdateModifierRequest {
  name?: string
  comment?: string
}

/**
 * 获取修饰符列表（分页）
 */
export async function fetchModifiers(offset = 0, limit = 50): Promise<{
  items: MetricModifierDTO[]
  total: number
  offset: number
  limit: number
}> {
  const response = await get<ModifierListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/modifiers?offset=${offset}&limit=${limit}`
  )

  return {
    items: response.modifiers || [],
    total: response.total,
    offset: response.offset,
    limit: response.limit
  }
}

/**
 * 获取修饰符详情
 */
export async function getModifier(code: string): Promise<MetricModifierDTO> {
  const response = await get<ModifierResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/modifiers/${encodeURIComponent(code)}`
  )
  return response.modifier
}

/**
 * 创建修饰符
 */
export async function createModifier(data: CreateModifierRequest): Promise<MetricModifierDTO> {
  const response = await post<ModifierResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/modifiers`,
    data
  )
  return response.modifier
}

/**
 * 删除修饰符
 */
export async function deleteModifier(code: string): Promise<void> {
  await del<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/modifiers/${encodeURIComponent(code)}`
  )
}

/**
 * 更新修饰符
 */
export async function updateModifier(code: string, data: UpdateModifierRequest): Promise<MetricModifierDTO> {
  const response = await put<ModifierResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/metrics/modifiers/${encodeURIComponent(code)}`,
    data
  )
  return response.modifier
}

// ============================================
// ValueDomain 值域管理
// ============================================

/** 值域类型 */
export type ValueDomainType = 'ENUM' | 'RANGE' | 'REGEX'

/** 值域级别 */
export type ValueDomainLevel = 'BUILTIN' | 'BUSINESS'

/** 值域枚举项 */
export interface ValueDomainItemDTO {
  value: string
  label?: string
}

/** 值域 DTO */
export interface ValueDomainDTO {
  domainCode: string
  domainName: string
  domainType: ValueDomainType
  domainLevel?: ValueDomainLevel
  items: ValueDomainItemDTO[]
  comment?: string
  dataType?: string
  audit?: {
    creator?: string
    createTime?: string
    lastModifier?: string
    lastModifiedTime?: string
  }
}

/** 值域列表响应 */
interface ValueDomainListResponse extends GravitinoBaseResponse {
  valueDomains: ValueDomainDTO[]
  total: number
  offset: number
  limit: number
}

/** 创建值域请求 */
export interface CreateValueDomainRequest {
  domainCode: string
  domainName: string
  domainType: ValueDomainType
  domainLevel?: ValueDomainLevel
  items: ValueDomainItemDTO[]
  comment?: string
  dataType?: string
}

/**
 * 获取值域列表（分页）
 */
export async function fetchValueDomains(offset = 0, limit = 20): Promise<{
  items: ValueDomainDTO[]
  total: number
  offset: number
  limit: number
}> {
  const response = await get<ValueDomainListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/valuedomains?offset=${offset}&limit=${limit}`
  )

  return {
    items: response.valueDomains || [],
    total: response.total,
    offset: response.offset,
    limit: response.limit
  }
}

/**
 * 根据值域编码获取该值域的枚举项列表
 * @param domainCode 值域编码，如 ORDER_STATUS
 */
export async function fetchValueDomainsByCode(domainCode: string): Promise<ValueDomainItemDTO[]> {
  const response = await get<ValueDomainListResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/valuedomains?offset=0&limit=100`
  )
  const domain = (response.valueDomains || []).find((d) => d.domainCode === domainCode)
  return domain?.items || []
}

/** 值域详情响应 */
interface ValueDomainResponse extends GravitinoBaseResponse {
  valueDomain: ValueDomainDTO
}

/**
 * 创建值域
 */
export async function createValueDomain(data: CreateValueDomainRequest): Promise<ValueDomainDTO> {
  const response = await post<ValueDomainResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/valuedomains`,
    data
  )
  return response.valueDomain
}

/**
 * 删除值域
 * @param domainCode 值域编码
 */
export async function deleteValueDomain(domainCode: string): Promise<void> {
  await del<GravitinoBaseResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/valuedomains/${encodeURIComponent(domainCode)}`
  )
}

/** 更新值域请求 */
export interface UpdateValueDomainRequest {
  domainName?: string
  domainLevel?: ValueDomainLevel
  items?: ValueDomainItemDTO[]
  comment?: string
  dataType?: string
}

/**
 * 更新值域
 * @param domainCode 值域编码
 */
export async function updateValueDomain(
  domainCode: string,
  data: UpdateValueDomainRequest
): Promise<ValueDomainDTO> {
  const response = await put<ValueDomainResponse>(
    `/metalakes/${METALAKE_NAME}/catalogs/${SEMANTIC_CATALOG}/schemas/${SEMANTIC_SCHEMA}/valuedomains/${encodeURIComponent(domainCode)}`,
    data
  )
  return response.valueDomain
}

// ============================================
// Tag 管理 API
// ============================================

/** Tag 关联请求 */
export interface TagsAssociateRequest {
  tagsToAdd?: string[]
  tagsToRemove?: string[]
}

/** Tag 列表响应 */
interface TagNamesResponse extends GravitinoBaseResponse {
  names: string[]
}

/**
 * 查询元数据对象关联的 Tag 列表
 * @param objectType 对象类型（如 VALUE_DOMAIN, METRIC_MODIFIER, COLUMN, TABLE 等）
 * @param fullName 对象全名（如 catalog.schema.domainCode）
 */
export async function getObjectTags(objectType: string, fullName: string): Promise<string[]> {
  const response = await get<TagNamesResponse>(
    `/metalakes/${METALAKE_NAME}/objects/${objectType}/${encodeURIComponent(fullName)}/tags`
  )
  return response.names || []
}

/**
 * 关联 Tag 到元数据对象
 * @param objectType 对象类型（如 VALUE_DOMAIN, METRIC_MODIFIER, COLUMN, TABLE 等）
 * @param fullName 对象全名（如 catalog.schema.domainCode）
 * @param request 关联请求（tagsToAdd/tagsToRemove）
 */
export async function associateObjectTags(
  objectType: string,
  fullName: string,
  request: TagsAssociateRequest
): Promise<string[]> {
  const response = await post<TagNamesResponse>(
    `/metalakes/${METALAKE_NAME}/objects/${objectType}/${encodeURIComponent(fullName)}/tags`,
    request
  )
  return response.names || []
}
