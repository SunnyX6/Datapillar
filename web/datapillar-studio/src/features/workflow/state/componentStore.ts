/**
 * Component caching Store
 *
 * Cache the list of components obtained from the backend，Avoid duplicate requests
 */

import { create } from 'zustand'
import { type JobComponent, getAllComponents, getComponentStyle } from '@/services/componentService'

interface ComponentStoreState {
  components: JobComponent[]
  componentMap: Map<string, JobComponent>
  isLoading: boolean
  isLoaded: boolean
  error: string | null
  loadComponents: () => Promise<void>
  getComponent: (code: string) => JobComponent | undefined
  getStyle: (code: string) => { icon: string; color: string }
}

export const useComponentStore = create<ComponentStoreState>((set, get) => ({
  components: [],
  componentMap: new Map(),
  isLoading: false,
  isLoaded: false,
  error: null,

  loadComponents: async () => {
    if (get().isLoaded || get().isLoading) {
      return
    }

    set({ isLoading: true, error: null })

    try {
      const components = await getAllComponents()
      const componentMap = new Map<string, JobComponent>()
      for (const comp of components) {
        componentMap.set(comp.componentCode, comp)
      }

      set({
        components,
        componentMap,
        isLoading: false,
        isLoaded: true
      })
    } catch (error) {
      set({
        isLoading: false,
        error: (error as Error).message
      })
    }
  },

  getComponent: (code: string) => {
    return get().componentMap.get(code)
  },

  getStyle: (code: string) => {
    const component = get().componentMap.get(code)
    return getComponentStyle(component)
  }
}))
