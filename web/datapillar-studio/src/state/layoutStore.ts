/**
 * Layout state management - Responsible for sidebar collapse preferences
 *
 * constraint：
 * - use Zustand Shared status，Avoid routing switch resets
 * - Pass globalThis Ensure that lazy loading routes are not instantiated repeatedly store
 * - Only on the browser side with localStorage sync，prevent SSR Report an error
 */

import { create } from 'zustand'

const STORAGE_KEY = 'layout:sidebar-collapsed'

const readInitialCollapsed = () => {
  if (typeof window === 'undefined') {
    return false
  }
  return window.localStorage.getItem(STORAGE_KEY) === '1'
}

const syncCollapsedToStorage = (collapsed: boolean) => {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(STORAGE_KEY, collapsed ? '1' : '0')
}

interface LayoutState {
  isSidebarCollapsed: boolean
  toggleSidebar: () => void
  setSidebarCollapsed: (collapsed: boolean) => void
}

const createLayoutStore = () =>
  create<LayoutState>((set) => ({
    isSidebarCollapsed: readInitialCollapsed(),
    toggleSidebar: () =>
      set((state) => {
        const next = !state.isSidebarCollapsed
        syncCollapsedToStorage(next)
        return { isSidebarCollapsed: next }
      }),
    setSidebarCollapsed: (collapsed) => {
      syncCollapsedToStorage(collapsed)
      set({ isSidebarCollapsed: collapsed })
    }
  }))

type LayoutStore = ReturnType<typeof createLayoutStore>

const getLayoutStore = () => {
  const globalScope = globalThis as typeof globalThis & {
    __dataAiLayoutStore?: LayoutStore
  }

  if (!globalScope.__dataAiLayoutStore) {
    globalScope.__dataAiLayoutStore = createLayoutStore()
  }

  return globalScope.__dataAiLayoutStore!
}

export const useLayoutStore = getLayoutStore()
