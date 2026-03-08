/**
 * One Metadata API service
 *
 * responsiblecatalog,schema,tablePhysical layer asset management
 */

import { API_BASE,requestRaw } from '@/api'
import type { ApiError } from '@/api/types/api'
import type { GravitinoIndexDTO } from '@/services/types/onemeta/metadata'

interface StudioEnvelope<T> {
 code:number
 data?: T
 limit?: number
 offset?: number
 total?: number
 message?: string
}

interface StudioCatalogDTO {
 name:string
 type:string
 provider:string
 comment?: string
 properties?: Record<string,string>
}

interface StudioSchemaDTO {
 name:string
 comment?: string
 properties?: Record<string,string>
}

interface StudioTableColumnDTO {
 name:string
 dataType:string
 comment?: string
 nullable?: boolean
 autoIncrement?: boolean
 defaultValue?: string
}

interface StudioOwnerDTO {
 name:string
 type:string
}

interface StudioTableDTO {
 name:string
 comment?: string
 properties?: Record<string,string>
 columns?: StudioTableColumnDTO[]
 owner?: StudioOwnerDTO
 audit?: {
 creator?: string
 createTime?: string
 lastModifier?: string
 lastModifiedTime?: string
 }
}

interface StudioTagDTO {
 name:string
 comment?: string
 properties?: Record<string,string>
}

/**
 * front end Catalog Type
 */
export interface CatalogItem {
 id:string
 name:string
 icon:string
 provider:string
 comment?: string
}

/**
 * front end Schema Type
 */
export interface SchemaItem {
 id:string
 name:string
 catalogId:string
 comment?: string
}

/**
 * front end Table Type
 */
export interface TableItem {
 id:string
 name:string
 schemaId:string
 owner?: string
 comment?: string
 columns?: Array<{
 name:string
 dataType:string
 comment?: string
 }>
 audit?: {
 creator?: string
 createTime?: string
 lastModifier?: string
 lastModifiedTime?: string
 }
 properties?: Record<string,string>
 indexes?: GravitinoIndexDTO[]
}

function extractOneMetaMessage(response:StudioEnvelope<unknown>):string {
 if (typeof response.message === 'string' && response.message.trim().length > 0) {
 return response.message
 }
 return `Operation failed (code:${response.code})`
}

function ensureOneMetaSuccess<T>(response:StudioEnvelope<T>):StudioEnvelope<T> {
 if (response.code!== 0) {
 const error = new Error(extractOneMetaMessage(response)) as ApiError
 error.code = response.code
 error.response = response
 throw error
 }
 return response
}

function requireData<T>(response:StudioEnvelope<T>,context:string):T {
 if (typeof response.data === 'undefined') {
 throw new Error(`Missing response data for ${context}`)
 }
 return response.data
}

async function oneMetaGet<T>(url:string):Promise<StudioEnvelope<T>> {
 const response = await requestRaw<StudioEnvelope<T>>({
 baseURL:API_BASE.governanceMetadata,url
 })
 return ensureOneMetaSuccess(response)
}

async function oneMetaPost<T>(url:string,data?: unknown):Promise<StudioEnvelope<T>> {
 const response = await requestRaw<StudioEnvelope<T>,unknown>({
 baseURL:API_BASE.governanceMetadataAdmin,url,method:'POST',data
 })
 return ensureOneMetaSuccess(response)
}

async function oneMetaPut<T>(url:string,data?: unknown):Promise<StudioEnvelope<T>> {
 const response = await requestRaw<StudioEnvelope<T>,unknown>({
 baseURL:API_BASE.governanceMetadataAdmin,url,method:'PUT',data
 })
 return ensureOneMetaSuccess(response)
}

async function oneMetaDelete<T>(url:string):Promise<StudioEnvelope<T>> {
 const response = await requestRaw<StudioEnvelope<T>>({
 baseURL:API_BASE.governanceMetadataAdmin,url,method:'DELETE'
 })
 return ensureOneMetaSuccess(response)
}

/**
 * mapping Gravitino provider to icon name
 */
export function mapProviderToIcon(provider:string):string {
 const mapping:Record<string,string> = {
 // Hive
 hive:'hive',// Lakehouse
 'lakehouse-iceberg':'iceberg','lakehouse-hudi':'hadoop','lakehouse-paimon':'database',// JDBC
 'jdbc-mysql':'mysql','jdbc-postgresql':'postgresql','jdbc-doris':'clickhouse','jdbc-oceanbase':'database','jdbc-starrocks':'clickhouse',// Messaging
 kafka:'kafka',// Other
 fileset:'folder',model:'model',metric:'metric'
 }
 return mapping[provider.toLowerCase()] || 'database'
}

/**
 * Get Catalog list(Contains complete information)
 */
export async function fetchCatalogs():Promise<CatalogItem[]> {
 const response = await oneMetaGet<StudioCatalogDTO[]>(`/catalogs?details=true`)
 const catalogs = requireData(response,'catalog list')

 // filter out dataset(Newly added in second edition)and model(gravitino Model management)type catalog
 const filteredCatalogs = catalogs.filter((catalog) => catalog.provider!== 'dataset' && catalog.provider!== 'model')

 return filteredCatalogs.map((catalog) => ({
 id:catalog.name,name:catalog.name,icon:catalog.provider,// Direct storage provider,Front-end dynamic calculation icon
 provider:catalog.provider,comment:catalog.comment
 }))
}

