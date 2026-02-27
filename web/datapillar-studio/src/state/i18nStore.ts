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
import i18n from '@/app/i18n'

// ==================== 类型定义 ====================

export type Language = 'zh-CN' | 'en-US'

interface I18nState {
  // 状态
  language: Language // 当前语言

  // Actions
  setLanguage: (language: Language) => void
  initialize: () => void
}

// ==================== 工具函数 ====================

/**
 * 切换 i18next 语言
 */
const changeI18nLanguage = (language: Language) => {
  i18n.changeLanguage(language)
}

// ==================== Zustand Store ====================

export const useI18nStore = create<I18nState>()(
  persist(
    (set, get) => ({
      // 初始状态（默认中文）
      language: 'zh-CN',

      /**
       * 设置语言
       */
      setLanguage: (language: Language) => {
        set({ language })
        changeI18nLanguage(language)
      },

      /**
       * 初始化语言
       * 应用启动时调用，设置初始语言
       */
      initialize: () => {
        const { language } = get()
        changeI18nLanguage(language)
      }
    }),
    {
      name: 'i18n-storage', // localStorage key
      storage: createJSONStorage(() => localStorage), // 持久化到 localStorage
      // 只持久化 language
      partialize: (state) => ({
        language: state.language
      })
    }
  )
)

// ==================== 辅助 Hooks ====================

/**
 * 获取当前语言
 */
export const useLanguage = () => {
  return useI18nStore(state => state.language)
}

/**
 * 检查是否为中文
 */
export const useIsZhCN = () => {
  return useI18nStore(state => state.language === 'zh-CN')
}
