/**
 * Semantic Asset Statistics Store
 * Cache statistical data for various semantic assets，Avoid duplicate requests
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
