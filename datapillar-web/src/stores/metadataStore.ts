/**
 * 元数据 Store
 *
 * 缓存 Catalog/Schema/Table 列表数据，避免重复请求
 * 注意：只缓存列表展示所需的基础信息，不包含敏感配置（密码、连接串等）
 */

import { create } from 'zustand'
import {
  fetchCatalogs,
  fetchSchemas,
  fetchTables,
  type CatalogItem,
  type SchemaItem,
  type TableItem
} from '@/services/oneMetaService'

interface MetadataState {
  /** Catalog 列表 */
  catalogs: CatalogItem[]
  /** Schema 列表，按 catalogId 索引 */
  schemasMap: Map<string, SchemaItem[]>
  /** Table 列表，按 schemaId (catalog.schema) 索引 */
  tablesMap: Map<string, TableItem[]>
  /** 是否正在加载 */
  isLoading: boolean
  /** 是否已初始化（首次加载完成） */
  isInitialized: boolean

  /** 加载 Catalog 列表 */
  loadCatalogs: () => Promise<void>
  /** 加载指定 Catalog 的 Schema 列表 */
  loadSchemas: (catalogId: string) => Promise<void>
  /** 加载指定 Schema 的 Table 列表 */
  loadTables: (catalogId: string, schemaId: string) => Promise<void>
  /** 强制刷新 Catalog 列表 */
  refreshCatalogs: () => Promise<void>
  /** 清除指定 Catalog 的 Schema 缓存并重新加载 */
  refreshSchemas: (catalogId: string) => Promise<void>
  /** 清除指定 Schema 的 Table 缓存并重新加载 */
  refreshTables: (catalogId: string, schemaId: string) => Promise<void>
  /** 清除所有缓存（登出时调用） */
  clear: () => void
}

export const useMetadataStore = create<MetadataState>((set, get) => ({
  catalogs: [],
  schemasMap: new Map(),
  tablesMap: new Map(),
  isLoading: false,
  isInitialized: false,

  loadCatalogs: async () => {
    const { catalogs, isLoading } = get()
    // 已有数据或正在加载，跳过
    if (catalogs.length > 0 || isLoading) return

    set({ isLoading: true })
    try {
      const data = await fetchCatalogs()
      set({ catalogs: data, isInitialized: true })
    } finally {
      set({ isLoading: false })
    }
  },

  loadSchemas: async (catalogId: string) => {
    const { schemasMap } = get()
    // 已有缓存，跳过
    if (schemasMap.has(catalogId)) return

    try {
      const data = await fetchSchemas(catalogId)
      set((state) => ({
        schemasMap: new Map(state.schemasMap).set(catalogId, data)
      }))
    } catch {
      // 错误已由 API 层处理
    }
  },

  loadTables: async (catalogId: string, schemaId: string) => {
    const fullSchemaId = `${catalogId}.${schemaId}`
    const { tablesMap } = get()
    // 已有缓存，跳过
    if (tablesMap.has(fullSchemaId)) return

    try {
      const data = await fetchTables(catalogId, schemaId)
      set((state) => ({
        tablesMap: new Map(state.tablesMap).set(fullSchemaId, data)
      }))
    } catch {
      // 错误已由 API 层处理
    }
  },

  refreshCatalogs: async () => {
    set({ isLoading: true })
    try {
      const data = await fetchCatalogs()
      set({ catalogs: data })
    } finally {
      set({ isLoading: false })
    }
  },

  refreshSchemas: async (catalogId: string) => {
    // 先清除缓存
    set((state) => {
      const newMap = new Map(state.schemasMap)
      newMap.delete(catalogId)
      return { schemasMap: newMap }
    })
    // 重新加载
    try {
      const data = await fetchSchemas(catalogId)
      set((state) => ({
        schemasMap: new Map(state.schemasMap).set(catalogId, data)
      }))
    } catch {
      // 错误已由 API 层处理
    }
  },

  refreshTables: async (catalogId: string, schemaId: string) => {
    const fullSchemaId = `${catalogId}.${schemaId}`
    // 先清除缓存
    set((state) => {
      const newMap = new Map(state.tablesMap)
      newMap.delete(fullSchemaId)
      return { tablesMap: newMap }
    })
    // 重新加载
    try {
      const data = await fetchTables(catalogId, schemaId)
      set((state) => ({
        tablesMap: new Map(state.tablesMap).set(fullSchemaId, data)
      }))
    } catch {
      // 错误已由 API 层处理
    }
  },

  clear: () => {
    set({
      catalogs: [],
      schemasMap: new Map(),
      tablesMap: new Map(),
      isLoading: false,
      isInitialized: false
    })
  }
}))
