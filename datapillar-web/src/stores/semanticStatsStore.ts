/**
 * 语义资产统计 Store
 * 缓存各类语义资产的统计数据，避免重复请求
 */

import { create } from 'zustand'

interface SemanticStatsState {
  metricsTotal: number | null
  wordRootsTotal: number | null
  setMetricsTotal: (total: number) => void
  setWordRootsTotal: (total: number) => void
}

export const useSemanticStatsStore = create<SemanticStatsState>((set) => ({
  metricsTotal: null,
  wordRootsTotal: null,
  setMetricsTotal: (total) => set({ metricsTotal: total }),
  setWordRootsTotal: (total) => set({ wordRootsTotal: total })
}))
