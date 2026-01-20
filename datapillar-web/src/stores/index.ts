/**
 * Stores 统一导出
 *
 * 使用 Zustand 进行状态管理
 * - Auth Store: sessionStorage 持久化（会话级）
 * - Theme Store: localStorage 持久化（永久保存）
 * - I18n Store: localStorage 持久化（永久保存）
 */

// Auth Store - 认证相关状态管理
export { useAuthStore } from './authStore'

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

// Workflow Studio Store
export { useWorkflowStudioStore, type AgentActivity, type ProcessActivity, type ChatMessage } from './workflowStudioStore'

// Layout Store - 布局偏好
export { useLayoutStore } from './layoutStore'

// Search Store - 全局搜索状态
export {
  useSearchStore,
  useSearchPlaceholder,
  useSearchTerm,
  type SearchContext
} from './searchStore'

// Semantic Stats Store - 语义资产统计缓存
export { useSemanticStatsStore } from './semanticStatsStore'

// Component Store - 组件缓存
export { useComponentStore } from './componentStore'

// Metadata Store - 元数据缓存（Catalog/Schema/Table）
export { useMetadataStore } from './metadataStore'

// Knowledge Graph Store - 知识谱图缓存
export { useKnowledgeGraphStore } from './knowledgeGraphStore'
