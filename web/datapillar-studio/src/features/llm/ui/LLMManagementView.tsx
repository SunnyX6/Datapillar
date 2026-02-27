import { createPortal } from 'react-dom'
import { useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import {
  connectAdminModel,
  createAdminModel,
  createAdminProvider,
  deleteAdminProvider,
  listAdminModels,
  listAdminProviders,
  updateAdminProvider,
  type StudioLlmProvider,
  type UpdateAdminLlmProviderRequest,
  type CreateAdminLlmModelRequest
} from '@/services/studioLlmService'
import { LLMFilter } from './LLMFilter'
import { LLMModels } from './LLMModels'
import { LLMTest } from './LLMTest'
import { mapAdminModelToRecord, resolveProviderLabel } from '../utils/modelAdapters'
import type { LlmProvider, ModelCategory, ModelFilters, ModelRecord } from '../utils/types'
import { collectConnectedModelIds, connectModel } from '../utils'

const MODEL_TYPE_OPTIONS: Array<{ label: string; value: ModelCategory }> = [
  { label: 'Chat', value: 'chat' },
  { label: 'Embeddings', value: 'embeddings' },
  { label: 'Reranking', value: 'reranking' },
  { label: 'Code', value: 'code' }
]

const CONTEXT_LENGTH_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '32K', value: '32768' },
  { label: '64K', value: '65536' },
  { label: '128K', value: '128K' },
  { label: '256K', value: '256K' },
  { label: '512K', value: '512K' },
  { label: '1M', value: '1M' }
]

function normalizeProviderCode(code?: string | null): LlmProvider | null {
  const normalizedCode = code?.trim().toLowerCase()
  if (!normalizedCode) {
    return null
  }
  return normalizedCode as LlmProvider
}

function normalizeProviderModelIds(modelIds?: string[] | null): string[] {
  if (!Array.isArray(modelIds) || modelIds.length === 0) {
    return []
  }
  const normalized = modelIds
    .map((modelId) => modelId.trim())
    .filter((modelId) => modelId.length > 0)
  return Array.from(new Set(normalized))
}

function normalizeProviderBaseUrl(baseUrl?: string | null): string {
  const normalized = baseUrl?.trim()
  return normalized && normalized.length > 0 ? normalized : ''
}

