/**
 * 主题状态管理 - Zustand
 *
 * 功能：
 * 1. 支持 light/dark 两种模式
 * 2. localStorage 持久化用户选择
 * 3. 无闪烁切换（配合 index.html 脚本）
 */

import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'

// ==================== 类型定义 ====================

export type ThemeMode = 'light' | 'dark'

interface ThemeState {
  // 状态
  mode: ThemeMode // 用户选择的模式

  // Actions
  setMode: (mode: ThemeMode) => void
  initialize: () => void
}

// ==================== 工具函数 ====================

/**
 * 应用主题到 DOM
 */
let themeSwitchTimeout: number | undefined

const applyTheme = (theme: ThemeMode) => {
  const root = document.documentElement

  root.classList.add('theme-switching')
  if (themeSwitchTimeout) {
    window.clearTimeout(themeSwitchTimeout)
  }

  if (theme === 'dark') {
    root.classList.add('dark')
  } else {
    root.classList.remove('dark')
  }

  themeSwitchTimeout = window.setTimeout(() => {
    root.classList.remove('theme-switching')
  }, 120)
}

// ==================== Zustand Store ====================

export const useThemeStore = create<ThemeState>()(
  persist(
    (set, get) => ({
      // 初始状态（默认深色，适合数据平台）
      mode: 'dark',

      /**
       * 设置主题模式
       */
      setMode: (mode: ThemeMode) => {
        set({ mode })
        applyTheme(mode)
      },

      /**
       * 初始化主题
       * 应用启动时调用，设置初始主题
       */
      initialize: () => {
        const { mode } = get()
        applyTheme(mode)
      }
    }),
    {
      name: 'theme-storage', // localStorage key
      storage: createJSONStorage(() => localStorage), // 持久化到 localStorage
      // 只持久化 mode
      partialize: (state) => ({
        mode: state.mode
      })
    }
  )
)

// ==================== 辅助 Hooks ====================

/**
 * 获取主题模式
 */
export const useThemeMode = () => {
  return useThemeStore(state => state.mode)
}

/**
 * 检查是否为暗色主题
 */
export const useIsDark = () => {
  return useThemeStore(state => state.mode === 'dark')
}
