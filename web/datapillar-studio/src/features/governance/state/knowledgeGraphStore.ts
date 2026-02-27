/**
 * 知识谱图 Store
 *
 * 缓存图数据，避免重复请求
 * 注意：节点上限 500，超出后不再自动加载，需用户主动搜索
 */

import { create } from 'zustand'
import { fetchInitialGraph, searchGraph, type GraphData, type GraphLink } from '@/services/knowledgeGraphService'

const MAX_NODES = 500

interface KnowledgeGraphState {
  /** 完整图数据（合并后） */
  allGraphData: GraphData
  /** 是否正在加载 */
  isLoading: boolean
  /** 是否已初始化 */
  isInitialized: boolean

  /** 加载初始图数据 */
  loadInitialGraph: (limit?: number) => Promise<void>
  /** 搜索并合并结果 */
  searchAndMerge: (query: string, limit?: number, signal?: AbortSignal) => Promise<GraphData>
  /** 强制刷新 */
  refresh: () => Promise<void>
  /** 清除缓存 */
  clear: () => void
}

/**
 * 合并图数据（去重）
 */
function mergeGraph(base: GraphData, incoming: GraphData): GraphData {
  const nodeMap = new Map(base.nodes.map(node => [node.id, node]))
  incoming.nodes.forEach(node => {
    if (!nodeMap.has(node.id)) {
      nodeMap.set(node.id, node)
    }
  })

  const linkKey = (link: GraphLink) => `${link.source}->${link.target}:${link.type ?? ''}`
  const linkMap = new Map(base.links.map(link => [linkKey(link), link]))
  incoming.links.forEach(link => {
    const key = linkKey(link)
    if (!linkMap.has(key)) {
      linkMap.set(key, link)
    }
  })

  return {
    nodes: Array.from(nodeMap.values()),
    links: Array.from(linkMap.values())
  }
}

export const useKnowledgeGraphStore = create<KnowledgeGraphState>((set, get) => ({
  allGraphData: { nodes: [], links: [] },
  isLoading: false,
  isInitialized: false,

  loadInitialGraph: async (limit = 100) => {
    const { isInitialized, isLoading, allGraphData } = get()

    // 已初始化或正在加载，跳过
    if (isInitialized || isLoading) return

    // 已达上限，跳过
    if (allGraphData.nodes.length >= MAX_NODES) return

    set({ isLoading: true })
    try {
      const data = await fetchInitialGraph(limit)
      set({
        allGraphData: data,
        isInitialized: true
      })
    } catch (error) {
      console.error('[KG Store] 加载图数据失败:', error)
    } finally {
      set({ isLoading: false })
    }
  },

  searchAndMerge: async (query: string, limit = 10, signal?: AbortSignal) => {
    const { allGraphData } = get()

    try {
      const searchResult = await searchGraph(query, limit, signal)

      // 检查合并后是否超过上限
      const merged = mergeGraph(allGraphData, searchResult)
      if (merged.nodes.length <= MAX_NODES) {
        set({ allGraphData: merged })
      }
      // 无论是否合并，都返回搜索结果供组件使用
      return searchResult
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return { nodes: [], links: [] }
      }
      if (error instanceof Error && error.name === 'AbortError') {
        return { nodes: [], links: [] }
      }
      console.error('[KG Store] 搜索失败:', error)
      return { nodes: [], links: [] }
    }
  },

  refresh: async () => {
    set({
      allGraphData: { nodes: [], links: [] },
      isInitialized: false
    })
    await get().loadInitialGraph()
  },

  clear: () => {
    set({
      allGraphData: { nodes: [], links: [] },
      isLoading: false,
      isInitialized: false
    })
  }
}))
