/**
 * International status management - Zustand
 *
 * Function：
 * 1. support zh-CN/en-US two languages
 * 2. localStorage Persisting user selections
 * 3. Synchronous switching i18next language
 */

import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import i18n from '@/app/i18n'

// ==================== type definition ====================

export type Language = 'zh-CN' | 'en-US'

interface I18nState {
  // Status
  language: Language // Current language

  // Actions
  setLanguage: (language: Language) => void
  initialize: () => void
}

// ==================== Utility function ====================

/**
 * switch i18next language
 */
const changeI18nLanguage = (language: Language) => {
  i18n.changeLanguage(language)
}

// ==================== Zustand Store ====================

export const useI18nStore = create<I18nState>()(
  persist(
    (set, get) => ({
      // initial state（Default Chinese）
      language: 'zh-CN',

      /**
       * Set language
       */
      setLanguage: (language: Language) => {
        set({ language })
        changeI18nLanguage(language)
      },

      /**
       * initialization language
       * Called when the application starts，Set initial language
       */
      initialize: () => {
        const { language } = get()
        changeI18nLanguage(language)
      }
    }),
    {
      name: 'i18n-storage', // localStorage key
      storage: createJSONStorage(() => localStorage), // persist to localStorage
      // Persistence only language
      partialize: (state) => ({
        language: state.language
      })
    }
  )
)

// ==================== Auxiliary Hooks ====================

/**
 * Get current language
 */
export const useLanguage = () => {
  return useI18nStore(state => state.language)
}

/**
 * Check if it is Chinese
 */
export const useIsZhCN = () => {
  return useI18nStore(state => state.language === 'zh-CN')
}
