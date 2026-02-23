export type LlmProvider =
  | 'openai'
  | 'anthropic'
  | 'deepseek'
  | 'google'
  | 'meta'
  | 'mistral'
  | 'custom'
  | (string & {})

export type ModelCategory = 'chat' | 'embeddings' | 'reranking' | 'code'

export interface ModelStats {
  context: string
  inputPrice: string
  outputPrice: string
  params?: string
}

export interface ModelRecord {
  id: string
  modelPk?: number
  name: string
  provider: LlmProvider
  providerLabel?: string
  description: string
  tags: string[]
  type: ModelCategory
  contextGroup: string
  stats: ModelStats
  baseUrl?: string
  maskedApiKey?: string
  hasApiKey?: boolean
  status?: string
  isNew?: boolean
}

export interface ModelFilters {
  providers: LlmProvider[]
  types: ModelCategory[]
  contexts: string[]
}
