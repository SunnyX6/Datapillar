/**
 * i18n 国际化配置
 *
 * 功能：
 * 1. 初始化 i18next
 * 2. 配置语言资源加载
 * 3. 支持 TypeScript 类型推导
 */

import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

// 导入语言资源
import enCommon from '@/locales/en-US/common.json'
import enLogin from '@/locales/en-US/login.json'
import enNavigation from '@/locales/en-US/navigation.json'
import zhCommon from '@/locales/zh-CN/common.json'
import zhLogin from '@/locales/zh-CN/login.json'
import zhNavigation from '@/locales/zh-CN/navigation.json'

// ==================== 语言资源配置 ====================

const resources = {
  'en-US': {
    common: enCommon,
    login: enLogin,
    navigation: enNavigation
  },
  'zh-CN': {
    common: zhCommon,
    login: zhLogin,
    navigation: zhNavigation
  }
} as const

// ==================== i18n 初始化 ====================

i18n
  .use(initReactI18next) // 绑定 react-i18next
  .init({
    resources,
    lng: 'en-US', // 默认语言：英文
    fallbackLng: 'en-US', // 回退语言
    defaultNS: 'common', // 默认命名空间
    ns: ['common', 'login', 'navigation'], // 支持的命名空间

    interpolation: {
      escapeValue: false // React 已经防止 XSS
    },

    react: {
      useSuspense: false // 禁用 Suspense，避免闪烁
    },

    debug: false // 生产环境关闭调试
  })

export default i18n