export function LLMManagementView() {
  const [models, setModels] = useState<ModelRecord[]>([])
  const [providers, setProviders] = useState<StudioLlmProvider[]>([])
  const [isLoadingModels, setIsLoadingModels] = useState(true)
  const [connectedModelIds, setConnectedModelIds] = useState<number[]>([])
  const [activeAiModelId, setActiveAiModelId] = useState<number | null>(null)
  const [drawerTab, setDrawerTab] = useState<'config' | 'playground'>('config')
  const [filters, setFilters] = useState<ModelFilters>({ providers: [], types: [], contexts: [] })

  const providerOptions = useMemo<Array<{ label: string; value: LlmProvider }>>(() => {
    const providerMap = new Map<string, string>()
    providers.forEach((provider) => {
      const normalizedCode = provider.code?.trim().toLowerCase()
      if (!normalizedCode || providerMap.has(normalizedCode)) {
        return
      }
      providerMap.set(
        normalizedCode,
        resolveProviderLabel(normalizedCode as LlmProvider, provider.name)
      )
    })
    models.forEach((model) => {
      if (!providerMap.has(model.provider)) {
        providerMap.set(model.provider, resolveProviderLabel(model.provider, model.providerLabel))
      }
    })
    return Array.from(providerMap.entries()).map(([value, label]) => ({
      value: value as LlmProvider,
      label
    }))
  }, [models, providers])

  const providerModelIdMap = useMemo(() => {
    const map = new Map<LlmProvider, string[]>()
    providers.forEach((provider) => {
      const providerCode = normalizeProviderCode(provider.code)
      if (!providerCode) {
        return
      }
      map.set(providerCode, normalizeProviderModelIds(provider.modelIds))
    })
    return map
  }, [providers])

  const providerBaseUrlMap = useMemo(() => {
    const map = new Map<LlmProvider, string>()
    providers.forEach((provider) => {
      const providerCode = normalizeProviderCode(provider.code)
      if (!providerCode) {
        return
      }
      map.set(providerCode, normalizeProviderBaseUrl(provider.baseUrl))
    })
    return map
  }, [providers])

  const filterTypeOptions = MODEL_TYPE_OPTIONS
  const contextOptions = CONTEXT_LENGTH_OPTIONS

  useEffect(() => {
    let ignore = false

    const syncModels = async () => {
      setIsLoadingModels(true)
      try {
        const [modelResponse, providerResponse] = await Promise.all([
          listAdminModels(),
          listAdminProviders().catch(() => [])
        ])

        if (ignore) {
          return
        }

        const nextModels = modelResponse.map(mapAdminModelToRecord)
        setProviders(providerResponse)
        setModels(nextModels)
        setConnectedModelIds(collectConnectedModelIds(nextModels))
      } catch (error) {
        if (ignore) {
          return
        }
        const message = error instanceof Error ? error.message : String(error)
        toast.error(`加载模型失败：${message}`)
        setProviders([])
        setModels([])
        setConnectedModelIds([])
      } finally {
        if (!ignore) {
          setIsLoadingModels(false)
        }
      }
    }

    void syncModels()

    return () => {
      ignore = true
    }
  }, [])

  useEffect(() => {
    if (!activeAiModelId) {
      return
    }
    if (models.some((model) => model.aiModelId === activeAiModelId)) {
      return
    }
    setActiveAiModelId(null)
  }, [activeAiModelId, models])

  useEffect(() => {
    const allowedProviders = new Set(providerOptions.map((option) => option.value))
    const allowedTypes = new Set(filterTypeOptions.map((option) => option.value))
    const allowedContexts = new Set(contextOptions.map((option) => option.value))
    setFilters((prev) => {
      const nextProviders = prev.providers.filter((provider) => allowedProviders.has(provider))
      const nextTypes = prev.types.filter((type) => allowedTypes.has(type))
      const nextContexts = prev.contexts.filter((context) => allowedContexts.has(context))
      if (
        nextProviders.length === prev.providers.length
        && nextTypes.length === prev.types.length
        && nextContexts.length === prev.contexts.length
      ) {
        return prev
      }
      return { ...prev, providers: nextProviders, types: nextTypes, contexts: nextContexts }
    })
  }, [contextOptions, filterTypeOptions, providerOptions])

  const toggleFilter = <T extends keyof ModelFilters>(group: T, value: ModelFilters[T][number]) => {
    setFilters((prev) => {
      const currentValues = prev[group] as Array<ModelFilters[T][number]>
      const nextValues = currentValues.includes(value)
        ? currentValues.filter((item) => item !== value)
        : [...currentValues, value]
      return { ...prev, [group]: nextValues } as ModelFilters
    })
  }

  const refreshProviders = async (): Promise<StudioLlmProvider[]> => {
    const nextProviders = await listAdminProviders()
    setProviders(nextProviders)
    return nextProviders
  }

  const handleCreateProvider = async (request: {
    code: string
    name?: string
    baseUrl?: string
    modelIds: string[]
  }) => {
    await createAdminProvider({
      code: request.code,
      name: request.name,
      baseUrl: request.baseUrl
    })

    if (request.modelIds.length > 0) {
      await updateAdminProvider(request.code, { addModelIds: request.modelIds })
    }

    await refreshProviders()
  }

  const handleUpdateProvider = async (providerCode: string, request: UpdateAdminLlmProviderRequest) => {
    await updateAdminProvider(providerCode, request)
    await refreshProviders()
  }

  const handleDeleteProvider = async (providerCode: string) => {
    await deleteAdminProvider(providerCode)
    await refreshProviders()
  }

  const handleCreateModel = async (request: CreateAdminLlmModelRequest): Promise<boolean> => {
    try {
      const createdModel = await createAdminModel(request)
      const createdRecord = mapAdminModelToRecord(createdModel)

      setModels((prev) => {
        const next = prev.filter((item) => item.aiModelId !== createdRecord.aiModelId)
        return [createdRecord, ...next]
      })
      if (createdRecord.hasApiKey) {
        setConnectedModelIds((prev) => connectModel(prev, createdRecord.aiModelId))
      }
      toast.success(`已添加模型：${createdRecord.name}`)
      return true
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`添加模型失败：${message}`)
      return false
    }
  }

  const handleModelSelect = (aiModelId: number, isConnected: boolean) => {
    if (activeAiModelId === aiModelId) {
      setActiveAiModelId(null)
      return
    }
    setDrawerTab(isConnected ? 'playground' : 'config')
    setActiveAiModelId(aiModelId)
  }

  const handleOpenTest = (aiModelId: number, tab: 'config' | 'playground') => {
    setDrawerTab(tab)
    setActiveAiModelId(aiModelId)
  }

  const handleConnectModel = async (
    model: ModelRecord,
    request: { apiKey: string; baseUrl?: string }
  ): Promise<boolean> => {
    if (!model.aiModelId) {
      toast.error('模型主键缺失，无法连接')
      return false
    }

    const normalizedApiKey = request.apiKey.trim()
    if (!normalizedApiKey) {
      toast.error('API Key 不能为空')
      return false
    }

    const normalizedBaseUrl = request.baseUrl?.trim()
    try {
      await connectAdminModel(model.aiModelId, {
        apiKey: normalizedApiKey,
        baseUrl: normalizedBaseUrl || undefined
      })

      const nextModels = (await listAdminModels()).map(mapAdminModelToRecord)
      setModels(nextModels)
      setConnectedModelIds(collectConnectedModelIds(nextModels))
      setDrawerTab('playground')
      toast.success(`模型连接成功：${model.name}`)
      return true
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`模型连接失败：${message}`)
      return false
    }
  }

  const activeModel = useMemo(
    () => models.find((model) => model.aiModelId === activeAiModelId) ?? null,
    [activeAiModelId, models]
  )

  const isActiveModelConnected = activeModel
    ? connectedModelIds.includes(activeModel.aiModelId) || Boolean(activeModel.hasApiKey)
    : false

  return (
    <section className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      <LLMFilter
        providerOptions={providerOptions}
        filterTypeOptions={filterTypeOptions}
        contextOptions={contextOptions}
        filters={filters}
        providers={providers}
        onToggleProvider={(value) => toggleFilter('providers', value)}
        onToggleType={(value) => toggleFilter('types', value)}
        onToggleContext={(value) => toggleFilter('contexts', value)}
        onCreateProvider={handleCreateProvider}
        onUpdateProvider={handleUpdateProvider}
        onDeleteProvider={handleDeleteProvider}
      />

      <LLMModels
        models={models}
        isLoadingModels={isLoadingModels}
        filters={filters}
        providerOptions={providerOptions}
        providerModelIdMap={providerModelIdMap}
        providerBaseUrlMap={providerBaseUrlMap}
        activeModelId={activeAiModelId}
        connectedModelIds={connectedModelIds}
        onModelSelect={handleModelSelect}
        onOpenTest={handleOpenTest}
        onCreateModel={handleCreateModel}
      />

      {activeModel &&
        createPortal(
          <LLMTest
            key={`${activeModel.aiModelId}-${drawerTab}-${isActiveModelConnected ? '1' : '0'}`}
            model={activeModel}
            isConnected={isActiveModelConnected}
            defaultTab={drawerTab}
            onClose={() => setActiveAiModelId(null)}
            onConnect={handleConnectModel}
          />,
          document.body
        )}
    </section>
  )
}
