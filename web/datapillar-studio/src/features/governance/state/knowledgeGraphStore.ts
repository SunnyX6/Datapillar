/**
 * Knowledge graph Store
 *
 * Caching graph data，Avoid duplicate requests
 * Note：Node limit 500，It will no longer load automatically after exceeding the limit.，Users are required to actively search
 */

import { create } from 'zustand'
import { fetchInitialGraph, searchGraph, type GraphData, type GraphLink } from '@/services/knowledgeGraphService'

const MAX_NODES = 500

interface KnowledgeGraphState {
  /** Complete graph data（After merger） */
  allGraphData: GraphData
  /** Is loading */
  isLoading: boolean
  /** Has it been initialized? */
  isInitialized: boolean

  /** Load initial graph data */
  loadInitialGraph: (limit?: number) => Promise<void>
  /** Search and merge results */
  searchAndMerge: (query: string, limit?: number, signal?: AbortSignal) => Promise<GraphData>
  /** Force refresh */
  refresh: () => Promise<void>
  /** clear cache */
  clear: () => void
}

/**
 * Merge graph data（Remove duplicates）
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

    // Initialized or loading，skip
    if (isInitialized || isLoading) return

    // The limit has been reached，skip
    if (allGraphData.nodes.length >= MAX_NODES) return

    set({ isLoading: true })
    try {
      const data = await fetchInitialGraph(limit)
      set({
        allGraphData: data,
        isInitialized: true
      })
    } catch (error) {
      console.error('[KG Store] Failed to load graph data:', error)
    } finally {
      set({ isLoading: false })
    }
  },

  searchAndMerge: async (query: string, limit = 10, signal?: AbortSignal) => {
    const { allGraphData } = get()

    try {
      const searchResult = await searchGraph(query, limit, signal)

      // Check whether the upper limit is exceeded after merging
      const merged = mergeGraph(allGraphData, searchResult)
      if (merged.nodes.length <= MAX_NODES) {
        set({ allGraphData: merged })
      }
      // Regardless of whether to merge，Both return search results for use by components
      return searchResult
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return { nodes: [], links: [] }
      }
      if (error instanceof Error && error.name === 'AbortError') {
        return { nodes: [], links: [] }
      }
      console.error('[KG Store] Search failed:', error)
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
