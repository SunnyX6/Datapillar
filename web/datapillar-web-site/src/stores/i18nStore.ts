/**
 * 国际化状态管理 - Zustand
 *
 * 功能：
 * 1. 支持 zh-CN/en-US 两种语言
 * 2. localStorage 持久化用户选择
 * 3. 同步切换 i18next 语言
 */

import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import i18n from '@/lib/i18n'

export type Language = 'zh-CN' | 'en-US'

interface I18nState {
  language: Language
  setLanguage: (language: Language) => void
  initialize: () => void
}

const changeI18nLanguage = (language: Language) => {
  i18n.changeLanguage(language)
  document.documentElement.lang = language
}

export const useI18nStore = create<I18nState>()(
  persist(
    (set, get) => ({
      language: 'zh-CN',
      setLanguage: (language: Language) => {
        set({ language })
        changeI18nLanguage(language)
      },
      initialize: () => {
        const { language } = get()
        changeI18nLanguage(language)
      }
    }),
    {
      name: 'i18n-storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        language: state.language
      })
    }
  )
)

export const useLanguage = () => useI18nStore(state => state.language)
