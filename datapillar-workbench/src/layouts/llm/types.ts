export type LlmProvider = 'openai' | 'anthropic' | 'deepseek' | 'google' | 'meta' | 'mistral' | 'custom'

export type ModelCategory = 'chat' | 'embeddings' | 'reranking' | 'code'

export interface ModelStats {
  context: string
  inputPrice: string
  outputPrice: string
  params?: string
}

export interface ModelRecord {
  id: string
  name: string
  provider: LlmProvider
  description: string
  tags: string[]
  type: ModelCategory
  contextGroup: string
  stats: ModelStats
  isNew?: boolean
}

export interface ModelFilters {
  providers: LlmProvider[]
  types: ModelCategory[]
  contexts: string[]
}
