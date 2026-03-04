/**
 * i18n International configuration
 *
 * Function：
 * 1. initialization i18next
 * 2. Configure language resource loading
 * 3. support TypeScript type inference
 */

import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

// Import language resources
import enCommon from '@/locales/en-US/common.json'
import enLogin from '@/locales/en-US/login.json'
import enNavigation from '@/locales/en-US/navigation.json'
import zhCommon from '@/locales/zh-CN/common.json'
import zhLogin from '@/locales/zh-CN/login.json'
import zhNavigation from '@/locales/zh-CN/navigation.json'

// ==================== Language resource configuration ====================

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

// ==================== i18n initialization ====================

i18n
  .use(initReactI18next) // binding react-i18next
  .init({
    resources,
    lng: 'zh-CN', // Default language：Chinese
    fallbackLng: 'zh-CN', // fallback language
    defaultNS: 'common', // default namespace
    ns: ['common', 'login', 'navigation'], // Supported namespaces

    interpolation: {
      escapeValue: false // React Already prevented XSS
    },

    react: {
      useSuspense: false // Disable Suspense，avoid flickering
    },

    debug: false // Turn off debugging in production environment
  })

export default i18n