/**
 * Get Schema list
 */
export async function fetchSchemas(catalogName:string):Promise<SchemaItem[]> {
 const response = await oneMetaGet<StudioSchemaDTO[]>(`/catalogs/${encodeURIComponent(catalogName)}/schemas`)
 const schemas = requireData(response,'schema list')

 return schemas.map((schema) => ({
 id:`${catalogName}.${schema.name}`,name:schema.name,catalogId:catalogName,comment:schema.comment
 }))
}

/**
 * Get Schema Details(Ground floor schema The backend will automatically sync)
 */
export async function getSchema(catalogName:string,schemaName:string):Promise<SchemaItem> {
 const response = await oneMetaGet<StudioSchemaDTO>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}`)
 const schema = requireData(response,'schema detail')
 return {
 id:`${catalogName}.${schema.name}`,name:schema.name,catalogId:catalogName,comment:schema.comment
 }
}

/**
 * Get Table list
 */
export async function fetchTables(catalogName:string,schemaName:string):Promise<TableItem[]> {
 const response = await oneMetaGet<StudioTableDTO[]>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables`)
 const tables = requireData(response,'table list')

 return tables.map((table) => ({
 id:`${catalogName}.${schemaName}.${table.name}`,name:table.name,schemaId:`${catalogName}.${schemaName}`,owner:table.owner?.name,comment:table.comment,columns:[]
 }))
}

/**
 * Get Table Details(The backend will automatically synchronize the table columns to gravitino)
 */
export async function getTable(catalogName:string,schemaName:string,tableName:string):Promise<TableItem> {
 const response = await oneMetaGet<StudioTableDTO>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables/${encodeURIComponent(tableName)}`)
 const table = requireData(response,'table detail')
 return {
 id:`${catalogName}.${schemaName}.${table.name}`,name:table.name,schemaId:`${catalogName}.${schemaName}`,owner:table.owner?.name,comment:table.comment,columns:table.columns?.map((col) => ({
 name:col.name,dataType:col.dataType,comment:col.comment
 })),audit:table.audit,properties:table.properties
 }
}

// ============================================
// Catalog create,update,Delete
// ============================================

/**
 * create Catalog Request parameters
 */
export interface CreateCatalogRequest {
 name:string
 type:string
 provider:string
 comment?: string
 properties?: Record<string,string>
}

/**
 * update Catalog Request parameters
 */
export interface UpdateCatalogRequest {
 comment?: string
 properties?: Record<string,string>
}

/**
 * test Catalog connect
 */
export async function testCatalogConnection(data:CreateCatalogRequest):Promise<void> {
 await oneMetaPost<void>(`/catalogs/testConnection`,data)
}

/**
 * create Catalog
 */
export async function createCatalog(data:CreateCatalogRequest):Promise<CatalogItem> {
 const response = await oneMetaPost<StudioCatalogDTO>(`/catalogs`,data)
 const catalog = requireData(response,'create catalog')
 return {
 id:catalog.name,name:catalog.name,icon:mapProviderToIcon(catalog.provider),provider:catalog.provider,comment:catalog.comment
 }
}

/**
 * update Catalog
 */
export async function updateCatalog(catalogName:string,data:UpdateCatalogRequest):Promise<CatalogItem> {
 const response = await oneMetaPut<StudioCatalogDTO>(`/catalogs/${encodeURIComponent(catalogName)}`,data)
 const catalog = requireData(response,'update catalog')
 return {
 id:catalog.name,name:catalog.name,icon:mapProviderToIcon(catalog.provider),provider:catalog.provider,comment:catalog.comment
 }
}

/**
 * Delete Catalog
 */
export async function deleteCatalog(catalogName:string):Promise<void> {
 await oneMetaDelete<void>(`/catalogs/${encodeURIComponent(catalogName)}`)
}

// ============================================
// Schema create,update,Delete
// ============================================

/**
 * create Schema Request parameters
 */
export interface CreateSchemaRequest {
 name:string
 comment?: string
 properties?: Record<string,string>
}

/**
 * update Schema Request parameters
 */
export interface UpdateSchemaRequest {
 comment?: string
 properties?: Record<string,string>
}

/**
 * create Schema
 */
export async function createSchema(catalogName:string,data:CreateSchemaRequest):Promise<SchemaItem> {
 const response = await oneMetaPost<StudioSchemaDTO>(`/catalogs/${encodeURIComponent(catalogName)}/schemas`,data)
 const schema = requireData(response,'create schema')
 return {
 id:`${catalogName}.${schema.name}`,name:schema.name,catalogId:catalogName,comment:schema.comment
 }
}

/**
 * update Schema
 */
export async function updateSchema(catalogName:string,schemaName:string,data:UpdateSchemaRequest):Promise<SchemaItem> {
 const response = await oneMetaPut<StudioSchemaDTO>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}`,data)
 const schema = requireData(response,'update schema')
 return {
 id:`${catalogName}.${schema.name}`,name:schema.name,catalogId:catalogName,comment:schema.comment
 }
}

