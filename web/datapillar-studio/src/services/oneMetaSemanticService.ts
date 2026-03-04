/**
 * One Meta Semantic (Metasemantics) API service
 *
 * responsible for root,indicator,modifier,unit,Semantic layer asset management such as value ranges
 */

import { API_BASE,requestRaw } from '@/api'
import type { ApiError,ApiResponse } from '@/api/types/api'
import type { GravitinoBaseResponse } from '@/services/types/onemeta/metadata'
import type { MetricType,Metric,WordRootDTO,UnitDTO,MetricModifierDTO } from '@/services/types/onemeta/semantic'

// Re-export API Type for external use
export type { MetricType,Metric,WordRootDTO,UnitDTO,MetricModifierDTO }

function extractOneMetaMessage(response:GravitinoBaseResponse):string {
 const candidate = response as { message?: unknown }
 if (typeof candidate.message === 'string' && candidate.message.trim().length > 0) {
 return candidate.message
 }
 return `Operation failed (code:${response.code})`
}

function ensureOneMetaSuccess<T extends GravitinoBaseResponse>(response:T):T {
 if (response.code!== 0) {
 const error = new Error(extractOneMetaMessage(response)) as ApiError
 error.code = response.code
 error.response = response as unknown as ApiResponse<unknown>
 throw error
 }
 return response
}

async function oneMetaGet<T extends GravitinoBaseResponse>(url:string):Promise<T> {
 const response = await requestRaw<T>({
 baseURL:API_BASE.governanceSemantic,url
 })
 return ensureOneMetaSuccess(response)
}

async function oneMetaPost<T extends GravitinoBaseResponse>(url:string,data?: unknown):Promise<T> {
 const response = await requestRaw<T,unknown>({
 baseURL:API_BASE.governanceSemantic,url,method:'POST',data
 })
 return ensureOneMetaSuccess(response)
}

async function oneMetaPut<T extends GravitinoBaseResponse>(url:string,data?: unknown):Promise<T> {
 const response = await requestRaw<T,unknown>({
 baseURL:API_BASE.governanceSemantic,url,method:'PUT',data
 })
 return ensureOneMetaSuccess(response)
}

async function oneMetaDelete<T extends GravitinoBaseResponse>(url:string):Promise<T> {
 const response = await requestRaw<T>({
 baseURL:API_BASE.governanceSemantic,url,method:'DELETE'
 })
 return ensureOneMetaSuccess(response)
}

// ============================================
// WordRoot Stem management
// ============================================

/** root list response */
interface WordRootListResponse extends GravitinoBaseResponse {
 roots:WordRootDTO[]
 total:number
 offset:number
 limit:number
}

/** root detail response */
interface WordRootResponse extends GravitinoBaseResponse {
 root:WordRootDTO
}

/** Create root request */
export interface CreateWordRootRequest {
 code:string
 name:string
 dataType?: string
 comment?: string
}

/** Update root request */
export interface UpdateWordRootRequest {
 name:string
 dataType?: string
 comment?: string
}

/**
 * Get a list of root words(Pagination)
 */
export async function fetchWordRoots(offset = 0,limit = 20):Promise<{
 items:WordRootDTO[]
 total:number
 offset:number
 limit:number
}> {
 const response = await oneMetaGet<WordRootListResponse>(`/wordroots?offset=${offset}&limit=${limit}`)

 return {
 items:response.roots || [],total:response.total,offset:response.offset,limit:response.limit
 }
}

/**
 * Get root details
 */
export async function getWordRoot(code:string):Promise<WordRootDTO> {
 const response = await oneMetaGet<WordRootResponse>(`/wordroots/${encodeURIComponent(code)}`)
 return response.root
}

/**
 * Create root words
 */
export async function createWordRoot(data:CreateWordRootRequest):Promise<WordRootDTO> {
 const response = await oneMetaPost<WordRootResponse>(`/wordroots`,data)
 return response.root
}

/**
 * Remove root word
 */
export async function deleteWordRoot(code:string):Promise<void> {
 await oneMetaDelete<GravitinoBaseResponse>(`/wordroots/${encodeURIComponent(code)}`)
}

/**
 * Update root
 */
export async function updateWordRoot(code:string,data:UpdateWordRootRequest):Promise<WordRootDTO> {
 const response = await oneMetaPut<WordRootResponse>(`/wordroots/${encodeURIComponent(code)}`,data)
 return response.root
}

// ============================================
// Metric Indicator management
// ============================================

