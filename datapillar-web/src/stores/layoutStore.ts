/**
 * 布局状态管理 - 负责侧边栏折叠偏好
 *
 * 约束：
 * - 使用 Zustand 共享状态，避免路由切换重置
 * - 通过 globalThis 确保懒加载路由不会重复实例化 store
 * - 仅在浏览器端与 localStorage 同步，防止 SSR 报错
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
