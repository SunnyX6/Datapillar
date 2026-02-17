import type { StudioLlmModel } from '@/services/studioLlmService'
import type { LlmProvider, ModelCategory, ModelRecord } from './types'

const KNOWN_PROVIDER_LABELS: Record<string, string> = {
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  deepseek: 'DeepSeek',
  google: 'Google',
  meta: 'Meta',
  mistral: 'Mistral',
  custom: 'Custom'
}

const MODEL_TYPE_MAPPING: Record<string, ModelCategory> = {
  CHAT: 'chat',
  LLM: 'chat',
  EMBEDDING: 'embeddings',
  EMBEDDINGS: 'embeddings',
  RERANKING: 'reranking',
  RERANKER: 'reranking',
  CODE: 'code'
}

function normalizeProvider(providerCode?: string | null): LlmProvider {
  const normalized = providerCode?.trim().toLowerCase()
  if (!normalized) {
    return 'custom'
  }
  return normalized as LlmProvider
}

function normalizeModelType(modelType?: string | null): ModelCategory {
  const normalized = modelType?.trim().toUpperCase()
  if (!normalized) {
    return 'chat'
  }
  return MODEL_TYPE_MAPPING[normalized] ?? 'chat'
}

function formatContextGroup(contextTokens?: number | null): string {
  if (!contextTokens || contextTokens <= 0) {
    return 'N/A'
  }
  if (contextTokens % 1000000 === 0) {
    return `${contextTokens / 1000000}M`
  }
  if (contextTokens % 1000 === 0) {
    return `${contextTokens / 1000}K`
  }
  return `${contextTokens}`
}

function formatPrice(priceUsd?: string | null): string {
  if (!priceUsd) {
    return '-'
  }
  const normalized = priceUsd.trim()
  if (!normalized) {
    return '-'
  }
  if (normalized.includes('$') || normalized.includes('/')) {
    return normalized
  }
  return `$${normalized}/M`
}

function toTitleCase(value: string): string {
  return value
    .split(/[-_\s]+/)
    .filter((segment) => segment.length > 0)
    .map((segment) => segment[0]?.toUpperCase() + segment.slice(1).toLowerCase())
    .join(' ')
}

export function resolveProviderLabel(
  provider: LlmProvider,
  providerName?: string | null
): string {
  if (providerName && providerName.trim()) {
    return providerName
  }
  return KNOWN_PROVIDER_LABELS[provider] ?? toTitleCase(provider)
}

export function mapAdminModelToRecord(model: StudioLlmModel): ModelRecord {
  const provider = normalizeProvider(model.providerCode)
  const type = normalizeModelType(model.modelType)
  const contextGroup = formatContextGroup(model.contextTokens)

  return {
    id: model.modelId,
    modelPk: model.id,
    name: model.name,
    provider,
    providerLabel: resolveProviderLabel(provider, model.providerName),
    description: model.description?.trim() || '暂无描述',
    tags: model.tags ?? [],
    type,
    contextGroup,
    stats: {
      context: contextGroup === 'N/A' ? 'N/A' : `${contextGroup} context`,
      inputPrice: formatPrice(model.inputPriceUsd),
      outputPrice: formatPrice(model.outputPriceUsd),
      params: model.embeddingDimension ? `${model.embeddingDimension} dims` : undefined
    },
    hasApiKey: model.hasApiKey,
    status: model.status
  }
}
