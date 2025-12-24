/**
 * 全局搜索状态管理
 *
 * 根据当前页面上下文动态切换搜索范围和提示
 */

import { create } from 'zustand'

export type SearchContext =
  | 'dashboard'
  | 'metadata'
  | 'semantic'
  | 'semantic-metrics'
  | 'semantic-glossary'
  | 'knowledge'
  | 'default'

interface SearchContextConfig {
  placeholder: string
  scope: string[]
}

const SEARCH_CONTEXT_MAP: Record<SearchContext, SearchContextConfig> = {
  dashboard: {
    placeholder: '搜索项目、工作流...',
    scope: ['projects', 'workflows']
  },
  metadata: {
    placeholder: '搜索 Catalog、Schema、Table...',
    scope: ['catalog', 'schema', 'table']
  },
  semantic: {
    placeholder: '搜索指标、词根、数据服务...',
    scope: ['metrics', 'glossary', 'apis', 'models', 'standards']
  },
  'semantic-metrics': {
    placeholder: '搜索指标名称或编码...',
    scope: ['metrics']
  },
  'semantic-glossary': {
    placeholder: '搜索词根、含义...',
    scope: ['glossary']
  },
  knowledge: {
    placeholder: '搜索知识图谱节点...',
    scope: ['knowledge']
  },
  default: {
    placeholder: '全局搜索...',
    scope: ['all']
  }
}

interface SearchState {
  /** 当前搜索关键词 */
  searchTerm: string
  /** 当前搜索上下文 */
  context: SearchContext
  /** 搜索框是否展开 */
  isOpen: boolean
  /** 设置搜索关键词 */
  setSearchTerm: (term: string) => void
  /** 设置搜索上下文 */
  setContext: (context: SearchContext) => void
  /** 设置搜索框展开状态 */
  setIsOpen: (isOpen: boolean) => void
  /** 清空搜索 */
  clearSearch: () => void
  /** 获取当前上下文配置 */
  getContextConfig: () => SearchContextConfig
}

export const useSearchStore = create<SearchState>((set, get) => ({
  searchTerm: '',
  context: 'default',
  isOpen: false,

  setSearchTerm: (term) => set({ searchTerm: term }),

  setContext: (context) => set({ context, searchTerm: '' }),

  setIsOpen: (isOpen) => set({ isOpen }),

  clearSearch: () => set({ searchTerm: '', isOpen: false }),

  getContextConfig: () => {
    const { context } = get()
    return SEARCH_CONTEXT_MAP[context] || SEARCH_CONTEXT_MAP.default
  }
}))

/** Hook: 获取当前搜索上下文的 placeholder */
export const useSearchPlaceholder = () => {
  const context = useSearchStore((state) => state.context)
  return SEARCH_CONTEXT_MAP[context]?.placeholder || SEARCH_CONTEXT_MAP.default.placeholder
}

/** Hook: 获取当前搜索关键词 */
export const useSearchTerm = () => useSearchStore((state) => state.searchTerm)
