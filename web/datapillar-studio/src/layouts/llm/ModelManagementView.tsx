import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import type { LucideIcon } from 'lucide-react'
import {
  ArrowUpDown,
  ArrowRight,
  Bot,
  Check,
  Eye,
  EyeOff,
  ChevronDown,
  ChevronRight,
  Copy,
  Globe,
  Key,
  LayoutGrid,
  List,
  Play,
  Plus,
  Settings2,
  Send,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Maximize2,
  Pencil,
  Trash2,
  User,
  X
} from 'lucide-react'
import { toast } from 'sonner'
import { Button, Card, Modal, ModalCancelButton, ModalPrimaryButton, Select, Tooltip } from '@/components/ui'
import { cardWidthClassMap, contentMaxWidthClassMap, drawerWidthClassMap, iconSizeToken, menuWidthClassMap, panelWidthClassMap, tableColumnWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import {
  connectAdminModel,
  createAdminModel,
  deleteAdminProvider,
  createAdminProvider,
  listAdminModels,
  listAdminProviders,
  type StudioLlmProvider,
  type UpdateAdminLlmProviderRequest,
  updateAdminProvider
} from '@/services/studioLlmService'
import { createLlmPlaygroundStream } from '@/services/aiLlmPlaygroundService'
import { mapAdminModelToRecord, resolveProviderLabel } from './modelAdapters'
import { MAX_COMPARE_MODELS, collectConnectedModelIds, connectModel, filterModels, toggleModelSelection } from './utils'
import type { LlmProvider, ModelCategory, ModelFilters, ModelRecord } from './types'

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

const MODEL_TYPE_API_MAP: Record<ModelCategory, 'chat' | 'embeddings' | 'reranking' | 'code'> = {
  chat: 'chat',
  embeddings: 'embeddings',
  reranking: 'reranking',
  code: 'code'
}

const DEFAULT_PROVIDER: LlmProvider = 'openai'

const emptyCreateForm = {
  modelId: '',
  name: '',
  provider: DEFAULT_PROVIDER,
  baseUrl: '',
  apiKey: '',
  description: '',
  modelType: 'chat' as ModelCategory
}

const emptyProviderCreateForm = {
  code: '',
  name: '',
  baseUrl: '',
  modelIds: [] as string[]
}

const emptyProviderEditForm = {
  code: '',
  name: '',
  baseUrl: '',
  modelIds: [] as string[]
}

const getProviderLabel = (model: ModelRecord) => resolveProviderLabel(model.provider, model.providerLabel)

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

function resolveErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

export function ModelManagementView() {
  const [searchQuery, setSearchQuery] = useState('')
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('list')
  const [models, setModels] = useState<ModelRecord[]>([])
  const [providers, setProviders] = useState<StudioLlmProvider[]>([])
  const [isLoadingModels, setIsLoadingModels] = useState(true)
  const [selectedModelIds, setSelectedModelIds] = useState<string[]>([])
  const [connectedModelIds, setConnectedModelIds] = useState<string[]>([])
  const [activeModelId, setActiveModelId] = useState<string | null>(null)
  const [drawerTab, setDrawerTab] = useState<'config' | 'playground'>('config')
  const [filters, setFilters] = useState<ModelFilters>({ providers: [], types: [], contexts: [] })
  const [isCompareOpen, setIsCompareOpen] = useState(false)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [createForm, setCreateForm] = useState(emptyCreateForm)
  const [isCreateApiKeyVisible, setIsCreateApiKeyVisible] = useState(false)
  const [isCreatingModel, setIsCreatingModel] = useState(false)
  const [isCreateProviderOpen, setIsCreateProviderOpen] = useState(false)
  const [isEditProviderOpen, setIsEditProviderOpen] = useState(false)
  const [providerCreateForm, setProviderCreateForm] = useState(emptyProviderCreateForm)
  const [providerEditForm, setProviderEditForm] = useState(emptyProviderEditForm)
  const [isCreatingProvider, setIsCreatingProvider] = useState(false)
  const [isUpdatingProvider, setIsUpdatingProvider] = useState(false)
  const [isDeletingProvider, setIsDeletingProvider] = useState(false)

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

  const contextOptions = CONTEXT_LENGTH_OPTIONS

  const filterTypeOptions = MODEL_TYPE_OPTIONS

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

  const modelIdOptions = useMemo(() => {
    const modelIds = providerModelIdMap.get(createForm.provider) ?? []
    return modelIds.map((modelId) => ({ value: modelId, label: modelId }))
  }, [createForm.provider, providerModelIdMap])

  const filteredModels = useMemo(() => filterModels(models, searchQuery, filters), [models, searchQuery, filters])

  const buildCreateForm = (provider: LlmProvider = providerOptions[0]?.value ?? DEFAULT_PROVIDER) => ({
    ...emptyCreateForm,
    provider,
    modelId: providerModelIdMap.get(provider)?.[0] ?? '',
    baseUrl: providerBaseUrlMap.get(provider) ?? ''
  })

  useEffect(() => {
    let ignore = false

    const syncModels = async () => {
      setIsLoadingModels(true)
      try {
        const [response, providerResponse] = await Promise.all([
          listAdminModels(),
          listAdminProviders().catch(() => [])
        ])
        if (ignore) {
          return
        }
        const nextModels = response.map(mapAdminModelToRecord)
        setProviders(providerResponse)
        setModels(nextModels)
        setConnectedModelIds(collectConnectedModelIds(nextModels))
        setCreateForm((prev) => {
          const providerCodeSet = new Set<LlmProvider>()
          const providerModelIdMapFromResponse = new Map<LlmProvider, string[]>()
          const providerBaseUrlMapFromResponse = new Map<LlmProvider, string>()

          providerResponse.forEach((provider) => {
            const providerCode = normalizeProviderCode(provider.code)
            if (!providerCode) {
              return
            }
            providerCodeSet.add(providerCode)
            providerModelIdMapFromResponse.set(providerCode, normalizeProviderModelIds(provider.modelIds))
            providerBaseUrlMapFromResponse.set(providerCode, normalizeProviderBaseUrl(provider.baseUrl))
          })

          const firstProviderCode = normalizeProviderCode(providerResponse[0]?.code)
          const fallbackProvider = firstProviderCode ?? DEFAULT_PROVIDER
          const nextProvider = providerCodeSet.has(prev.provider) ? prev.provider : fallbackProvider
          const nextProviderModelIds = providerModelIdMapFromResponse.get(nextProvider) ?? []
          const nextModelId = nextProviderModelIds.includes(prev.modelId) ? prev.modelId : (nextProviderModelIds[0] ?? '')
          const nextBaseUrl = providerBaseUrlMapFromResponse.get(nextProvider) ?? ''

          return {
            ...prev,
            provider: nextProvider,
            modelId: nextModelId,
            baseUrl: nextBaseUrl
          }
        })
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
    if (!activeModelId) {
      return
    }
    if (models.some((model) => model.id === activeModelId)) {
      return
    }
    setActiveModelId(null)
  }, [activeModelId, models])

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

  const toggleSelection = (id: string) => {
    setSelectedModelIds((prev) => toggleModelSelection(prev, id))
  }

  const openCompare = () => {
    if (selectedModelIds.length === 0) return
    setIsCompareOpen(true)
  }

  const openDrawer = (modelId: string, tab: 'config' | 'playground') => {
    setDrawerTab(tab)
    setActiveModelId(modelId)
  }

  const handleModelSelect = (modelId: string, isConnected: boolean) => {
    if (activeModelId === modelId) {
      setActiveModelId(null)
      return
    }
    setDrawerTab(isConnected ? 'playground' : 'config')
    setActiveModelId(modelId)
  }

  const handleConnectModel = async (
    model: ModelRecord,
    request: { apiKey: string; baseUrl?: string }
  ): Promise<boolean> => {
    if (!model.modelPk) {
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
      await connectAdminModel(model.modelPk, {
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
      const message = resolveErrorMessage(error)
      toast.error(`模型连接失败：${message}`)
      return false
    }
  }

  const handleProviderChange = (value: LlmProvider) => {
    const nextModelId = providerModelIdMap.get(value)?.[0] ?? ''
    const nextBaseUrl = providerBaseUrlMap.get(value) ?? ''
    setCreateForm((prev) => ({
      ...prev,
      provider: value,
      modelId: nextModelId,
      baseUrl: nextBaseUrl
    }))
  }

  const handleOpenCreateProviderModal = () => {
    setProviderCreateForm(emptyProviderCreateForm)
    setIsCreateProviderOpen(true)
  }

  const handleCloseCreateProviderModal = () => {
    if (isCreatingProvider) {
      return
    }
    setIsCreateProviderOpen(false)
    setProviderCreateForm(emptyProviderCreateForm)
  }

  const handleAddCreateProviderModelId = () => {
    setProviderCreateForm((prev) => ({
      ...prev,
      modelIds: [...prev.modelIds, '']
    }))
  }

  const handleChangeCreateProviderModelId = (index: number, value: string) => {
    setProviderCreateForm((prev) => ({
      ...prev,
      modelIds: prev.modelIds.map((modelId, modelIndex) => (modelIndex === index ? value : modelId))
    }))
  }

  const handleRemoveCreateProviderModelId = (index: number) => {
    setProviderCreateForm((prev) => ({
      ...prev,
      modelIds: prev.modelIds.filter((_, modelIndex) => modelIndex !== index)
    }))
  }

  const handleAddEditProviderModelId = () => {
    setProviderEditForm((prev) => ({
      ...prev,
      modelIds: [...prev.modelIds, '']
    }))
  }

  const handleChangeEditProviderModelId = (index: number, value: string) => {
    setProviderEditForm((prev) => ({
      ...prev,
      modelIds: prev.modelIds.map((modelId, modelIndex) => (modelIndex === index ? value : modelId))
    }))
  }

  const handleRemoveEditProviderModelId = (index: number) => {
    setProviderEditForm((prev) => ({
      ...prev,
      modelIds: prev.modelIds.filter((_, modelIndex) => modelIndex !== index)
    }))
  }

  const refreshProviders = async (): Promise<StudioLlmProvider[]> => {
    const nextProviders = await listAdminProviders()
    setProviders(nextProviders)
    return nextProviders
  }

  const handleOpenEditProviderModal = (providerValue: string) => {
    const providerCode = normalizeProviderCode(providerValue)
    if (!providerCode) {
      toast.error('Provider 无效')
      return
    }
    const provider = providers.find((item) => normalizeProviderCode(item.code) === providerCode)
    if (!provider) {
      toast.error(`未找到 Provider：${providerCode}`)
      return
    }
    setProviderEditForm({
      code: providerCode,
      name: provider.name?.trim() ?? '',
      baseUrl: normalizeProviderBaseUrl(provider.baseUrl),
      modelIds: normalizeProviderModelIds(provider.modelIds)
    })
    setIsEditProviderOpen(true)
  }

  const handleDeleteProviderFromList = async (providerValue: string) => {
    if (isDeletingProvider) {
      return
    }
    const providerCode = normalizeProviderCode(providerValue)
    if (!providerCode) {
      toast.error('Provider 无效')
      return
    }
    const provider = providers.find((item) => normalizeProviderCode(item.code) === providerCode)
    if (!provider) {
      toast.error(`未找到 Provider：${providerCode}`)
      return
    }

    setIsDeletingProvider(true)
    try {
      await deleteAdminProvider(providerCode)
      const nextProviders = await refreshProviders()
      setCreateForm((prev) => {
        if (prev.provider !== providerCode) {
          return prev
        }
        const fallbackProvider = normalizeProviderCode(nextProviders[0]?.code) ?? DEFAULT_PROVIDER
        const fallbackModelIds = normalizeProviderModelIds(
          nextProviders.find((item) => normalizeProviderCode(item.code) === fallbackProvider)?.modelIds
        )
        const fallbackBaseUrl = normalizeProviderBaseUrl(
          nextProviders.find((item) => normalizeProviderCode(item.code) === fallbackProvider)?.baseUrl
        )
        return {
          ...prev,
          provider: fallbackProvider,
          modelId: fallbackModelIds[0] ?? '',
          baseUrl: fallbackBaseUrl
        }
      })

      if (normalizeProviderCode(providerEditForm.code) === providerCode) {
        setIsEditProviderOpen(false)
        setProviderEditForm(emptyProviderEditForm)
      }
      toast.success(`已删除 Provider：${providerCode}`)
    } catch (error) {
      const message = resolveErrorMessage(error)
      toast.error(`删除 Provider 失败：${message}`)
    } finally {
      setIsDeletingProvider(false)
    }
  }

  const handleCloseEditProviderModal = () => {
    if (isUpdatingProvider || isDeletingProvider) {
      return
    }
    setIsEditProviderOpen(false)
    setProviderEditForm(emptyProviderEditForm)
  }

  const handleCreateProvider = async () => {
    if (isCreatingProvider) {
      return
    }
    const providerCode = normalizeProviderCode(providerCreateForm.code)
    if (!providerCode) {
      toast.error('Provider code 不能为空')
      return
    }
    const providerName = providerCreateForm.name.trim()
    const baseUrl = providerCreateForm.baseUrl.trim()
    const createModelIds = normalizeProviderModelIds(providerCreateForm.modelIds)

    setIsCreatingProvider(true)
    try {
      await createAdminProvider({
        code: providerCode,
        name: providerName || undefined,
        baseUrl: baseUrl || undefined
      })
      if (createModelIds.length > 0) {
        await updateAdminProvider(providerCode, { addModelIds: createModelIds })
      }
      const nextProviders = await refreshProviders()
      const provider = nextProviders.find((item) => normalizeProviderCode(item.code) === providerCode)
      const nextModelId = normalizeProviderModelIds(provider?.modelIds)[0] ?? ''
      const nextBaseUrl = normalizeProviderBaseUrl(provider?.baseUrl)

      setCreateForm((prev) => ({
        ...prev,
        provider: providerCode,
        modelId: nextModelId,
        baseUrl: nextBaseUrl
      }))
      setIsCreateProviderOpen(false)
      setProviderCreateForm(emptyProviderCreateForm)
      toast.success(`已新增 Provider：${providerCode}`)
    } catch (error) {
      const message = resolveErrorMessage(error)
      toast.error(`新增 Provider 失败：${message}`)
    } finally {
      setIsCreatingProvider(false)
    }
  }

  const handleUpdateProvider = async () => {
    if (isUpdatingProvider) {
      return
    }
    const providerCode = normalizeProviderCode(providerEditForm.code)
    if (!providerCode) {
      toast.error('Provider 无效')
      return
    }
    const currentProvider = providers.find((item) => normalizeProviderCode(item.code) === providerCode)
    if (!currentProvider) {
      toast.error(`未找到 Provider：${providerCode}`)
      return
    }

    const currentName = currentProvider.name?.trim() ?? ''
    const currentBaseUrl = normalizeProviderBaseUrl(currentProvider.baseUrl)
    const currentModelIds = normalizeProviderModelIds(currentProvider.modelIds)

    const nextName = providerEditForm.name.trim()
    const nextBaseUrl = providerEditForm.baseUrl.trim()
    const nextModelIds = normalizeProviderModelIds(providerEditForm.modelIds)

    if (!nextName && currentName) {
      toast.error('Provider 名称不能为空')
      return
    }

    const addModelIds = nextModelIds.filter((modelId) => !currentModelIds.includes(modelId))
    const removeModelIds = currentModelIds.filter((modelId) => !nextModelIds.includes(modelId))

    const request: UpdateAdminLlmProviderRequest = {}
    if (nextName !== currentName && nextName.length > 0) {
      request.name = nextName
    }
    if (nextBaseUrl !== currentBaseUrl) {
      request.baseUrl = nextBaseUrl
    }
    if (addModelIds.length > 0) {
      request.addModelIds = addModelIds
    }
    if (removeModelIds.length > 0) {
      request.removeModelIds = removeModelIds
    }

    if (Object.keys(request).length === 0) {
      toast.message('未检测到任何变更')
      return
    }

    setIsUpdatingProvider(true)
    try {
      await updateAdminProvider(providerCode, request)
      const nextProviders = await refreshProviders()
      const updatedProvider = nextProviders.find((item) => normalizeProviderCode(item.code) === providerCode)
      const normalizedBaseUrl = normalizeProviderBaseUrl(updatedProvider?.baseUrl)
      const normalizedModelIds = normalizeProviderModelIds(updatedProvider?.modelIds)

      setCreateForm((prev) => {
        if (prev.provider !== providerCode) {
          return prev
        }
        return {
          ...prev,
          modelId: normalizedModelIds.includes(prev.modelId) ? prev.modelId : (normalizedModelIds[0] ?? ''),
          baseUrl: normalizedBaseUrl
        }
      })
      setProviderEditForm({
        code: providerCode,
        name: updatedProvider?.name?.trim() ?? nextName,
        baseUrl: normalizedBaseUrl,
        modelIds: normalizedModelIds
      })
      setIsEditProviderOpen(false)
      toast.success(`已更新 Provider：${providerCode}`)
    } catch (error) {
      const message = resolveErrorMessage(error)
      toast.error(`更新 Provider 失败：${message}`)
    } finally {
      setIsUpdatingProvider(false)
    }
  }

  const isCreateValid = createForm.modelId.trim().length > 0 && createForm.name.trim().length > 0 && createForm.provider && createForm.modelType

  const handleCloseCreateModal = () => {
    if (isCreatingModel) {
      return
    }
    setIsCreateOpen(false)
    setCreateForm(buildCreateForm())
    setIsCreateApiKeyVisible(false)
  }

  const handleCreateModel = async () => {
    if (!isCreateValid || isCreatingModel) {
      return
    }

    setIsCreatingModel(true)
    try {
      const createdModel = await createAdminModel({
        modelId: createForm.modelId.trim(),
        name: createForm.name.trim(),
        providerCode: createForm.provider,
        modelType: MODEL_TYPE_API_MAP[createForm.modelType],
        description: createForm.description.trim() || undefined,
        baseUrl: createForm.baseUrl.trim() || undefined,
        apiKey: createForm.apiKey.trim() || undefined
      })
      const createdRecord = mapAdminModelToRecord(createdModel)

      setModels((prev) => {
        const next = prev.filter((item) => item.id !== createdRecord.id)
        return [createdRecord, ...next]
      })
      if (createdRecord.hasApiKey) {
        setConnectedModelIds((prev) => connectModel(prev, createdRecord.id))
      }
      setIsCreateOpen(false)
      setCreateForm(buildCreateForm())
      setIsCreateApiKeyVisible(false)
      toast.success(`已添加模型：${createdRecord.name}`)
    } catch (error) {
      const message = resolveErrorMessage(error)
      toast.error(`添加模型失败：${message}`)
    } finally {
      setIsCreatingModel(false)
    }
  }

  const selectedModels = useMemo(
    () => models.filter((model) => selectedModelIds.includes(model.id)),
    [models, selectedModelIds]
  )
  const activeModel = useMemo(
    () => models.find((model) => model.id === activeModelId) ?? null,
    [activeModelId, models]
  )
  const isActiveModelConnected = activeModel
    ? connectedModelIds.includes(activeModel.id) || Boolean(activeModel.hasApiKey)
    : false

  return (
    <section className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      <aside className={`hidden @md:block ${panelWidthClassMap.mediumResponsive} flex-shrink-0 border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-5 @xl:pl-8 @xl:pr-6 pb-6 pt-8 overflow-y-auto custom-scrollbar`}>
        <FilterSection
          title="供应商 (Providers)"
          options={providerOptions}
          selected={filters.providers}
          onToggle={(value) => toggleFilter('providers', value as LlmProvider)}
          onAddOption={handleOpenCreateProviderModal}
          onEditOption={handleOpenEditProviderModal}
          onDeleteOption={(value) => {
            void handleDeleteProviderFromList(value)
          }}
          defaultOpen
        />
        <FilterSection
          title="模型类型 (Type)"
          options={filterTypeOptions}
          selected={filters.types}
          onToggle={(value) => toggleFilter('types', value as ModelCategory)}
          defaultOpen
        />
        <FilterSection
          title="上下文长度 (Context)"
          options={contextOptions}
          selected={filters.contexts}
          onToggle={(value) => toggleFilter('contexts', value)}
          defaultOpen
        />
      </aside>

      <main className="flex-1 overflow-y-auto custom-scrollbar pb-24">
        <div className={`mx-auto ${contentMaxWidthClassMap.extraWide} px-6 @md:px-8 py-8`}>
          <div className="flex flex-col @md:flex-row @md:items-center justify-between gap-4 mb-6">
            <h1 className="text-heading @md:text-title @xl:text-display font-black text-slate-900 dark:text-white tracking-tight">
              模型管理
            </h1>
            <div className="flex items-center gap-3">
              <div className="bg-slate-100 dark:bg-slate-800 p-1 rounded-lg flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => setViewMode('list')}
                  className={`p-1.5 rounded-md transition-all ${
                    viewMode === 'list'
                      ? 'bg-white dark:bg-slate-700 shadow-sm text-slate-900 dark:text-white'
                      : 'text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'
                  }`}
                >
                  <List size={iconSizeToken.small} />
                </button>
                <button
                  type="button"
                  onClick={() => setViewMode('grid')}
                  className={`p-1.5 rounded-md transition-all ${
                    viewMode === 'grid'
                      ? 'bg-white dark:bg-slate-700 shadow-sm text-slate-900 dark:text-white'
                      : 'text-slate-400 hover:text-slate-600 dark:hover:text-slate-200'
                  }`}
                >
                  <LayoutGrid size={iconSizeToken.small} />
                </button>
              </div>
              <Button
                size="header"
                className="shadow-lg shadow-slate-200/60 dark:shadow-none"
                onClick={() => setIsCreateOpen(true)}
              >
                <Plus size={iconSizeToken.small} />
                添加模型
              </Button>
            </div>
          </div>

          <div className="mb-6 relative">
            <div className="relative group">
              <Search
                className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-slate-600 transition-colors"
                size={16}
              />
              <input
                type="text"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="搜索模型 (Search models)..."
                className="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl py-2.5 pl-11 pr-10 text-body-sm text-slate-900 dark:text-slate-100 placeholder:text-caption placeholder:text-slate-500 focus:outline-none focus:border-indigo-500/60 focus:ring-1 focus:ring-indigo-500/20 transition-colors shadow-sm"
              />
              <div className="absolute right-3 top-1/2 -translate-y-1/2 px-2 py-1 bg-white dark:bg-slate-800 rounded border border-slate-200 dark:border-slate-700 text-micro text-slate-400 font-medium">
                /
              </div>
            </div>
          </div>

          <div className="flex justify-between items-center text-body-sm text-slate-500 dark:text-slate-400 mb-4">
            <span>{isLoadingModels ? '模型加载中...' : `${filteredModels.length} 个模型`}</span>
            <button type="button" className="flex items-center hover:text-slate-900 dark:hover:text-slate-200 transition-colors">
              最新发布 (Newest)
              <ArrowUpDown size={14} className="ml-1.5" />
            </button>
          </div>

          {isLoadingModels ? (
            <Card className="h-28 flex items-center justify-center text-slate-500 dark:text-slate-400">
              正在加载模型...
            </Card>
          ) : filteredModels.length === 0 ? (
            <Card className="h-28 flex items-center justify-center text-slate-500 dark:text-slate-400">
              暂无可用模型
            </Card>
          ) : viewMode === 'list' ? (
            <div className="space-y-2">
              {filteredModels.map((model) => {
                const isConnected = connectedModelIds.includes(model.id) || Boolean(model.hasApiKey)
                const isSelected = selectedModelIds.includes(model.id)
                const isSelectionDisabled = !isSelected && selectedModelIds.length >= MAX_COMPARE_MODELS
                return (
                  <ModelRow
                    key={model.id}
                    model={model}
                    isSelected={isSelected}
                    isSelectionDisabled={isSelectionDisabled}
                    isActive={activeModelId === model.id}
                    isConnected={isConnected}
                    onToggle={() => toggleSelection(model.id)}
                    onSelect={() => handleModelSelect(model.id, isConnected)}
                    onManage={() => openDrawer(model.id, isConnected ? 'playground' : 'config')}
                  />
                )
              })}
            </div>
          ) : (
            <div className="grid grid-cols-1 @md:grid-cols-2 @xl:grid-cols-3 gap-4 pb-4">
              {filteredModels.map((model) => {
                const isConnected = connectedModelIds.includes(model.id) || Boolean(model.hasApiKey)
                const isSelected = selectedModelIds.includes(model.id)
                const isSelectionDisabled = !isSelected && selectedModelIds.length >= MAX_COMPARE_MODELS
                return (
                  <ModelCard
                    key={model.id}
                    model={model}
                    isSelected={isSelected}
                    isSelectionDisabled={isSelectionDisabled}
                    isActive={activeModelId === model.id}
                    isConnected={isConnected}
                    onToggle={() => toggleSelection(model.id)}
                    onSelect={() => handleModelSelect(model.id, isConnected)}
                    onManage={() => openDrawer(model.id, isConnected ? 'playground' : 'config')}
                  />
                )
              })}
            </div>
          )}

          <div className="pt-8 pb-12 flex justify-center">
            <button
              type="button"
              className="px-6 py-2.5 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-full text-body-sm font-medium text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-slate-100 hover:border-slate-300 dark:hover:border-slate-600 transition-all shadow-sm"
            >
              加载更多模型
            </button>
          </div>
        </div>
      </main>

      {selectedModelIds.length > 0 && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 animate-in slide-in-from-bottom-4 duration-300">
          <div className="flex items-center bg-slate-900 text-white rounded-full px-2.5 py-1.5 shadow-2xl border border-slate-800 ring-1 ring-white/10">
            <div className="pl-3 pr-2 text-caption font-medium flex items-center">
              <span className="bg-slate-800 text-white w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold mr-2">
                {selectedModelIds.length}
              </span>
              已选
            </div>
            <div className="h-4 w-px bg-slate-700 mx-1" />
            <button
              type="button"
              onClick={() => setSelectedModelIds([])}
              className="px-2 py-0.5 text-caption text-slate-400 hover:text-white transition-colors font-medium"
            >
              清空
            </button>
            <button
              type="button"
              onClick={openCompare}
              className="ml-2 px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white text-caption font-semibold rounded-full transition-all shadow-lg shadow-indigo-900/40 flex items-center"
            >
              <SlidersHorizontal size={12} className="mr-2" />
              开始对比
            </button>
          </div>
        </div>
      )}

      <CompareModal isOpen={isCompareOpen} onClose={() => setIsCompareOpen(false)} models={selectedModels} />
      <Modal
        isOpen={isCreateOpen}
        onClose={handleCloseCreateModal}
        size="sm"
        title="添加模型"
        footerRight={
          <>
            <ModalCancelButton onClick={handleCloseCreateModal} disabled={isCreatingModel} />
            <ModalPrimaryButton onClick={handleCreateModel} disabled={!isCreateValid || isCreatingModel} loading={isCreatingModel}>
              确认添加
            </ModalPrimaryButton>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              模型名称 <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={createForm.name}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, name: event.target.value }))}
              placeholder="如：GPT-4o"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>

          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Base URL
            </label>
            <input
              type="text"
              value={createForm.baseUrl}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, baseUrl: event.target.value }))}
              placeholder="https://api.example.com/v1"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>

          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              API Key（可选）
            </label>
            <div className="relative">
              <input
                type={isCreateApiKeyVisible ? 'text' : 'password'}
                value={createForm.apiKey}
                onChange={(event) => setCreateForm((prev) => ({ ...prev, apiKey: event.target.value }))}
                placeholder="sk-..."
                className="w-full pl-3 pr-10 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
              />
              <Button
                type="button"
                onClick={() => setIsCreateApiKeyVisible((prev) => !prev)}
                variant="ghost"
                size="tiny"
                className="absolute inset-y-0 right-0 px-3 flex items-center text-slate-400 dark:text-slate-500 hover:text-indigo-500 transition-colors"
                aria-label={isCreateApiKeyVisible ? '隐藏 API Key' : '显示 API Key'}
              >
                {isCreateApiKeyVisible ? <EyeOff size={14} /> : <Eye size={14} />}
              </Button>
            </div>
          </div>

          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Provider <span className="text-rose-500">*</span>
            </label>
            <Select
              value={createForm.provider}
              onChange={(value) => handleProviderChange(value as LlmProvider)}
              options={providerOptions.map((opt) => ({ value: opt.value, label: opt.label }))}
              dropdownHeader="选择供应商"
              size="sm"
            />
          </div>

          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              模型 ID <span className="text-rose-500">*</span>
            </label>
            <Select
              value={createForm.modelId}
              onChange={(value) => setCreateForm((prev) => ({ ...prev, modelId: value }))}
              options={modelIdOptions}
              placeholder={modelIdOptions.length > 0 ? '请选择模型 ID' : '该 Provider 暂无可选 modelIds'}
              dropdownHeader="选择模型 ID"
              size="sm"
            />
          </div>

          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              模型类型 <span className="text-rose-500">*</span>
            </label>
            <Select
              value={createForm.modelType}
              onChange={(value) => setCreateForm((prev) => ({ ...prev, modelType: value as ModelCategory }))}
              options={MODEL_TYPE_OPTIONS.map((opt) => ({ value: opt.value, label: opt.label }))}
              dropdownHeader="选择模型类型"
              size="sm"
            />
          </div>

          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              描述
            </label>
            <textarea
              value={createForm.description}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, description: event.target.value }))}
              placeholder="模型描述（可选）"
              className="w-full min-h-[88px] px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600 resize-none"
            />
          </div>
        </div>
      </Modal>

      <Modal
        isOpen={isCreateProviderOpen}
        onClose={handleCloseCreateProviderModal}
        size="sm"
        title="新增供应商 (Provider)"
        footerRight={
          <>
            <ModalCancelButton onClick={handleCloseCreateProviderModal} disabled={isCreatingProvider} />
            <ModalPrimaryButton
              onClick={handleCreateProvider}
              disabled={!normalizeProviderCode(providerCreateForm.code)}
              loading={isCreatingProvider}
            >
              确认新增
            </ModalPrimaryButton>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Provider Code <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={providerCreateForm.code}
              onChange={(event) => setProviderCreateForm((prev) => ({ ...prev, code: event.target.value }))}
              placeholder="如：openai"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Provider 名称
            </label>
            <input
              type="text"
              value={providerCreateForm.name}
              onChange={(event) => setProviderCreateForm((prev) => ({ ...prev, name: event.target.value }))}
              placeholder="如：OpenAI"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Base URL
            </label>
            <input
              type="text"
              value={providerCreateForm.baseUrl}
              onChange={(event) => setProviderCreateForm((prev) => ({ ...prev, baseUrl: event.target.value }))}
              placeholder="https://api.example.com/v1"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="block text-caption font-medium text-slate-700 dark:text-slate-300">支持的模型ID</label>
              <button
                type="button"
                onClick={handleAddCreateProviderModelId}
                className="text-caption text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300"
              >
                + 添加
              </button>
            </div>
            {providerCreateForm.modelIds.length === 0 ? (
              <div className="py-3 text-center text-caption text-slate-400 border border-dashed border-slate-200 dark:border-slate-700 rounded-xl">
                暂无模型 ID
              </div>
            ) : (
              <div className="space-y-1.5 max-h-40 overflow-y-auto custom-scrollbar">
                {providerCreateForm.modelIds.map((modelId, index) => (
                  <div key={`create-provider-model-${index}`} className="flex items-center gap-1.5">
                    <input
                      type="text"
                      value={modelId}
                      onChange={(event) => handleChangeCreateProviderModelId(index, event.target.value)}
                      placeholder="如：gpt-4o-mini"
                      className="flex-1 min-w-0 px-2.5 py-2 text-body-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-600 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500/20 focus:border-blue-500"
                    />
                    <button
                      type="button"
                      onClick={() => handleRemoveCreateProviderModelId(index)}
                      className="p-1.5 text-slate-300 hover:text-rose-500 rounded transition-colors flex-shrink-0"
                      title="删除模型 ID"
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </Modal>

      <Modal
        isOpen={isEditProviderOpen}
        onClose={handleCloseEditProviderModal}
        size="sm"
        title={`编辑供应商：${providerEditForm.code || '-'}`}
        footerRight={
          <>
            <ModalCancelButton onClick={handleCloseEditProviderModal} disabled={isUpdatingProvider || isDeletingProvider} />
            <ModalPrimaryButton
              onClick={handleUpdateProvider}
              disabled={!normalizeProviderCode(providerEditForm.code) || isDeletingProvider}
              loading={isUpdatingProvider}
            >
              保存更新
            </ModalPrimaryButton>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Provider Code
            </label>
            <input
              type="text"
              value={providerEditForm.code}
              disabled
              className="w-full px-3 py-2 text-body-sm text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700 rounded-xl bg-slate-50 dark:bg-slate-800/70 cursor-not-allowed"
            />
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Provider 名称
            </label>
            <input
              type="text"
              value={providerEditForm.name}
              onChange={(event) => setProviderEditForm((prev) => ({ ...prev, name: event.target.value }))}
              placeholder="如：OpenAI"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
          <div>
            <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Base URL
            </label>
            <input
              type="text"
              value={providerEditForm.baseUrl}
              onChange={(event) => setProviderEditForm((prev) => ({ ...prev, baseUrl: event.target.value }))}
              placeholder="https://api.example.com/v1"
              className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
          </div>
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <label className="block text-caption font-medium text-slate-700 dark:text-slate-300">支持的模型ID</label>
              <button
                type="button"
                onClick={handleAddEditProviderModelId}
                className="text-caption text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300"
              >
                + 添加
              </button>
            </div>
            {providerEditForm.modelIds.length === 0 ? (
              <div className="py-3 text-center text-caption text-slate-400 border border-dashed border-slate-200 dark:border-slate-700 rounded-xl">
                暂无模型 ID
              </div>
            ) : (
              <div className="space-y-1.5 max-h-40 overflow-y-auto custom-scrollbar">
                {providerEditForm.modelIds.map((modelId, index) => (
                  <div key={`edit-provider-model-${index}`} className="flex items-center gap-1.5">
                    <input
                      type="text"
                      value={modelId}
                      onChange={(event) => handleChangeEditProviderModelId(index, event.target.value)}
                      placeholder="如：gpt-4o-mini"
                      className="flex-1 min-w-0 px-2.5 py-2 text-body-sm text-slate-800 dark:text-slate-200 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-600 rounded-lg focus:outline-none focus:ring-1 focus:ring-blue-500/20 focus:border-blue-500"
                    />
                    <button
                      type="button"
                      onClick={() => handleRemoveEditProviderModelId(index)}
                      className="p-1.5 text-slate-300 hover:text-rose-500 rounded transition-colors flex-shrink-0"
                      title="删除模型 ID"
                    >
                      <Trash2 size={12} />
                    </button>
                  </div>
                ))}
              </div>
            )}
            <p className="mt-1 text-micro text-slate-400">保存时会自动计算新增和移除的 modelIds。</p>
          </div>
        </div>
      </Modal>

      {activeModel &&
        createPortal(
          <ModelDrawer
            key={`${activeModel.id}-${drawerTab}-${isActiveModelConnected ? '1' : '0'}`}
            model={activeModel}
            isConnected={isActiveModelConnected}
            defaultTab={drawerTab}
            onClose={() => setActiveModelId(null)}
            onConnect={handleConnectModel}
          />,
          document.body
        )}
    </section>
  )
}

function CompareModal({
  isOpen,
  onClose,
  models
}: {
  isOpen: boolean
  onClose: () => void
  models: ModelRecord[]
}) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="模型对比 (Compare Models)"
      subtitle={<span className="text-body-sm text-slate-500">正在对比 {models.length} 个模型</span>}
      size="xl"
    >
      <Card padding="none" variant="default" className="overflow-hidden shadow-sm">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr>
              <th className="p-4 w-48 bg-slate-50 dark:bg-slate-800/60 border-b border-r border-slate-100 dark:border-slate-800 sticky left-0 z-20 text-micro font-semibold text-slate-400 uppercase tracking-wider">
                模型属性 (Attributes)
              </th>
              {models.map((model) => (
                <th
                  key={model.id}
                  className={`p-6 border-b border-slate-100 dark:border-slate-800 ${tableColumnWidthClassMap['6xl']} bg-white dark:bg-slate-900 align-top`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <div className="font-semibold text-body text-slate-900 dark:text-slate-100">{model.name}</div>
                    {model.isNew && (
                      <span className={`${TYPOGRAPHY.nano} bg-blue-50 text-blue-600 px-1.5 py-0.5 rounded font-semibold uppercase`}>
                        NEW
                      </span>
                    )}
                  </div>
                  <div className="text-caption text-slate-500 font-medium bg-slate-100 dark:bg-slate-800 px-2 py-1 rounded w-fit mb-3">
                    {getProviderLabel(model)}
                  </div>
                  <div className="text-micro text-slate-400 font-mono break-all">{model.id}</div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
            <tr>
              <td className="p-4 bg-slate-50 dark:bg-slate-800/60 border-r border-slate-100 dark:border-slate-800 font-medium text-body-sm text-slate-600 sticky left-0">
                上下文窗口 (Context)
              </td>
              {models.map((model) => (
                <td key={model.id} className="p-4 text-body-sm font-mono text-slate-800 dark:text-slate-200">
                  <div className="flex items-center">
                    <Maximize2 size={14} className="mr-2 text-slate-400" />
                    {model.stats.context}
                  </div>
                </td>
              ))}
            </tr>
            <tr>
              <td className="p-4 bg-slate-50 dark:bg-slate-800/60 border-r border-slate-100 dark:border-slate-800 font-medium text-body-sm text-slate-600 sticky left-0">
                定价 (Pricing)
              </td>
              {models.map((model) => (
                <td key={model.id} className="p-4 text-body-sm font-mono text-slate-800 dark:text-slate-200">
                  <div className="flex flex-col space-y-1">
                    <div className="flex items-center text-slate-900 dark:text-slate-100 font-semibold">
                      <span className="w-16 text-caption text-slate-400 font-normal">Input</span>
                      {model.stats.inputPrice}
                    </div>
                    <div className="flex items-center text-slate-900 dark:text-slate-100 font-semibold">
                      <span className="w-16 text-caption text-slate-400 font-normal">Output</span>
                      {model.stats.outputPrice}
                    </div>
                  </div>
                </td>
              ))}
            </tr>
            <tr>
              <td className="p-4 bg-slate-50 dark:bg-slate-800/60 border-r border-slate-100 dark:border-slate-800 font-medium text-body-sm text-slate-600 sticky left-0">
                参数量 (Params)
              </td>
              {models.map((model) => (
                <td key={model.id} className="p-4 text-body-sm text-slate-800 dark:text-slate-200">
                  {model.stats.params ? (
                    <span className="bg-purple-50 text-purple-700 px-2 py-1 rounded text-caption font-semibold border border-purple-100">
                      {model.stats.params}
                    </span>
                  ) : (
                    <span className="text-slate-400 italic text-caption">Proprietary / Unknown</span>
                  )}
                </td>
              ))}
            </tr>
            <tr>
              <td className="p-4 bg-slate-50 dark:bg-slate-800/60 border-r border-slate-100 dark:border-slate-800 font-medium text-body-sm text-slate-600 sticky left-0">
                能力标签 (Tags)
              </td>
              {models.map((model) => (
                <td key={model.id} className="p-4">
                  <div className="flex flex-wrap gap-2">
                    {model.tags.map((tag) => (
                      <span
                        key={tag}
                        className="px-2 py-1 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded text-caption text-slate-600 dark:text-slate-300 capitalize"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                </td>
              ))}
            </tr>
            <tr>
              <td className="p-4 bg-slate-50 dark:bg-slate-800/60 border-r border-slate-100 dark:border-slate-800 font-medium text-body-sm text-slate-600 sticky left-0 align-top">
                描述 (Description)
              </td>
              {models.map((model) => (
                <td key={model.id} className="p-4 text-caption text-slate-500 dark:text-slate-400 leading-relaxed align-top">
                  {model.description}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </Card>
    </Modal>
  )
}

type ChatMessage = {
  id: string
  role: 'assistant' | 'user'
  content: string
  reasoning?: string
  thinkingEnabled?: boolean
}

type PendingIndicatorPhase = 'hidden' | 'entering' | 'visible' | 'leaving'

function buildWelcomeMessage(model: ModelRecord): ChatMessage {
  return {
    id: 'welcome',
    role: 'assistant',
    content: `你好！我是 ${model.name}，你可以在这里测试我的能力。`
  }
}

function ModelDrawer({
  model,
  isConnected,
  defaultTab,
  onClose,
  onConnect
}: {
  model: ModelRecord
  isConnected: boolean
  defaultTab: 'config' | 'playground'
  onClose: () => void
  onConnect: (model: ModelRecord, request: { apiKey: string; baseUrl?: string }) => Promise<boolean>
}) {
  const [activeTab, setActiveTab] = useState<'config' | 'playground'>(isConnected ? defaultTab : 'config')
  const [isEditing, setIsEditing] = useState(false)
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState(model.baseUrl ?? '')
  const [isConnecting, setIsConnecting] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([buildWelcomeMessage(model)])
  const [collapsedReasoningMessageIds, setCollapsedReasoningMessageIds] = useState<string[]>([])
  const [draft, setDraft] = useState('')
  const [temperature, setTemperature] = useState(0.7)
  const [topP, setTopP] = useState(0.9)
  const [thinkingEnabled, setThinkingEnabled] = useState(false)
  const [systemInstruction, setSystemInstruction] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [streamingAssistantMessageId, setStreamingAssistantMessageId] = useState<string | null>(null)
  const [pendingAssistantMessageId, setPendingAssistantMessageId] = useState<string | null>(null)
  const [pendingIndicatorPhase, setPendingIndicatorPhase] = useState<PendingIndicatorPhase>('hidden')
  const streamCancelRef = useRef<(() => void) | null>(null)
  const messageEndRef = useRef<HTMLDivElement | null>(null)
  const pendingAssistantMessageIdRef = useRef<string | null>(null)
  const pendingIndicatorPhaseRef = useRef<PendingIndicatorPhase>('hidden')
  const pendingShownAtRef = useRef(0)
  const pendingEnterTimerRef = useRef<number | null>(null)
  const pendingMinDurationTimerRef = useRef<number | null>(null)
  const pendingLeaveTimerRef = useRef<number | null>(null)

  const updatePendingIndicatorPhase = (phase: PendingIndicatorPhase) => {
    pendingIndicatorPhaseRef.current = phase
    setPendingIndicatorPhase(phase)
  }

  const clearPendingIndicatorTimers = () => {
    if (pendingEnterTimerRef.current) {
      window.clearTimeout(pendingEnterTimerRef.current)
      pendingEnterTimerRef.current = null
    }
    if (pendingMinDurationTimerRef.current) {
      window.clearTimeout(pendingMinDurationTimerRef.current)
      pendingMinDurationTimerRef.current = null
    }
    if (pendingLeaveTimerRef.current) {
      window.clearTimeout(pendingLeaveTimerRef.current)
      pendingLeaveTimerRef.current = null
    }
  }

  const resetPendingIndicator = () => {
    clearPendingIndicatorTimers()
    pendingShownAtRef.current = 0
    pendingAssistantMessageIdRef.current = null
    setPendingAssistantMessageId(null)
    updatePendingIndicatorPhase('hidden')
  }

  const startPendingIndicator = (assistantMessageId: string) => {
    clearPendingIndicatorTimers()
    pendingShownAtRef.current = Date.now()
    pendingAssistantMessageIdRef.current = assistantMessageId
    setPendingAssistantMessageId(assistantMessageId)
    updatePendingIndicatorPhase('entering')
    pendingEnterTimerRef.current = window.setTimeout(() => {
      if (pendingAssistantMessageIdRef.current !== assistantMessageId) {
        return
      }
      updatePendingIndicatorPhase('visible')
      pendingEnterTimerRef.current = null
    }, 120)
  }

  const hidePendingIndicator = (assistantMessageId: string, force = false) => {
    if (pendingAssistantMessageIdRef.current !== assistantMessageId) {
      return
    }
    if (pendingIndicatorPhaseRef.current === 'hidden' || pendingIndicatorPhaseRef.current === 'leaving') {
      return
    }

    const leave = () => {
      if (pendingAssistantMessageIdRef.current !== assistantMessageId) {
        return
      }
      updatePendingIndicatorPhase('leaving')
      pendingLeaveTimerRef.current = window.setTimeout(() => {
        if (pendingAssistantMessageIdRef.current !== assistantMessageId) {
          return
        }
        pendingAssistantMessageIdRef.current = null
        setPendingAssistantMessageId(null)
        updatePendingIndicatorPhase('hidden')
        pendingShownAtRef.current = 0
        pendingLeaveTimerRef.current = null
      }, 100)
    }

    clearPendingIndicatorTimers()
    if (force) {
      leave()
      return
    }

    const elapsed = Date.now() - pendingShownAtRef.current
    const remaining = Math.max(0, 250 - elapsed)
    if (remaining <= 0) {
      leave()
      return
    }
    pendingMinDurationTimerRef.current = window.setTimeout(() => {
      leave()
      pendingMinDurationTimerRef.current = null
    }, remaining)
  }

  const cancelStreaming = () => {
    streamCancelRef.current?.()
    streamCancelRef.current = null
    resetPendingIndicator()
    setIsStreaming(false)
    setStreamingAssistantMessageId(null)
  }

  useEffect(() => {
    return () => {
      streamCancelRef.current?.()
      streamCancelRef.current = null
      clearPendingIndicatorTimers()
    }
  }, [])

  useEffect(() => {
    if (activeTab !== 'playground') {
      return
    }
    let rafId = 0
    rafId = window.requestAnimationFrame(() => {
      messageEndRef.current?.scrollIntoView({ block: 'end', behavior: 'auto' })
    })
    return () => {
      if (rafId) {
        window.cancelAnimationFrame(rafId)
      }
    }
  }, [activeTab, messages])

  const handleSend = () => {
    const trimmed = draft.trim()
    if (!trimmed || isStreaming) return
    cancelStreaming()

    const userMessageId = `user-${Date.now()}`
    const assistantMessageId = `assistant-${Date.now()}`
    const assistantThinkingEnabled = thinkingEnabled
    setMessages((prev) => [
      ...prev,
      {
        id: userMessageId,
        role: 'user',
        content: trimmed
      },
      {
        id: assistantMessageId,
        role: 'assistant',
        content: '',
        reasoning: assistantThinkingEnabled ? '' : undefined,
        thinkingEnabled: assistantThinkingEnabled
      }
    ])
    setDraft('')

    setIsStreaming(true)
    setStreamingAssistantMessageId(assistantMessageId)
    startPendingIndicator(assistantMessageId)
    streamCancelRef.current = createLlmPlaygroundStream(
      {
        providerCode: model.provider,
        modelId: model.id,
        message: trimmed,
        modelConfig: {
          temperature,
          topP,
          thinkingEnabled: assistantThinkingEnabled,
          systemInstruction: systemInstruction.trim()
        }
      },
      {
        onDelta: (delta) => {
          hidePendingIndicator(assistantMessageId)
          setMessages((prev) =>
            prev.map((message) =>
              message.id === assistantMessageId
                ? { ...message, content: `${message.content}${delta}` }
                : message
            )
          )
        },
        onReasoningDelta: (delta) => {
          if (!assistantThinkingEnabled) {
            return
          }
          hidePendingIndicator(assistantMessageId)
          setMessages((prev) =>
            prev.map((message) =>
              message.id === assistantMessageId
                ? { ...message, reasoning: `${message.reasoning ?? ''}${delta}` }
                : message
            )
          )
        },
        onDone: (event) => {
          hidePendingIndicator(assistantMessageId)
          setMessages((prev) =>
            prev.map((item) => {
              if (item.id !== assistantMessageId) {
                return item
              }

              const nextContent = item.content.trim()
                ? item.content
                : (event.content.trim() ? event.content : item.content)
              const nextReasoning = assistantThinkingEnabled
                ? (item.reasoning?.trim()
                  ? item.reasoning
                  : (event.reasoning?.trim() ? event.reasoning : item.reasoning))
                : item.reasoning

              if (nextContent === item.content && nextReasoning === item.reasoning) {
                return item
              }

              return {
                ...item,
                content: nextContent,
                reasoning: nextReasoning
              }
            })
          )
          streamCancelRef.current = null
          setIsStreaming(false)
          setStreamingAssistantMessageId(null)
        },
        onError: (message) => {
          hidePendingIndicator(assistantMessageId, true)
          setMessages((prev) =>
            prev.map((item) =>
              item.id === assistantMessageId && !item.content.trim()
                ? { ...item, content: '模型暂时无响应，请稍后重试。' }
                : item
            )
          )
          toast.error(`Playground 调用失败：${message}`)
          streamCancelRef.current = null
          setIsStreaming(false)
          setStreamingAssistantMessageId(null)
        }
      }
    )
  }

  const handleReset = () => {
    cancelStreaming()
    setMessages([buildWelcomeMessage(model)])
    setCollapsedReasoningMessageIds([])
    setDraft('')
  }

  const toggleReasoningCollapse = (messageId: string) => {
    setCollapsedReasoningMessageIds((prev) => (
      prev.includes(messageId)
        ? prev.filter((id) => id !== messageId)
        : [...prev, messageId]
    ))
  }

  const handleConnect = async () => {
    if (!apiKey.trim() || isConnecting) return
    setIsConnecting(true)
    const connected = await onConnect(model, {
      apiKey: apiKey.trim(),
      baseUrl: baseUrl.trim() || undefined
    })
    setIsConnecting(false)
    if (!connected) {
      return
    }
    setApiKey('')
    setIsEditing(false)
    setActiveTab('playground')
  }

  return (
    <aside className={`fixed right-0 top-14 bottom-0 z-40 ${drawerWidthClassMap.wide} bg-white dark:bg-slate-900 border-l border-slate-200 dark:border-slate-800 shadow-2xl flex flex-col animate-in slide-in-from-right duration-500`}>
      <div className="h-14 px-5 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between bg-white dark:bg-slate-900 flex-shrink-0">
        <div className="flex items-center gap-3 min-w-0">
          <div className="size-9 rounded-xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-300 flex items-center justify-center flex-shrink-0">
            <Bot size={18} />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-body-sm font-semibold text-slate-900 dark:text-slate-100 truncate">{model.name}</span>
              <span
                className={`px-2 py-0.5 text-micro font-semibold rounded-full border ${
                  isConnected
                    ? 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20'
                    : 'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-500/10 dark:text-amber-200 dark:border-amber-500/20'
                }`}
              >
                {isConnected ? 'READY' : 'SETUP REQUIRED'}
              </span>
            </div>
            <div className="text-micro text-slate-400 font-mono truncate">{model.id}</div>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex bg-slate-100 dark:bg-slate-800 p-1 rounded-lg border border-slate-200 dark:border-slate-700">
            <button
              type="button"
              onClick={() => setActiveTab('config')}
              className={`px-3 py-1.5 text-micro font-semibold rounded-md transition-all flex items-center ${
                activeTab === 'config'
                  ? 'bg-white dark:bg-slate-700 text-slate-900 dark:text-white shadow-sm'
                  : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
              }`}
            >
              <Key size={12} className="mr-1.5" />
              Connect
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('playground')}
              disabled={!isConnected}
              className={`px-3 py-1.5 text-micro font-semibold rounded-md transition-all flex items-center ${
                activeTab === 'playground'
                  ? 'bg-white dark:bg-slate-700 text-slate-900 dark:text-white shadow-sm'
                  : 'text-slate-400'
              } ${!isConnected ? 'cursor-not-allowed' : 'hover:text-slate-700 dark:hover:text-slate-200'}`}
            >
              <Play size={12} className="mr-1.5 fill-current" />
              Playground
            </button>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors"
            aria-label="关闭模型对话"
          >
            <X size={18} />
          </button>
        </div>
      </div>

      {activeTab === 'config' ? (
        <div className="flex-1 min-h-0 overflow-y-auto bg-white dark:bg-slate-900 flex items-center justify-center p-6 @md:p-8">
          <div className={`w-full ${cardWidthClassMap.half} @md:${cardWidthClassMap.medium}`}>
            <div className="text-center mb-8">
              <div className="w-14 h-14 mx-auto rounded-2xl flex items-center justify-center mb-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 shadow-sm">
                <Key size={22} className="text-slate-400" />
              </div>
              <h2 className="text-subtitle @md:text-title font-black text-slate-900 dark:text-white">
                {isConnected ? `${getProviderLabel(model)} 已连接` : `Connect to ${getProviderLabel(model)}`}
              </h2>
              <p className="text-caption @md:text-body-sm text-slate-500 dark:text-slate-400 mt-2.5 max-w-sm mx-auto">
                {isConnected
                  ? 'API Key 已连接，可直接进入 Playground 测试模型。'
                  : `输入 API Key 以连接 ${getProviderLabel(model)} 并启用 ${model.name}。`}
              </p>
            </div>

            <div className={`bg-white dark:bg-slate-900 p-3.5 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm space-y-2.5 ${cardWidthClassMap.narrow} mx-auto`}>
              {isConnected && !isEditing ? (
                <div className="space-y-2.5">
                  <div className="p-4 bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-100 dark:border-emerald-500/20 rounded-xl flex items-start">
                    <ShieldCheck size={18} className="text-emerald-600 dark:text-emerald-300 mt-0.5 mr-3 flex-shrink-0" />
                    <div>
                      <h4 className="text-body-sm font-semibold text-emerald-800 dark:text-emerald-200">连接已生效</h4>
                      <p className="text-caption text-emerald-600/90 dark:text-emerald-200/80 mt-1">
                        凭证已安全保存，可直接进入 Playground。
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => setActiveTab('playground')}
                      className="flex-1 h-9 bg-slate-900 hover:bg-slate-800 dark:bg-indigo-600 dark:hover:bg-indigo-500 text-white text-caption font-semibold rounded-xl transition-all shadow-md flex items-center justify-center"
                    >
                      <Play size={14} className="mr-2 fill-current" />
                      Playground
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setBaseUrl(model.baseUrl ?? '')
                        setApiKey('')
                        setIsEditing(true)
                      }}
                      className="h-9 w-9 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-500 hover:text-slate-700 dark:hover:text-slate-200 hover:border-indigo-300 transition-colors flex items-center justify-center"
                      aria-label="修改配置"
                    >
                      <Settings2 size={16} />
                    </button>
                  </div>
                </div>
              ) : (
                <div className="space-y-2.5">
                  <div>
                    <label className={`block ${TYPOGRAPHY.micro} font-bold text-slate-500 uppercase tracking-wide mb-1`}>
                      API Key <span className="text-rose-500">*</span>
                    </label>
                    <div className="relative group">
                      <Key size={12} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" />
                      <input
                        type="password"
                        value={apiKey}
                        onChange={(event) => setApiKey(event.target.value)}
                        placeholder={model.maskedApiKey
                          ? `${model.maskedApiKey}（输入新 Key 覆盖）`
                          : `sk-... (${getProviderLabel(model)} Key)`}
                        className="w-full h-9 pl-8 pr-3 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-caption text-slate-700 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-500 transition-all font-mono"
                        autoFocus
                      />
                    </div>
                  </div>

                  <div>
                    <label className={`block ${TYPOGRAPHY.micro} font-bold text-slate-500 uppercase tracking-wide mb-1`}>
                      Base URL <span className="text-slate-400 font-normal normal-case ml-1">(optional)</span>
                    </label>
                    <div className="relative group">
                      <Globe size={12} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" />
                      <input
                        type="text"
                        value={baseUrl}
                        onChange={(event) => setBaseUrl(event.target.value)}
                        placeholder="https://api.example.com/v1"
                        className="w-full h-9 pl-8 pr-3 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-caption text-slate-700 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-500 transition-all font-mono"
                      />
                    </div>
                  </div>

                  <div className="pt-0.5">
                    <button
                      type="button"
                      onClick={() => {
                        void handleConnect()
                      }}
                      disabled={!apiKey.trim() || isConnecting}
                      className="w-full h-9 bg-indigo-400 hover:bg-indigo-500 text-white text-caption font-semibold rounded-xl transition-all shadow-sm flex items-center justify-center disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {isConnecting ? 'Connecting...' : 'Save & Connect'}
                      <ArrowRight size={16} className="ml-2" />
                    </button>
                    <p className={`${TYPOGRAPHY.nano} text-center text-slate-400 mt-2 flex items-center justify-center`}>
                      <ShieldCheck size={10} className="mr-1" /> Keys are encrypted at rest.
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      ) : (
        <div className="flex-1 min-h-0 flex">
          <div className="flex-1 min-w-0 flex flex-col">
            <div className="flex-1 min-h-0 overflow-y-auto p-5 space-y-4">
              {messages.map((message) => {
                if (message.role === 'assistant') {
                  const allowReasoningUi = message.thinkingEnabled === true
                  const reasoningContent = (message.reasoning ?? '').trim()
                  const hasReasoning = reasoningContent.length > 0
                  const isReasoningCollapsed = hasReasoning && collapsedReasoningMessageIds.includes(message.id)
                  const isCurrentStreamingAssistant = isStreaming && message.id === streamingAssistantMessageId
                  const isReasoningStreaming = allowReasoningUi && isCurrentStreamingAssistant
                  const shouldShowReasoningBlock = allowReasoningUi && (hasReasoning || isReasoningStreaming)
                  const isPendingIndicatorVisible = pendingAssistantMessageId === message.id && pendingIndicatorPhase !== 'hidden'
                  const hasContent = Boolean(message.content)
                  const shouldHoldContentForPending = isPendingIndicatorVisible && !shouldShowReasoningBlock && hasContent
                  const shouldShowContent = hasContent && !shouldHoldContentForPending
                  const shouldShowPendingBlock = isPendingIndicatorVisible && !shouldShowReasoningBlock && !shouldShowContent
                  const pendingContainerAnimationClass = pendingIndicatorPhase === 'entering'
                    ? 'animate-llm-pending-enter'
                    : (pendingIndicatorPhase === 'leaving' ? 'animate-llm-pending-leave' : '')

                  return (
                    <div key={message.id} className="flex justify-start">
                      <div className="mr-3 mt-1 size-7 rounded-full bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-300 flex items-center justify-center flex-shrink-0">
                        <Bot size={14} />
                      </div>
                      <div className="max-w-[70%] flex flex-col">
                        {shouldShowReasoningBlock && (
                          <div className={`rounded-xl border border-slate-200/80 dark:border-slate-600/70 bg-slate-50/80 dark:bg-slate-900/60 px-3 py-2 ${
                            shouldShowContent ? 'rounded-b-none' : ''
                          }`}>
                            {hasReasoning ? (
                              <button
                                type="button"
                                onClick={() => toggleReasoningCollapse(message.id)}
                                className="w-full flex items-center justify-between gap-2 text-left text-micro font-semibold text-slate-500 dark:text-slate-300"
                              >
                                <span className="uppercase tracking-wide">思考过程</span>
                                {isReasoningCollapsed ? <ChevronRight size={12} /> : <ChevronDown size={12} />}
                              </button>
                            ) : (
                              <div className="text-micro font-semibold text-slate-500 dark:text-slate-300 uppercase tracking-wide">思考过程</div>
                            )}
                            {hasReasoning ? (
                              !isReasoningCollapsed && (
                                <div className="mt-2 pt-2 border-t border-slate-200/70 dark:border-slate-700 whitespace-pre-wrap text-caption text-slate-600 dark:text-slate-300 leading-relaxed">
                                  {reasoningContent}
                                </div>
                              )
                            ) : (
                              <div className="mt-2 pt-2 border-t border-slate-200/70 dark:border-slate-700 text-caption text-slate-500 dark:text-slate-400">
                                思考中...
                              </div>
                            )}
                          </div>
                        )}
                        {shouldShowContent && (
                          <div className={`rounded-2xl px-4 py-3 text-body-sm shadow-sm bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 text-slate-700 dark:text-slate-200 ${
                            shouldShowReasoningBlock ? 'rounded-t-none border-t-0' : ''
                          }`}>
                            {message.content}
                          </div>
                        )}
                        {shouldShowPendingBlock && (
                          <div
                            role="status"
                            aria-live="polite"
                            className={`rounded-2xl px-4 py-3 text-body-sm shadow-sm bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 ${pendingContainerAnimationClass}`}
                          >
                            <div className="flex items-center gap-3">
                              <div className="flex space-x-1">
                                <div
                                  className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-llm-pending-dot"
                                  style={{ animationDelay: '0ms' }}
                                />
                                <div
                                  className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-llm-pending-dot"
                                  style={{ animationDelay: '150ms' }}
                                />
                                <div
                                  className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-llm-pending-dot"
                                  style={{ animationDelay: '300ms' }}
                                />
                              </div>
                              <span className="text-micro font-semibold text-indigo-500 tracking-tight">处理中...</span>
                              <span className="sr-only">AI 正在生成响应</span>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  )
                }

                return (
                  <div key={message.id} className="flex justify-end">
                    <div className="max-w-[70%] rounded-2xl px-4 py-3 text-body-sm shadow-sm bg-indigo-600 text-white">
                      {message.content}
                    </div>
                    <div className="ml-3 mt-1 size-7 rounded-full bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-200 flex items-center justify-center flex-shrink-0">
                      <User size={14} />
                    </div>
                  </div>
                )
              })}
              <div ref={messageEndRef} />
            </div>

            <div className="border-t border-slate-200 dark:border-slate-800 p-4">
              <div className="group flex items-center gap-3 bg-white/80 dark:bg-slate-900/80 border border-slate-200 dark:border-slate-700 rounded-xl px-3 py-2 transition-all shadow-sm focus-within:ring-2 focus-within:ring-indigo-500/20 focus-within:border-indigo-500/40 focus-within:shadow-md">
                <input
                  type="text"
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.nativeEvent.isComposing) {
                      return
                    }
                    if (event.key === 'Enter' && !isStreaming) {
                      event.preventDefault()
                      handleSend()
                    }
                  }}
                  placeholder={`Message ${model.name}...`}
                  className="flex-1 bg-transparent outline-none text-body-sm text-slate-700 dark:text-slate-200 placeholder:text-slate-400 group-focus-within:placeholder:text-slate-500 transition-colors"
                  disabled={isStreaming}
                />
                <button
                  type="button"
                  onClick={handleSend}
                  className={`size-8 rounded-lg flex items-center justify-center transition-all active:scale-95 ${
                    draft.trim() && !isStreaming
                      ? 'bg-indigo-600 text-white hover:bg-indigo-500 shadow-md shadow-indigo-500/20'
                      : 'bg-slate-200 text-slate-400 cursor-not-allowed dark:bg-slate-700'
                  }`}
                  disabled={!draft.trim() || isStreaming}
                  aria-label="发送消息"
                >
                  <Send size={14} />
                </button>
              </div>
              <p className={`${TYPOGRAPHY.micro} text-slate-400 text-center mt-2`}>AI 输出可能不准确，请谨慎判断。</p>
            </div>
          </div>

          <div className={`${menuWidthClassMap.extraWide} flex-shrink-0 border-l border-slate-200 dark:border-slate-800 p-5 bg-slate-50/60 dark:bg-slate-900/60`}>
            <div className="flex items-center gap-2 text-micro font-bold text-slate-500 uppercase tracking-wider">
              <span className="size-6 rounded-lg bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 flex items-center justify-center text-indigo-500">
                <SlidersHorizontal size={12} />
              </span>
              MODEL CONFIG
            </div>

            <div className="mt-5 space-y-5">
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-caption font-semibold text-slate-600 dark:text-slate-300">
                    System Instruction
                  </label>
                  <span className="text-micro font-mono text-slate-500">{systemInstruction.length}/2000</span>
                </div>
                <textarea
                  value={systemInstruction}
                  onChange={(event) => setSystemInstruction(event.target.value.slice(0, 2000))}
                  placeholder="可选：输入系统级指令（例如回复风格、约束）"
                  rows={4}
                  className="w-full resize-y min-h-[84px] rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-caption text-slate-700 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-500 transition-all"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-caption font-semibold text-slate-600 dark:text-slate-300">Temperature</label>
                  <span className="text-micro font-mono text-slate-500">{temperature.toFixed(1)}</span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.1"
                  value={temperature}
                  onChange={(event) => setTemperature(parseFloat(event.target.value))}
                  className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg cursor-pointer accent-indigo-500 focus:outline-none"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-caption font-semibold text-slate-600 dark:text-slate-300">Top P</label>
                  <span className="text-micro font-mono text-slate-500">{topP.toFixed(2)}</span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="1"
                  step="0.05"
                  value={topP}
                  onChange={(event) => setTopP(parseFloat(event.target.value))}
                  className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg cursor-pointer accent-indigo-500 focus:outline-none"
                />
              </div>

              <div className="flex items-center justify-between rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2">
                <div>
                  <p className="text-caption font-semibold text-slate-600 dark:text-slate-300">开启思考</p>
                  <p className="text-micro text-slate-400">仅在模型支持时生效</p>
                </div>
                <button
                  type="button"
                  onClick={() => setThinkingEnabled((prev) => !prev)}
                  className={`h-6 w-11 rounded-full border transition-colors ${
                    thinkingEnabled
                      ? 'border-indigo-500 bg-indigo-500'
                      : 'border-slate-300 bg-slate-200 dark:border-slate-600 dark:bg-slate-700'
                  }`}
                  aria-label="切换思考模式"
                >
                  <span
                    className={`block h-5 w-5 rounded-full bg-white transition-transform ${
                      thinkingEnabled ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>

              <button
                type="button"
                onClick={handleReset}
                className="w-full px-3 py-2 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-body-sm font-semibold text-slate-600 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
              >
                重置对话
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  )
}

function FilterSection({
  title,
  options,
  selected,
  onToggle,
  onAddOption,
  onEditOption,
  onDeleteOption,
  defaultOpen = false
}: {
  title: string
  options: Array<{ label: string; value: string }>
  selected: string[]
  onToggle: (value: string) => void
  onAddOption?: () => void
  onEditOption?: (value: string) => void
  onDeleteOption?: (value: string) => void
  defaultOpen?: boolean
}) {
  const [isOpen, setIsOpen] = useState(defaultOpen)

  return (
    <div className="mb-6">
      <div className="flex items-center justify-between mb-3">
        <button
          type="button"
          onClick={() => setIsOpen((prev) => !prev)}
          className="text-body-sm font-medium text-slate-900 dark:text-slate-100 hover:text-slate-600 transition-colors"
        >
          {title}
        </button>
        <div className="flex items-center gap-1">
          {onAddOption && isOpen && (
            <Tooltip content="新增供应商" side="top">
              <button
                type="button"
                onClick={onAddOption}
                className="p-1 rounded text-slate-400 hover:text-indigo-500 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 transition-all"
              >
                <Plus size={14} />
              </button>
            </Tooltip>
          )}
          <button
            type="button"
            onClick={() => setIsOpen((prev) => !prev)}
            className="p-1 rounded text-slate-400 hover:text-indigo-500 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 transition-all"
            title={isOpen ? '收起' : '展开'}
          >
            {isOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
          </button>
        </div>
      </div>

      {isOpen && (
        <div className="space-y-2.5">
          {options.map((option) => {
            const isChecked = selected.includes(option.value)
            return (
              <div key={option.value} className="group flex items-center gap-2">
                <label className="flex items-center flex-1 cursor-pointer">
                  <div className="relative flex items-center justify-center w-4 h-4 mr-3 border border-slate-300 dark:border-slate-600 rounded hover:border-slate-400 transition-colors bg-white dark:bg-slate-950">
                    <input
                      type="checkbox"
                      checked={isChecked}
                      onChange={() => onToggle(option.value)}
                      className="peer appearance-none w-full h-full cursor-pointer absolute inset-0 z-10"
                    />
                    <Check size={10} className="text-slate-900 dark:text-slate-100 opacity-0 peer-checked:opacity-100 transition-opacity" strokeWidth={3} />
                  </div>
                  <span className="text-body-sm text-slate-600 dark:text-slate-300 group-hover:text-slate-900 dark:group-hover:text-slate-100 transition-colors truncate">
                    {option.label}
                  </span>
                </label>
                {(onEditOption || onDeleteOption) && (
                  <div className="flex items-center gap-0.5">
                    {onEditOption && (
                      <Tooltip content={`编辑 ${option.label}`} side="top">
                        <button
                          type="button"
                          onClick={(event) => {
                            event.preventDefault()
                            event.stopPropagation()
                            onEditOption(option.value)
                          }}
                          className="opacity-0 group-hover:opacity-100 p-1 rounded text-slate-400 hover:text-indigo-500 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 transition-all"
                        >
                          <Pencil size={12} />
                        </button>
                      </Tooltip>
                    )}
                    {onDeleteOption && (
                      <Tooltip content={`删除 ${option.label}`} side="top">
                        <button
                          type="button"
                          onClick={(event) => {
                            event.preventDefault()
                            event.stopPropagation()
                            onDeleteOption(option.value)
                          }}
                          className="opacity-0 group-hover:opacity-100 p-1 rounded text-slate-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/20 transition-all"
                        >
                          <Trash2 size={12} />
                        </button>
                      </Tooltip>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function ModelRow({
  model,
  isSelected,
  isSelectionDisabled,
  isActive,
  isConnected,
  onToggle,
  onSelect,
  onManage
}: {
  model: ModelRecord
  isSelected: boolean
  isSelectionDisabled: boolean
  isActive: boolean
  isConnected: boolean
  onToggle: () => void
  onSelect: () => void
  onManage: () => void
}) {
  return (
    <div
      className={`group py-6 border-b border-slate-100 dark:border-slate-800 last:border-0 hover:bg-slate-50/50 dark:hover:bg-slate-800/60 transition-all -mx-4 px-4 rounded-xl cursor-pointer ${
        isSelected ? 'bg-indigo-50/40 dark:bg-indigo-500/10' : ''
      } ${isActive ? 'ring-1 ring-indigo-200/70 dark:ring-indigo-500/30' : ''}`}
      onClick={onSelect}
    >
      <div className="flex gap-4">
        <div className="pt-1">
          <div
            onClick={(event) => {
              event.stopPropagation()
              if (isSelectionDisabled) return
              onToggle()
            }}
            aria-disabled={isSelectionDisabled}
            className={`w-4 h-4 rounded border flex items-center justify-center transition-all ${
              isSelected
                ? 'bg-indigo-600 border-indigo-600 text-white cursor-pointer'
                : isSelectionDisabled
                  ? 'border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-950 text-transparent opacity-40 cursor-not-allowed'
                  : 'border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-950 group-hover:border-slate-400 text-transparent cursor-pointer'
            }`}
          >
            <Check size={10} strokeWidth={3} />
          </div>
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex justify-between items-start mb-2">
            <div className="flex items-center gap-2 min-w-0">
              <h3 className="text-body-sm font-semibold text-slate-900 dark:text-slate-100 group-hover:text-indigo-600 dark:group-hover:text-indigo-300 transition-colors">
                {model.name}
              </h3>
              <span
                className={`${TYPOGRAPHY.micro} px-2 py-0.5 rounded-full font-semibold uppercase border ${
                  isConnected
                    ? 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20'
                    : 'bg-slate-100 text-slate-500 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700'
                }`}
              >
                {isConnected ? 'ACTIVE' : 'CONNECT'}
              </span>
              <button
                className="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-slate-600 transition-all p-1 hover:bg-slate-100 dark:hover:bg-slate-800 rounded"
                title="Copy Model ID"
                onClick={(event) => {
                  event.stopPropagation()
                  navigator.clipboard.writeText(model.id)
                }}
              >
                <Copy size={12} />
              </button>
              {model.isNew && (
                <span className="px-2 py-0.5 bg-blue-50 text-blue-600 text-micro font-semibold uppercase rounded-full tracking-wide ml-1">
                  NEW
                </span>
              )}
            </div>
            <div className="flex items-center gap-3">
              <div className="text-caption text-slate-400 font-medium">
                {model.stats.params || ''}
              </div>
              <button
                type="button"
                onClick={(event) => {
                  event.stopPropagation()
                  onManage()
                }}
                className="opacity-0 group-hover:opacity-100 transition-all bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-1.5 flex items-center text-micro font-semibold text-slate-600 dark:text-slate-300 shadow-sm hover:border-indigo-300 hover:text-indigo-600 dark:hover:text-indigo-300"
              >
                {isConnected ? (
                  <>
                    <Play size={12} className="mr-1.5 fill-current" />
                    Playground
                  </>
                ) : (
                  <>
                    <Key size={12} className="mr-1.5" />
                    Configure
                  </>
                )}
              </button>
            </div>
          </div>

          <p className="text-caption text-slate-500 dark:text-slate-400 leading-relaxed mb-4 max-w-4xl">
            {model.description}
          </p>

          <div className="flex flex-wrap items-center text-caption text-slate-500 dark:text-slate-400 space-x-4 font-mono">
            <span className="text-slate-400">
              by <span className="text-slate-600 dark:text-slate-300 underline decoration-slate-300">{getProviderLabel(model)}</span>
            </span>
            <span>{model.stats.context}</span>
            <span className="text-slate-400">{model.stats.inputPrice} input</span>
            <span className="text-slate-400">{model.stats.outputPrice} output</span>
          </div>
        </div>
      </div>
    </div>
  )
}

function ModelCard({
  model,
  isSelected,
  isSelectionDisabled,
  isActive,
  isConnected,
  onToggle,
  onSelect,
  onManage
}: {
  model: ModelRecord
  isSelected: boolean
  isSelectionDisabled: boolean
  isActive: boolean
  isConnected: boolean
  onToggle: () => void
  onSelect: () => void
  onManage: () => void
}) {
  return (
    <Card
      padding="none"
      variant="default"
      className={`group flex flex-col p-5 bg-white dark:bg-slate-900 border rounded-xl transition-all relative h-full cursor-pointer ${
        isSelected
          ? 'border-indigo-400 ring-1 ring-indigo-400/30 shadow-sm'
          : 'border-slate-200 dark:border-slate-800 hover:shadow-md hover:border-slate-300 dark:hover:border-slate-700'
      } ${isActive ? 'ring-1 ring-indigo-200/70 dark:ring-indigo-500/30' : ''}`}
      onClick={onSelect}
    >
      <div className="flex justify-between items-start mb-3 gap-3">
        <div className="flex gap-3 min-w-0">
          <div
            onClick={(event) => {
              event.stopPropagation()
              if (isSelectionDisabled) return
              onToggle()
            }}
            aria-disabled={isSelectionDisabled}
            className={`w-4 h-4 rounded border flex-shrink-0 flex items-center justify-center transition-all ${
              isSelected
                ? 'bg-indigo-600 border-indigo-600 text-white cursor-pointer'
                : isSelectionDisabled
                  ? 'border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-950 text-transparent opacity-40 cursor-not-allowed'
                  : 'border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-950 opacity-0 group-hover:opacity-100 hover:border-slate-400 text-transparent cursor-pointer'
            }`}
          >
            <Check size={10} strokeWidth={3} />
          </div>

          <div className="pr-2 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h3
                className="text-body-sm font-semibold text-slate-900 dark:text-slate-100 group-hover:text-indigo-600 transition-colors line-clamp-2 leading-tight"
                title={model.name}
              >
                {model.name}
              </h3>
              <span
                className={`${TYPOGRAPHY.micro} px-2 py-0.5 rounded-full font-semibold uppercase border ${
                  isConnected
                    ? 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20'
                    : 'bg-slate-100 text-slate-500 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700'
                }`}
              >
                {isConnected ? 'ACTIVE' : 'CONNECT'}
              </span>
            </div>
            <div className="text-caption text-slate-500 mt-1.5 flex items-center">
              <span className="text-slate-400 mr-1">by</span>
              <span className="underline decoration-slate-300">{getProviderLabel(model)}</span>
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {model.isNew && (
            <span className="flex-shrink-0 px-2 py-0.5 bg-blue-50 text-blue-600 text-micro font-semibold uppercase rounded-full">
              NEW
            </span>
          )}
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation()
              onManage()
            }}
            className="opacity-0 group-hover:opacity-100 transition-all bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-2.5 py-1 flex items-center text-micro font-semibold text-slate-600 dark:text-slate-300 shadow-sm hover:border-indigo-300 hover:text-indigo-600 dark:hover:text-indigo-300"
          >
            {isConnected ? (
              <>
                <Play size={12} className="mr-1.5 fill-current" />
                Playground
              </>
            ) : (
              <>
                <Key size={12} className="mr-1.5" />
                Configure
              </>
            )}
          </button>
        </div>
      </div>

      <p className="text-caption text-slate-500 dark:text-slate-400 leading-relaxed mb-5 line-clamp-3 flex-1 pl-8">
        {model.description}
      </p>

      <div className="space-y-2 pt-4 border-t border-slate-100 dark:border-slate-800 mt-auto">
        <StatLine label="Context" value={model.stats.context} />
        <StatLine label="Input Price" value={model.stats.inputPrice} />
        <StatLine label="Output Price" value={model.stats.outputPrice} />
      </div>
    </Card>
  )
}

function StatLine({ label, value, icon }: { label: string; value: string; icon?: LucideIcon }) {
  const Icon = icon
  return (
    <div className="flex justify-between text-caption text-slate-500 dark:text-slate-400">
      <span className="flex items-center">
        {Icon && <Icon size={12} className="mr-2 text-slate-400" />}
        {label}
      </span>
      <span className="font-mono font-medium text-slate-700 dark:text-slate-200">{value}</span>
    </div>
  )
}