/** Metric list response */
interface MetricListResponse extends GravitinoBaseResponse {
 metrics:Metric[]
 total:number
 offset:number
 limit:number
}

/** Indicator details response */
interface MetricResponse extends GravitinoBaseResponse {
 metric:Metric
}

/** Register a metric request */
export interface RegisterMetricRequest {
 name:string
 code:string
 type:MetricType
 dataType?: string
 comment?: string
 properties?: Record<string,string>
 unit?: string
 aggregationLogic?: string
 parentMetricCodes?: string[]
 calculationFormula?: string
 // Atomic indicator data source configuration(use ID Quote)
 refTableId?: number
 refCatalogName?: string
 refSchemaName?: string
 refTableName?: string
 measureColumnIds?: string
 filterColumnIds?: string
}

/** Update indicator request */
export interface UpdateMetricRequest {
 name?: string
 comment?: string
 dataType?: string
}

/**
 * Get a list of indicators(Pagination)
 */
export async function fetchMetrics(offset = 0,limit = 20):Promise<{
 items:Metric[]
 total:number
 offset:number
 limit:number
}> {
 const response = await oneMetaGet<MetricListResponse>(`/metrics?offset=${offset}&limit=${limit}`)

 return {
 items:response.metrics || [],total:response.total,offset:response.offset,limit:response.limit
 }
}

/**
 * Get indicator details
 */
export async function getMetric(code:string):Promise<Metric> {
 const response = await oneMetaGet<MetricResponse>(`/metrics/${encodeURIComponent(code)}`)
 return response.metric
}

/**
 * Register indicator
 */
export async function registerMetric(data:RegisterMetricRequest):Promise<Metric> {
 const response = await oneMetaPost<MetricResponse>(`/metrics`,data)
 return response.metric
}

/**
 * Delete indicator
 */
export async function deleteMetric(code:string):Promise<void> {
 await oneMetaDelete<GravitinoBaseResponse>(`/metrics/${encodeURIComponent(code)}`)
}

/**
 * Update indicator
 */
export async function updateMetric(code:string,data:UpdateMetricRequest):Promise<Metric> {
 // Build updates array
 const updates:Array<{ '@type':string;newName?: string;newComment?: string;newDataType?: string }> = []

 if (data.name) {
 updates.push({ '@type':'rename',newName:data.name })
 }
 if (data.comment!== undefined) {
 updates.push({ '@type':'updateComment',newComment:data.comment || '' })
 }
 if (data.dataType!== undefined) {
 updates.push({ '@type':'updateDataType',newDataType:data.dataType })
 }

 const response = await oneMetaPut<MetricResponse>(`/metrics/${encodeURIComponent(code)}`,{ updates })
 return response.metric
}

// ============================================
// Metric Version Indicator version management
// ============================================

/** Indicator version details */
export interface MetricVersionItem {
 version:number
 name:string
 code:string
 type:MetricType
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
 refColumnName?: string
 measureColumnIds?: string
 filterColumnIds?: string
 measureColumns?: Array<{ name:string;type:string;comment?: string }>
 filterColumns?: Array<{
 name:string
 type:string
 comment?: string
 values?: Array<{ key:string;label:string }>
 }>
 parentMetricCodes?: string[]
 properties?: Record<string,string>
 audit?: {
 creator?: string
 createTime?: string
 lastModifier?: string
 lastModifiedTime?: string
 }
}

/** Indicator version details response */
interface MetricVersionResponse extends GravitinoBaseResponse {
 version:MetricVersionItem
}

/** Indicator version list response */
interface MetricVersionListResponse extends GravitinoBaseResponse {
 versions:number[]
}

/**
 * Get a list of indicator version numbers
 */
export async function fetchMetricVersionNumbers(code:string):Promise<number[]> {
 const response = await oneMetaGet<MetricVersionListResponse>(`/metrics/${encodeURIComponent(code)}/versions`)
 return response.versions || []
}

/**
 * Get indicator version details
 */
export async function fetchMetricVersion(code:string,version:number):Promise<MetricVersionItem> {
 const response = await oneMetaGet<MetricVersionResponse>(`/metrics/${encodeURIComponent(code)}/versions/${version}`)
 return response.version
}

