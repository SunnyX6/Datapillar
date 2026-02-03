/**
 * i18n 国际化配置
 *
 * 参考 workbench 的初始化方式，覆盖导航与营销站点文案。
 */

import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import enNavigation from '@/locales/en-US/navigation.json'
import enHome from '@/locales/en-US/home.json'
import zhNavigation from '@/locales/zh-CN/navigation.json'
import zhHome from '@/locales/zh-CN/home.json'

const resources = {
  'en-US': {
    navigation: enNavigation,
    home: enHome
  },
  'zh-CN': {
    navigation: zhNavigation,
    home: zhHome
  }
} as const

i18n
  .use(initReactI18next)
  .init({
    resources,
    lng: 'zh-CN',
    fallbackLng: 'en-US',
    defaultNS: 'home',
    ns: ['home', 'navigation'],

    interpolation: {
      escapeValue: false
    },

    react: {
      useSuspense: false
    },

    debug: false
  })

export default i18n
