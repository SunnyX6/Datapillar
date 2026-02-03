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

export const connectModel = (connectedIds: string[], modelId: string) => {
  if (connectedIds.includes(modelId)) return connectedIds
  return [...connectedIds, modelId]
}

export const toggleModelSelection = (selectedIds: string[], modelId: string, maxSelection = MAX_COMPARE_MODELS) => {
  if (selectedIds.includes(modelId)) {
    return selectedIds.filter((id) => id !== modelId)
  }
  if (selectedIds.length >= maxSelection) {
    return selectedIds
  }
  return [...selectedIds, modelId]
}
