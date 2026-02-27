import { useState } from 'react'
import { Check, ChevronDown, ChevronRight, Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { Modal, ModalCancelButton, ModalPrimaryButton, Tooltip } from '@/components/ui'
import { panelWidthClassMap } from '@/design-tokens/dimensions'
import type { UpdateAdminLlmProviderRequest, StudioLlmProvider } from '@/services/studioLlmService'
import type { LlmProvider, ModelCategory, ModelFilters } from '../utils/types'

type ProviderCreateForm = {
  code: string
  name: string
  baseUrl: string
  modelIds: string[]
}

type ProviderEditForm = {
  code: string
  name: string
  baseUrl: string
  modelIds: string[]
}

const emptyProviderCreateForm: ProviderCreateForm = {
  code: '',
  name: '',
  baseUrl: '',
  modelIds: []
}

const emptyProviderEditForm: ProviderEditForm = {
  code: '',
  name: '',
  baseUrl: '',
  modelIds: []
}

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

export function LLMFilter({
  providerOptions,
  filterTypeOptions,
  contextOptions,
  filters,
  providers,
  onToggleProvider,
  onToggleType,
  onToggleContext,
  onCreateProvider,
  onUpdateProvider,
  onDeleteProvider
}: {
  providerOptions: Array<{ label: string; value: LlmProvider }>
  filterTypeOptions: Array<{ label: string; value: ModelCategory }>
  contextOptions: Array<{ label: string; value: string }>
  filters: ModelFilters
  providers: StudioLlmProvider[]
  onToggleProvider: (provider: LlmProvider) => void
  onToggleType: (type: ModelCategory) => void
  onToggleContext: (context: string) => void
  onCreateProvider: (request: { code: string; name?: string; baseUrl?: string; modelIds: string[] }) => Promise<void>
  onUpdateProvider: (providerCode: string, request: UpdateAdminLlmProviderRequest) => Promise<void>
  onDeleteProvider: (providerCode: string) => Promise<void>
}) {
  const [isCreateProviderOpen, setIsCreateProviderOpen] = useState(false)
  const [isEditProviderOpen, setIsEditProviderOpen] = useState(false)
  const [providerCreateForm, setProviderCreateForm] = useState<ProviderCreateForm>(emptyProviderCreateForm)
  const [providerEditForm, setProviderEditForm] = useState<ProviderEditForm>(emptyProviderEditForm)
  const [isCreatingProvider, setIsCreatingProvider] = useState(false)
  const [isUpdatingProvider, setIsUpdatingProvider] = useState(false)
  const [isDeletingProvider, setIsDeletingProvider] = useState(false)

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
      await onDeleteProvider(providerCode)
      if (normalizeProviderCode(providerEditForm.code) === providerCode) {
        setIsEditProviderOpen(false)
        setProviderEditForm(emptyProviderEditForm)
      }
      toast.success(`已删除 Provider：${providerCode}`)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
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
      await onCreateProvider({
        code: providerCode,
        name: providerName || undefined,
        baseUrl: baseUrl || undefined,
        modelIds: createModelIds
      })
      setIsCreateProviderOpen(false)
      setProviderCreateForm(emptyProviderCreateForm)
      toast.success(`已新增 Provider：${providerCode}`)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
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
      await onUpdateProvider(providerCode, request)
      setProviderEditForm((prev) => ({
        ...prev,
        name: nextName,
        baseUrl: nextBaseUrl,
        modelIds: nextModelIds
      }))
      setIsEditProviderOpen(false)
      toast.success(`已更新 Provider：${providerCode}`)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`更新 Provider 失败：${message}`)
    } finally {
      setIsUpdatingProvider(false)
    }
  }

  return (
    <>
      <aside className={`hidden @md:block ${panelWidthClassMap.mediumResponsive} flex-shrink-0 border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-5 @xl:pl-8 @xl:pr-6 pb-6 pt-8 overflow-y-auto custom-scrollbar`}>
        <FilterSection
          title="供应商 (Providers)"
          options={providerOptions}
          selected={filters.providers}
          onToggle={(value) => onToggleProvider(value as LlmProvider)}
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
          onToggle={(value) => onToggleType(value as ModelCategory)}
          defaultOpen
        />
        <FilterSection
          title="上下文长度 (Context)"
          options={contextOptions}
          selected={filters.contexts}
          onToggle={(value) => onToggleContext(value)}
          defaultOpen
        />
      </aside>

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
    </>
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
