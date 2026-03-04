/**
 * One Metadata API service
 *
 * responsiblecatalog,schema,tablePhysical layer asset management
 */

import { API_BASE,requestRaw } from '@/api'
import type { ApiError,ApiResponse } from '@/api/types/api'
import type {
 GravitinoBaseResponse,GravitinoEntityListResponse,GravitinoCatalogListResponse,GravitinoCatalogResponse,GravitinoSchemaResponse,GravitinoTableResponse,GravitinoIndexDTO,GravitinoErrorResponse
} from '@/services/types/onemeta/metadata'

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

function extractOneMetaMessage(response:GravitinoBaseResponse):string {
 const candidate = response as GravitinoErrorResponse & { message?: string }
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
 baseURL:API_BASE.governanceMetadata,url
 })
 return ensureOneMetaSuccess(response)
}

async function oneMetaPost<T extends GravitinoBaseResponse>(url:string,data?: unknown):Promise<T> {
 const response = await requestRaw<T,unknown>({
 baseURL:API_BASE.governanceMetadata,url,method:'POST',data
 })
 return ensureOneMetaSuccess(response)
}

async function oneMetaPut<T extends GravitinoBaseResponse>(url:string,data?: unknown):Promise<T> {
 const response = await requestRaw<T,unknown>({
 baseURL:API_BASE.governanceMetadata,url,method:'PUT',data
 })
 return ensureOneMetaSuccess(response)
}

async function oneMetaDelete<T extends GravitinoBaseResponse>(url:string):Promise<T> {
 const response = await requestRaw<T>({
 baseURL:API_BASE.governanceMetadata,url,method:'DELETE'
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
 const response = await oneMetaGet<GravitinoCatalogListResponse>(`/catalogs?details=true`)

 const catalogs = response.catalogs || []

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
 const response = await oneMetaGet<GravitinoEntityListResponse>(`/catalogs/${encodeURIComponent(catalogName)}/schemas`)

 const identifiers = response.identifiers || []

 return identifiers.map((identifier) => ({
 id:`${catalogName}.${identifier.name}`,name:identifier.name,catalogId:catalogName,comment:undefined
 }))
}

/**
 * Get Schema Details(Ground floor schema The backend will automatically sync)
 */
export async function getSchema(catalogName:string,schemaName:string):Promise<SchemaItem> {
 const response = await oneMetaGet<GravitinoSchemaResponse>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}`)

 const schema = response.schema
 return {
 id:`${catalogName}.${schema.name}`,name:schema.name,catalogId:catalogName,comment:schema.comment
 }
}

/**
 * Get Table list
 */
export async function fetchTables(catalogName:string,schemaName:string):Promise<TableItem[]> {
 const response = await oneMetaGet<GravitinoEntityListResponse>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables`)

 const identifiers = response.identifiers || []

 return identifiers.map((identifier) => ({
 id:`${catalogName}.${schemaName}.${identifier.name}`,name:identifier.name,schemaId:`${catalogName}.${schemaName}`,comment:undefined,columns:[]
 }))
}

/**
 * Get Table Details(The backend will automatically synchronize the table columns to gravitino)
 */
export async function getTable(catalogName:string,schemaName:string,tableName:string):Promise<TableItem> {
 const response = await oneMetaGet<GravitinoTableResponse>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables/${encodeURIComponent(tableName)}`)

 const table = response.table
 return {
 id:`${catalogName}.${schemaName}.${table.name}`,name:table.name,schemaId:`${catalogName}.${schemaName}`,comment:table.comment,columns:table.columns?.map((col) => ({
 name:col.name,dataType:col.type,comment:col.comment
 })),audit:table.audit,properties:table.properties,indexes:table.indexes
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
 await oneMetaPost<GravitinoBaseResponse>(`/catalogs/testConnection`,data)
}

/**
 * create Catalog
 */
export async function createCatalog(data:CreateCatalogRequest):Promise<CatalogItem> {
 const response = await oneMetaPost<GravitinoCatalogResponse>(`/catalogs`,data)

 const catalog = response.catalog
 return {
 id:catalog.name,name:catalog.name,icon:mapProviderToIcon(catalog.provider),provider:catalog.provider,comment:catalog.comment
 }
}

/**
 * update Catalog
 */
export async function updateCatalog(catalogName:string,data:UpdateCatalogRequest):Promise<CatalogItem> {
 const response = await oneMetaPut<GravitinoCatalogResponse>(`/catalogs/${encodeURIComponent(catalogName)}`,data)

 const catalog = response.catalog
 return {
 id:catalog.name,name:catalog.name,icon:mapProviderToIcon(catalog.provider),provider:catalog.provider,comment:catalog.comment
 }
}

/**
 * Delete Catalog
 */
export async function deleteCatalog(catalogName:string):Promise<void> {
 await oneMetaDelete<GravitinoBaseResponse>(`/catalogs/${encodeURIComponent(catalogName)}`)
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
 const response = await oneMetaPost<GravitinoSchemaResponse>(`/catalogs/${encodeURIComponent(catalogName)}/schemas`,data)

 const schema = response.schema
 return {
 id:`${catalogName}.${schema.name}`,name:schema.name,catalogId:catalogName,comment:schema.comment
 }
}

/**
 * update Schema
 */
export async function updateSchema(catalogName:string,schemaName:string,data:UpdateSchemaRequest):Promise<SchemaItem> {
 const response = await oneMetaPut<GravitinoSchemaResponse>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}`,data)

 const schema = response.schema
 return {
 id:`${catalogName}.${schema.name}`,name:schema.name,catalogId:catalogName,comment:schema.comment
 }
}

