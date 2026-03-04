/**
 * Topic status management - Zustand
 *
 * Function：
 * 1. support light/dark Two modes
 * 2. localStorage Persisting user selections
 * 3. flicker-free switching（cooperate index.html script）
 */

import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'

// ==================== type definition ====================

export type ThemeMode = 'light' | 'dark'

interface ThemeState {
  // Status
  mode: ThemeMode // User selected mode

  // Actions
  setMode: (mode: ThemeMode) => void
  initialize: () => void
}

// ==================== Utility function ====================

/**
 * Apply theme to DOM
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
      // initial state（Default light color）
      mode: 'light',

      /**
       * Set theme mode
       */
      setMode: (mode: ThemeMode) => {
        set({ mode })
        applyTheme(mode)
      },

      /**
       * Initialize theme
       * Called when the application starts，Set initial theme
       */
      initialize: () => {
        const { mode } = get()
        applyTheme(mode)
      }
    }),
    {
      name: 'theme-storage', // localStorage key
      storage: createJSONStorage(() => localStorage), // persist to localStorage
      // Persistence only mode
      partialize: (state) => ({
        mode: state.mode
      })
    }
  )
)

// ==================== Auxiliary Hooks ====================

/**
 * Get theme mode
 */
export const useThemeMode = () => {
  return useThemeStore(state => state.mode)
}

/**
 * Check for dark theme
 */
export const useIsDark = () => {
  return useThemeStore(state => state.mode === 'dark')
}
