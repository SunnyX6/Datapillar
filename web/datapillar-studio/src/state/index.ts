/**
 * Stores 统一导出
 *
 * 使用 Zustand 进行状态管理
 * - Auth Store: localStorage 持久化（7天/30天）
 * - Theme Store: localStorage 持久化（永久保存）
 * - I18n Store: localStorage 持久化（永久保存）
 */

// Auth Store - 认证相关状态管理
export { useAuthStore } from './authStore'
export { useSetupStore, type SetupGuardStatus } from './setupStore'

// Theme Store - 主题相关状态管理
export {
  useThemeStore,
  useThemeMode,
  useIsDark,
  type ThemeMode
} from './themeStore'

// I18n Store - 国际化相关状态管理
export {
  useI18nStore,
  useLanguage,
  useIsZhCN,
  type Language
} from './i18nStore'

// Layout Store - 布局偏好
export { useLayoutStore } from './layoutStore'

// Search Store - 全局搜索状态
export {
  useSearchStore,
  useSearchPlaceholder,
  useSearchTerm,
  type SearchContext
} from './searchStore'