/**
 * Delete Schema
 */
export async function deleteSchema(catalogName:string,schemaName:string):Promise<void> {
 await oneMetaDelete<GravitinoBaseResponse>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}`)
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
 const response = await oneMetaPost<GravitinoTableResponse>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables`,data)

 const table = response.table
 return {
 id:`${catalogName}.${schemaName}.${table.name}`,name:table.name,schemaId:`${catalogName}.${schemaName}`,comment:table.comment,columns:table.columns?.map((col) => ({
 name:col.name,dataType:col.type,comment:col.comment
 }))
 }
}

/**
 * update Table
 */
export async function updateTable(catalogName:string,schemaName:string,tableName:string,data:UpdateTableRequest):Promise<TableItem> {
 const response = await oneMetaPut<GravitinoTableResponse>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables/${encodeURIComponent(tableName)}`,data)

 const table = response.table
 return {
 id:`${catalogName}.${schemaName}.${table.name}`,name:table.name,schemaId:`${catalogName}.${schemaName}`,comment:table.comment,columns:table.columns?.map((col) => ({
 name:col.name,dataType:col.type,comment:col.comment
 }))
 }
}

/**
 * Delete Table
 */
export async function deleteTable(catalogName:string,schemaName:string,tableName:string):Promise<void> {
 await oneMetaDelete<GravitinoBaseResponse>(`/catalogs/${encodeURIComponent(catalogName)}/schemas/${encodeURIComponent(schemaName)}/tables/${encodeURIComponent(tableName)}`)
}

// ============================================
// Tag (label) management
// ============================================

/** tag response */
interface TagResponse extends GravitinoBaseResponse {
 tag:{
 name:string
 comment?: string
 properties?: Record<string,string>
 }
}

/** tag list response */
interface TagListResponse extends GravitinoBaseResponse {
 tags:Array<{
 name:string
 comment?: string
 properties?: Record<string,string>
 }>
}

/** Object type */
export type MetadataObjectType = 'CATALOG' | 'SCHEMA' | 'TABLE' | 'COLUMN'

/**
 * Get a list of tags for an object
 * @param objectType Object type (COLUMN,TABLE,SCHEMA,CATALOG)
 * @param fullName full name (Such as:catalog.schema.table.column)
 */
export async function getObjectTags(objectType:MetadataObjectType,fullName:string):Promise<string[]> {
 const response = await oneMetaGet<{ code:number;names?: string[] }>(`/objects/${objectType}/${encodeURIComponent(fullName)}/tags`)
 return response.names || []
}

/**
 * Get tag details of an object
 * @param objectType Object type
 * @param fullName full name
 * @param tagName Tag name
 */
export async function getObjectTag(objectType:MetadataObjectType,fullName:string,tagName:string):Promise<TagResponse['tag']> {
 const response = await oneMetaGet<TagResponse>(`/objects/${objectType}/${encodeURIComponent(fullName)}/tags/${encodeURIComponent(tagName)}`)
 return response.tag
}

/**
 * association/unlabel
 * @param objectType Object type
 * @param fullName full name
 * @param tagsToAdd List of tag names to add
 * @param tagsToRemove List of tag names to remove
 */
export async function associateObjectTags(objectType:MetadataObjectType,fullName:string,tagsToAdd:string[],tagsToRemove:string[]):Promise<string[]> {
 const response = await oneMetaPost<GravitinoEntityListResponse>(`/objects/${objectType}/${encodeURIComponent(fullName)}/tags`,{ tagsToAdd,tagsToRemove })
 return response.identifiers?.map((id) => id.name) || []
}

/**
 * Get all tags
 */
export async function fetchAllTags():Promise<Array<{ name:string;comment?: string }>> {
 const response = await oneMetaGet<TagListResponse>(`/tags?details=true`)
 return response.tags || []
}

/**
 * Create tags
 */
export async function createTag(name:string,comment?: string,properties?: Record<string,string>):Promise<TagResponse['tag']> {
 const response = await oneMetaPost<TagResponse>(`/tags`,{ name,comment,properties })
 return response.tag
}
