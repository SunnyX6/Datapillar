import type { ModelFilters, ModelRecord } from './types'

export const MAX_COMPARE_MODELS = 3

const normalizeQuery = (value: string) => value.trim().toLowerCase()

export const filterModels = (models: ModelRecord[], query: string, filters: ModelFilters) => {
  const normalized = normalizeQuery(query)

  return models.filter((model) => {
    const matchesQuery =
      !normalized ||
      model.name.toLowerCase().includes(normalized) ||
      model.provider.toLowerCase().includes(normalized)

    const matchesProviders = filters.providers.length === 0 || filters.providers.includes(model.provider)
    const matchesTypes = filters.types.length === 0 || filters.types.includes(model.type)
    const matchesContexts = filters.contexts.length === 0 || filters.contexts.includes(model.contextGroup)

    return matchesQuery && matchesProviders && matchesTypes && matchesContexts
  })
}

export const connectModel = (connectedIds: number[], aiModelId: number) => {
  if (connectedIds.includes(aiModelId)) return connectedIds
  return [...connectedIds, aiModelId]
}

export const collectConnectedModelIds = (models: Array<Pick<ModelRecord, 'aiModelId' | 'hasApiKey'>>) => {
  return models
    .filter((model) => Boolean(model.hasApiKey))
    .map((model) => model.aiModelId)
}

export const toggleModelSelection = (selectedIds: number[], aiModelId: number, maxSelection = MAX_COMPARE_MODELS) => {
  if (selectedIds.includes(aiModelId)) {
    return selectedIds.filter((id) => id !== aiModelId)
  }
  if (selectedIds.length >= maxSelection) {
    return selectedIds
  }
  return [...selectedIds, aiModelId]
}
