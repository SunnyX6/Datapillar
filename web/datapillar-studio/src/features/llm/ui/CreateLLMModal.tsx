import { useEffect, useMemo, useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button, Modal, ModalCancelButton, ModalPrimaryButton, Select } from '@/components/ui'
import type { CreateAdminLlmModelRequest } from '@/services/studioLlmService'
import type { LlmProvider, ModelCategory } from '../utils/types'

const MODEL_TYPES: ModelCategory[] = ['chat', 'embeddings', 'reranking', 'code']

type CreateFormState = {
  providerModelId: string
  name: string
  provider: LlmProvider
  baseUrl: string
  apiKey: string
  description: string
  modelType: ModelCategory
}

function buildCreateForm(
  provider: LlmProvider,
  providerModelIdMap: Map<LlmProvider, string[]>,
  providerBaseUrlMap: Map<LlmProvider, string>
): CreateFormState {
  return {
    providerModelId: providerModelIdMap.get(provider)?.[0] ?? '',
    name: '',
    provider,
    baseUrl: providerBaseUrlMap.get(provider) ?? '',
    apiKey: '',
    description: '',
    modelType: 'chat'
  }
}

export function CreateLLMModal({
  isOpen,
  providerOptions,
  providerModelIdMap,
  providerBaseUrlMap,
  onClose,
  onSubmit
}: {
  isOpen: boolean
  providerOptions: Array<{ label: string; value: LlmProvider }>
  providerModelIdMap: Map<LlmProvider, string[]>
  providerBaseUrlMap: Map<LlmProvider, string>
  onClose: () => void
  onSubmit: (request: CreateAdminLlmModelRequest) => Promise<boolean>
}) {
  const { t } = useTranslation('llm')
  const defaultProvider = useMemo<LlmProvider>(
    () => providerOptions[0]?.value ?? 'openai',
    [providerOptions]
  )
  const modelTypeOptions = useMemo(
    () => MODEL_TYPES.map((value) => ({ value, label: t(`modelTypes.${value}`) })),
    [t]
  )

  const [form, setForm] = useState<CreateFormState>(() => (
    buildCreateForm(defaultProvider, providerModelIdMap, providerBaseUrlMap)
  ))
  const [isApiKeyVisible, setIsApiKeyVisible] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (!isOpen) {
      return
    }
    setForm(buildCreateForm(defaultProvider, providerModelIdMap, providerBaseUrlMap))
    setIsApiKeyVisible(false)
  }, [defaultProvider, isOpen, providerBaseUrlMap, providerModelIdMap])

  const modelIdOptions = useMemo(() => {
    const modelIds = providerModelIdMap.get(form.provider) ?? []
    return modelIds.map((modelId) => ({ value: modelId, label: modelId }))
  }, [form.provider, providerModelIdMap])

  const isValid =
    form.providerModelId.trim().length > 0
    && form.name.trim().length > 0
    && form.provider.trim().length > 0

  const handleProviderChange = (provider: LlmProvider) => {
    setForm((prev) => ({
      ...prev,
      provider,
      providerModelId: providerModelIdMap.get(provider)?.[0] ?? '',
      baseUrl: providerBaseUrlMap.get(provider) ?? ''
    }))
  }

  const handleClose = () => {
    if (isSubmitting) {
      return
    }
    onClose()
  }

  const handleSubmit = async () => {
    if (!isValid || isSubmitting) {
      return
    }
    setIsSubmitting(true)
    try {
      const created = await onSubmit({
        providerModelId: form.providerModelId.trim(),
        name: form.name.trim(),
        providerCode: form.provider,
        modelType: form.modelType,
        description: form.description.trim() || undefined,
        baseUrl: form.baseUrl.trim() || undefined,
        apiKey: form.apiKey.trim() || undefined
      })
      if (created) {
        onClose()
        setForm(buildCreateForm(defaultProvider, providerModelIdMap, providerBaseUrlMap))
        setIsApiKeyVisible(false)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      size="sm"
      title={t('createModal.title')}
      footerRight={
        <>
          <ModalCancelButton onClick={handleClose} disabled={isSubmitting} />
          <ModalPrimaryButton onClick={handleSubmit} disabled={!isValid || isSubmitting} loading={isSubmitting}>
            {t('createModal.confirm')}
          </ModalPrimaryButton>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {t('createModal.modelName')} <span className="text-rose-500">*</span>
          </label>
          <input
            type="text"
            value={form.name}
            onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
            placeholder={t('createModal.modelNamePlaceholder')}
            className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>

        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {t('createModal.baseUrl')}
          </label>
          <input
            type="text"
            value={form.baseUrl}
            onChange={(event) => setForm((prev) => ({ ...prev, baseUrl: event.target.value }))}
            placeholder={t('createModal.baseUrlPlaceholder')}
            className="w-full px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
          />
        </div>

        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {t('createModal.apiKeyOptional')}
          </label>
          <div className="relative">
            <input
              type={isApiKeyVisible ? 'text' : 'password'}
              value={form.apiKey}
              onChange={(event) => setForm((prev) => ({ ...prev, apiKey: event.target.value }))}
              placeholder={t('createModal.apiKeyPlaceholder')}
              className="w-full pl-3 pr-10 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
            <Button
              type="button"
              onClick={() => setIsApiKeyVisible((prev) => !prev)}
              variant="ghost"
              size="tiny"
              className="absolute inset-y-0 right-0 px-3 flex items-center text-slate-400 dark:text-slate-500 hover:text-indigo-500 transition-colors"
              aria-label={isApiKeyVisible ? t('createModal.hideApiKeyAria') : t('createModal.showApiKeyAria')}
            >
              {isApiKeyVisible ? <EyeOff size={14} /> : <Eye size={14} />}
            </Button>
          </div>
        </div>

        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {t('createModal.provider')} <span className="text-rose-500">*</span>
          </label>
          <Select
            value={form.provider}
            onChange={(value) => handleProviderChange(value as LlmProvider)}
            options={providerOptions.map((opt) => ({ value: opt.value, label: opt.label }))}
            dropdownHeader={t('createModal.selectProvider')}
            size="sm"
          />
        </div>

        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {t('createModal.modelId')} <span className="text-rose-500">*</span>
          </label>
          <Select
            value={form.providerModelId}
            onChange={(value) => setForm((prev) => ({ ...prev, providerModelId: value }))}
            options={modelIdOptions}
            placeholder={modelIdOptions.length > 0 ? t('createModal.selectModelId') : t('createModal.noModelIdOptions')}
            dropdownHeader={t('createModal.selectModelId')}
            size="sm"
          />
        </div>

        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {t('createModal.modelType')} <span className="text-rose-500">*</span>
          </label>
          <Select
            value={form.modelType}
            onChange={(value) => setForm((prev) => ({ ...prev, modelType: value as ModelCategory }))}
            options={modelTypeOptions.map((opt) => ({ value: opt.value, label: opt.label }))}
            dropdownHeader={t('createModal.selectModelType')}
            size="sm"
          />
        </div>

        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            {t('createModal.description')}
          </label>
          <textarea
            value={form.description}
            onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))}
            placeholder={t('createModal.descriptionPlaceholder')}
            className="w-full min-h-[88px] px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600 resize-none"
          />
        </div>
      </div>
    </Modal>
  )
}
