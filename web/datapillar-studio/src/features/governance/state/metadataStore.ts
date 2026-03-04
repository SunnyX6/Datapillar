/**
 * metadata Store
 *
 * cache Catalog/Schema/Table List data,Avoid duplicate requests
 * Note:Only cache the basic information required for list display,Does not contain sensitive configuration(Password,Connection string etc.)
 */

import { create } from 'zustand'
import {
 fetchCatalogs,fetchSchemas,fetchTables,type CatalogItem,type SchemaItem,type TableItem
} from '@/services/oneMetaService'

interface MetadataState {
 /** Catalog list */
 catalogs:CatalogItem[]
 /** Schema list,press catalogId Index */
 schemasMap:Map<string,SchemaItem[]>
 /** Table list,press schemaId (catalog.schema) Index */
 tablesMap:Map<string,TableItem[]>
 /** Is loading */
 isLoading:boolean
 /** Has it been initialized?(First load completed) */
 isInitialized:boolean

 /** Load Catalog list */
 loadCatalogs:() => Promise<void>
 /** load specified Catalog of Schema list */
 loadSchemas:(catalogId:string) => Promise<void>
 /** load specified Schema of Table list */
 loadTables:(catalogId:string,schemaId:string) => Promise<void>
 /** Force refresh Catalog list */
 refreshCatalogs:() => Promise<void>
 /** Clear assignment Catalog of Schema cache and reload */
 refreshSchemas:(catalogId:string) => Promise<void>
 /** Clear assignment Schema of Table cache and reload */
 refreshTables:(catalogId:string,schemaId:string) => Promise<void>
 /** clear all cache(Called when logging out) */
 clear:() => void
}

export const useMetadataStore = create<MetadataState>((set,get) => ({
 catalogs:[],schemasMap:new Map(),tablesMap:new Map(),isLoading:false,isInitialized:false,loadCatalogs:async () => {
 const { catalogs,isLoading } = get()
 // Data already exists or loading,skip
 if (catalogs.length > 0 || isLoading) return

 set({ isLoading:true })
 try {
 const data = await fetchCatalogs()
 set({ catalogs:data,isInitialized:true })
 } finally {
 set({ isLoading:false })
 }
 },loadSchemas:async (catalogId:string) => {
 const { schemasMap } = get()
 // Already cached,skip
 if (schemasMap.has(catalogId)) return

 try {
 const data = await fetchSchemas(catalogId)
 set((state) => ({
 schemasMap:new Map(state.schemasMap).set(catalogId,data)
 }))
 } catch {
 // The error has been API layer processing
 }
 },loadTables:async (catalogId:string,schemaId:string) => {
 const fullSchemaId = `${catalogId}.${schemaId}`
 const { tablesMap } = get()
 // Already cached,skip
 if (tablesMap.has(fullSchemaId)) return

 try {
 const data = await fetchTables(catalogId,schemaId)
 set((state) => ({
 tablesMap:new Map(state.tablesMap).set(fullSchemaId,data)
 }))
 } catch {
 // The error has been API layer processing
 }
 },refreshCatalogs:async () => {
 set({ isLoading:true })
 try {
 const data = await fetchCatalogs()
 set({ catalogs:data })
 } finally {
 set({ isLoading:false })
 }
 },refreshSchemas:async (catalogId:string) => {
 // Clear cache first
 set((state) => {
 const newMap = new Map(state.schemasMap)
 newMap.delete(catalogId)
 return { schemasMap:newMap }
 })
 // reload
 try {
 const data = await fetchSchemas(catalogId)
 set((state) => ({
 schemasMap:new Map(state.schemasMap).set(catalogId,data)
 }))
 } catch {
 // The error has been API layer processing
 }
 },refreshTables:async (catalogId:string,schemaId:string) => {
 const fullSchemaId = `${catalogId}.${schemaId}`
 // Clear cache first
 set((state) => {
 const newMap = new Map(state.tablesMap)
 newMap.delete(fullSchemaId)
 return { tablesMap:newMap }
 })
 // reload
 try {
 const data = await fetchTables(catalogId,schemaId)
 set((state) => ({
 tablesMap:new Map(state.tablesMap).set(fullSchemaId,data)
 }))
 } catch {
 // The error has been API layer processing
 }
 },clear:() => {
 set({
 catalogs:[],schemasMap:new Map(),tablesMap:new Map(),isLoading:false,isInitialized:false
 })
 }
}))
