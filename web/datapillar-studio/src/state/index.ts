/**
 * Stores Unified export
 *
 * use Zustand Perform status management
 * - Auth Store: localStorage persistence（7day/30day）
 * - Theme Store: localStorage persistence（Save permanently）
 * - I18n Store: localStorage persistence（Save permanently）
 */

// Auth Store - Certification related status management
export { useAuthStore } from './authStore'
export { useSetupStore, type SetupGuardStatus } from './setupStore'

// Theme Store - Topic related status management
export {
  useThemeStore,
  useThemeMode,
  useIsDark,
  type ThemeMode
} from './themeStore'

// I18n Store - Internationalization related status management
export {
  useI18nStore,
  useLanguage,
  useIsZhCN,
  type Language
} from './i18nStore'

// Layout Store - Layout preferences
export { useLayoutStore } from './layoutStore'

// Search Store - Global search status
export {
  useSearchStore,
  useSearchPlaceholder,
  useSearchTerm,
  type SearchContext
} from './searchStore'