/** Update indicator version request */
export interface AlterMetricVersionRequest {
 metricName:string
 metricCode:string
 metricType:string
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
 * Update indicator version
 */
export async function alterMetricVersion(code:string,version:number,data:AlterMetricVersionRequest):Promise<MetricVersionItem> {
 const response = await oneMetaPut<MetricVersionResponse>(`/metrics/${encodeURIComponent(code)}/versions/${version}`,data)
 return response.version
}

/**
 * Switch indicator current version
 */
export async function switchMetricVersion(code:string,version:number):Promise<MetricVersionItem> {
 const response = await oneMetaPut<MetricVersionResponse>(`/metrics/${encodeURIComponent(code)}/switch/versions/${version}`,{})
 return response.version
}

// ============================================
// Unit Unit management
// ============================================

/** unit list response */
interface UnitListResponse extends GravitinoBaseResponse {
 units:UnitDTO[]
 total:number
 offset:number
 limit:number
}

/** Unit details response */
interface UnitResponse extends GravitinoBaseResponse {
 unit:UnitDTO
}

/** Create unit request */
export interface CreateUnitRequest {
 code:string
 name:string
 symbol?: string
 comment?: string
}

/** Update unit request */
export interface UpdateUnitRequest {
 name:string
 symbol?: string
 comment?: string
}

/**
 * Get unit list(Pagination)
 */
export async function fetchUnits(offset = 0,limit = 50):Promise<{
 items:UnitDTO[]
 total:number
 offset:number
 limit:number
}> {
 const response = await oneMetaGet<UnitListResponse>(`/units?offset=${offset}&limit=${limit}`)

 return {
 items:response.units || [],total:response.total,offset:response.offset,limit:response.limit
 }
}

/**
 * Get unit details
 */
export async function getUnit(code:string):Promise<UnitDTO> {
 const response = await oneMetaGet<UnitResponse>(`/units/${encodeURIComponent(code)}`)
 return response.unit
}

/**
 * Create an organization
 */
export async function createUnit(data:CreateUnitRequest):Promise<UnitDTO> {
 const response = await oneMetaPost<UnitResponse>(`/units`,data)
 return response.unit
}

/**
 * Delete unit
 */
export async function deleteUnit(code:string):Promise<void> {
 await oneMetaDelete<GravitinoBaseResponse>(`/units/${encodeURIComponent(code)}`)
}

/**
 * Update unit
 */
export async function updateUnit(code:string,data:UpdateUnitRequest):Promise<UnitDTO> {
 const response = await oneMetaPut<UnitResponse>(`/units/${encodeURIComponent(code)}`,data)
 return response.unit
}

// ============================================
// MetricModifier Modifier management
// ============================================

/** modifier list response */
interface ModifierListResponse extends GravitinoBaseResponse {
 modifiers:MetricModifierDTO[]
 total:number
 offset:number
 limit:number
}

/** Modifier details response */
interface ModifierResponse extends GravitinoBaseResponse {
 modifier:MetricModifierDTO
}

/** Create modifier request */
export interface CreateModifierRequest {
 code:string
 name:string
 comment?: string
 modifierType?: string
}

/** Update modifier request */
export interface UpdateModifierRequest {
 name?: string
 comment?: string
}

/**
 * Get list of modifiers(Pagination)
 */
export async function fetchModifiers(offset = 0,limit = 50):Promise<{
 items:MetricModifierDTO[]
 total:number
 offset:number
 limit:number
}> {
 const response = await oneMetaGet<ModifierListResponse>(`/metrics/modifiers?offset=${offset}&limit=${limit}`)

 return {
 items:response.modifiers || [],total:response.total,offset:response.offset,limit:response.limit
 }
}

/**
 * Get modifier details
 */
export async function getModifier(code:string):Promise<MetricModifierDTO> {
 const response = await oneMetaGet<ModifierResponse>(`/metrics/modifiers/${encodeURIComponent(code)}`)
 return response.modifier
}

/**
 * Create modifier
 */
export async function createModifier(data:CreateModifierRequest):Promise<MetricModifierDTO> {
 const response = await oneMetaPost<ModifierResponse>(`/metrics/modifiers`,data)
 return response.modifier
}

/**
 * Remove modifier
 */
export async function deleteModifier(code:string):Promise<void> {
 await oneMetaDelete<GravitinoBaseResponse>(`/metrics/modifiers/${encodeURIComponent(code)}`)
}

/**
 * update modifier
 */
export async function updateModifier(code:string,data:UpdateModifierRequest):Promise<MetricModifierDTO> {
 const response = await oneMetaPut<ModifierResponse>(`/metrics/modifiers/${encodeURIComponent(code)}`,data)
 return response.modifier
}

// ============================================
// ValueDomain Value range management
// ============================================

/** Range type */
export type ValueDomainType = 'ENUM' | 'RANGE' | 'REGEX'

/** range level */
export type ValueDomainLevel = 'BUILTIN' | 'BUSINESS'

/** Value range enumeration items */
export interface ValueDomainItemDTO {
 value:string
 label?: string
}

/** range DTO */
export interface ValueDomainDTO {
 domainCode:string
 domainName:string
 domainType:ValueDomainType
 domainLevel?: ValueDomainLevel
 items:ValueDomainItemDTO[]
 comment?: string
 dataType?: string
 audit?: {
 creator?: string
 createTime?: string
 lastModifier?: string
 lastModifiedTime?: string
 }
}

/** Value range list response */
interface ValueDomainListResponse extends GravitinoBaseResponse {
 valueDomains:ValueDomainDTO[]
 total:number
 offset:number
 limit:number
}

/** Create range request */
export interface CreateValueDomainRequest {
 domainCode:string
 domainName:string
 domainType:ValueDomainType
 domainLevel?: ValueDomainLevel
 items:ValueDomainItemDTO[]
 comment?: string
 dataType?: string
}

/**
 * Get list of value ranges(Pagination)
 */
export async function fetchValueDomains(offset = 0,limit = 20):Promise<{
 items:ValueDomainDTO[]
 total:number
 offset:number
 limit:number
}> {
 const response = await oneMetaGet<ValueDomainListResponse>(`/valuedomains?offset=${offset}&limit=${limit}`)

 return {
 items:response.valueDomains || [],total:response.total,offset:response.offset,limit:response.limit
 }
}

/**
 * Get the enumeration item list of the value domain based on the value domain encoding
 * @param domainCode range encoding,Such as ORDER_STATUS
 */
export async function fetchValueDomainsByCode(domainCode:string):Promise<ValueDomainItemDTO[]> {
 const response = await oneMetaGet<ValueDomainListResponse>(`/valuedomains?offset=0&limit=100`)
 const domain = (response.valueDomains || []).find((d) => d.domainCode === domainCode)
 return domain?.items || []
}

/** Value range details response */
interface ValueDomainResponse extends GravitinoBaseResponse {
 valueDomain:ValueDomainDTO
}

/**
 * Create a value range
 */
export async function createValueDomain(data:CreateValueDomainRequest):Promise<ValueDomainDTO> {
 const response = await oneMetaPost<ValueDomainResponse>(`/valuedomains`,data)
 return response.valueDomain
}

/**
 * Delete range
 * @param domainCode range encoding
 */
export async function deleteValueDomain(domainCode:string):Promise<void> {
 await oneMetaDelete<GravitinoBaseResponse>(`/valuedomains/${encodeURIComponent(domainCode)}`)
}

/** Update range request */
export interface UpdateValueDomainRequest {
 domainName?: string
 domainLevel?: ValueDomainLevel
 items?: ValueDomainItemDTO[]
 comment?: string
 dataType?: string
}

/**
 * update range
 * @param domainCode range encoding
 */
export async function updateValueDomain(domainCode:string,data:UpdateValueDomainRequest):Promise<ValueDomainDTO> {
 const response = await oneMetaPut<ValueDomainResponse>(`/valuedomains/${encodeURIComponent(domainCode)}`,data)
 return response.valueDomain
}

// ============================================
// Tag management API
// ============================================

/** Tag Association request */
export interface TagsAssociateRequest {
 tagsToAdd?: string[]
 tagsToRemove?: string[]
}

/** Tag list response */
interface TagNamesResponse extends GravitinoBaseResponse {
 names:string[]
}

/**
 * Query metadata objects associated with Tag list
 * @param objectType Object type(Such as VALUE_DOMAIN,METRIC_MODIFIER,COLUMN,TABLE Wait)
 * @param fullName Object full name(Such as catalog.schema.domainCode)
 */
export async function getObjectTags(objectType:string,fullName:string):Promise<string[]> {
 const response = await oneMetaGet<TagNamesResponse>(`/objects/${objectType}/${encodeURIComponent(fullName)}/tags`)
 return response.names || []
}

/**
 * association Tag to metadata object
 * @param objectType Object type(Such as VALUE_DOMAIN,METRIC_MODIFIER,COLUMN,TABLE Wait)
 * @param fullName Object full name(Such as catalog.schema.domainCode)
 * @param request Association request(tagsToAdd/tagsToRemove)
 */
export async function associateObjectTags(objectType:string,fullName:string,request:TagsAssociateRequest):Promise<string[]> {
 const response = await oneMetaPost<TagNamesResponse>(`/objects/${objectType}/${encodeURIComponent(fullName)}/tags`,request)
 return response.names || []
}
