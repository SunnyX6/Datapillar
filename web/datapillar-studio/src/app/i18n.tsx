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
import enCollaboration from '@/locales/en-US/collaboration.json'
import enException from '@/locales/en-US/exception.json'
import enLlm from '@/locales/en-US/llm.json'
import enLogin from '@/locales/en-US/login.json'
import enNavigation from '@/locales/en-US/navigation.json'
import enKnowledgeGraph from '@/locales/en-US/knowledgeGraph.json'
import enOneMeta from '@/locales/en-US/oneMeta.json'
import enOneSemantics from '@/locales/en-US/oneSemantics.json'
import enPermission from '@/locales/en-US/permission.json'
import enWiki from '@/locales/en-US/wiki.json'
import zhCommon from '@/locales/zh-CN/common.json'
import zhCollaboration from '@/locales/zh-CN/collaboration.json'
import zhException from '@/locales/zh-CN/exception.json'
import zhLlm from '@/locales/zh-CN/llm.json'
import zhLogin from '@/locales/zh-CN/login.json'
import zhNavigation from '@/locales/zh-CN/navigation.json'
import zhKnowledgeGraph from '@/locales/zh-CN/knowledgeGraph.json'
import zhOneMeta from '@/locales/zh-CN/oneMeta.json'
import zhOneSemantics from '@/locales/zh-CN/oneSemantics.json'
import zhPermission from '@/locales/zh-CN/permission.json'
import zhWiki from '@/locales/zh-CN/wiki.json'

// ==================== Language resource configuration ====================

const resources = {
  'en-US': {
    common: enCommon,
    collaboration: enCollaboration,
    exception: enException,
    llm: enLlm,
    login: enLogin,
    navigation: enNavigation,
    knowledgeGraph: enKnowledgeGraph,
    oneMeta: enOneMeta,
    oneSemantics: enOneSemantics,
    permission: enPermission,
    wiki: enWiki
  },
  'zh-CN': {
    common: zhCommon,
    collaboration: zhCollaboration,
    exception: zhException,
    llm: zhLlm,
    login: zhLogin,
    navigation: zhNavigation,
    knowledgeGraph: zhKnowledgeGraph,
    oneMeta: zhOneMeta,
    oneSemantics: zhOneSemantics,
    permission: zhPermission,
    wiki: zhWiki
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
    ns: ['common', 'collaboration', 'exception', 'oneMeta', 'oneSemantics', 'knowledgeGraph', 'login', 'navigation', 'permission', 'llm', 'wiki'], // Supported namespaces

    interpolation: {
      escapeValue: false // React Already prevented XSS
    },

    react: {
      useSuspense: false // Disable Suspense，avoid flickering
    },

    debug: false // Turn off debugging in production environment
  })

export default i18n