/**
 * Delete Schema
 */
export async function deleteSchema(catalogName:string,schemaName:string):Promise<void> {
 await oneMetaDelete<void>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}`)
}

// ============================================
// Table create,update,Delete
// ============================================

/**
 * create Table Request parameters
 */
export interface CreateTableRequest {
 name:string
 comment?: string
 columns:Array<{
 name:string
 type:string
 comment?: string
 nullable?: boolean
 autoIncrement?: boolean
 defaultValue?: string
 }>
 properties?: Record<string,string>
 partitioning?: Array<{
 strategy:string
 fieldName:string[]
 }>
 sortOrders?: Array<{
 sortTerm:string
 direction:'ASC' | 'DESC'
 nullOrdering:'NULLS_FIRST' | 'NULLS_LAST'
 }>
 distribution?: {
 strategy:string
 number:number
 expressions:string[]
 }
}

/**
 * update Table Request parameters
 */
export interface UpdateTableRequest {
 updates:Array<{ '@type':string;[key:string]:unknown }>
}

/**
 * create Table
 */
export async function createTable(catalogName:string,schemaName:string,data:CreateTableRequest):Promise<TableItem> {
 const response = await oneMetaPost<StudioTableDTO>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables`,data)
 const table = requireData(response,'create table')
 return {
 id:`${catalogName}.${schemaName}.${table.name}`,name:table.name,schemaId:`${catalogName}.${schemaName}`,owner:table.owner?.name,comment:table.comment,columns:table.columns?.map((col) => ({
 name:col.name,dataType:col.dataType,comment:col.comment
 }))
 }
}

/**
 * update Table
 */
export async function updateTable(catalogName:string,schemaName:string,tableName:string,data:UpdateTableRequest):Promise<TableItem> {
 const response = await oneMetaPut<StudioTableDTO>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables/${encodeURIComponent(tableName)}`,data)
 const table = requireData(response,'update table')
 return {
 id:`${catalogName}.${schemaName}.${table.name}`,name:table.name,schemaId:`${catalogName}.${schemaName}`,owner:table.owner?.name,comment:table.comment,columns:table.columns?.map((col) => ({
 name:col.name,dataType:col.dataType,comment:col.comment
 }))
 }
}

/**
 * Delete Table
 */
export async function deleteTable(catalogName:string,schemaName:string,tableName:string):Promise<void> {
 await oneMetaDelete<void>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables/${encodeURIComponent(tableName)}`)
}

// ============================================
// Tag (label) management
// ============================================

/** Object type */
export type MetadataObjectType = 'CATALOG' | 'SCHEMA' | 'TABLE' | 'COLUMN'

/**
 * Get a list of tags for an object
 * @param objectType Object type (COLUMN,TABLE,SCHEMA,CATALOG)
 * @param fullName full name (Such as:catalog.schema.table.column)
 */
export async function getObjectTags(objectType:MetadataObjectType,fullName:string):Promise<string[]> {
 const response = await oneMetaGet<StudioTagDTO[]>(`/objects/${objectType}/${encodeURIComponent(fullName)}/tags`)
 const tags = requireData(response,'object tags')
 return tags.map((tag) => tag.name)
}

/**
 * Get tag details of an object
 * @param objectType Object type
 * @param fullName full name
 * @param tagName Tag name
 */
export async function getObjectTag(objectType:MetadataObjectType,fullName:string,tagName:string):Promise<StudioTagDTO> {
 const response = await oneMetaGet<StudioTagDTO>(`/objects/${objectType}/${encodeURIComponent(fullName)}/tags/${encodeURIComponent(tagName)}`)
 return requireData(response,'object tag detail')
}

/**
 * association/unlabel
 * @param objectType Object type
 * @param fullName full name
 * @param tagsToAdd List of tag names to add
 * @param tagsToRemove List of tag names to remove
 */
export async function associateObjectTags(objectType:MetadataObjectType,fullName:string,tagsToAdd:string[],tagsToRemove:string[]):Promise<string[]> {
 const response = await oneMetaPost<StudioTagDTO[]>(`/objects/${objectType}/${encodeURIComponent(fullName)}/tags`,{ tagsToAdd,tagsToRemove })
 const tags = requireData(response,'alter object tags')
 return tags.map((tag) => tag.name)
}

/**
 * Get all tags
 */
export async function fetchAllTags():Promise<Array<{ name:string;comment?: string }>> {
 const response = await oneMetaGet<StudioTagDTO[]>(`/tags?details=true`)
 return requireData(response,'tag list')
}

/**
 * Create tags
 */
export async function createTag(name:string,comment?: string,properties?: Record<string,string>):Promise<StudioTagDTO> {
 const response = await oneMetaPost<StudioTagDTO>(`/tags`,{ name,comment,properties })
 return requireData(response,'create tag')
}
