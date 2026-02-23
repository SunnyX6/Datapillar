import { useEffect, useMemo, useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { Button, Modal, ModalCancelButton, ModalPrimaryButton, Select } from '@/components/ui'
import type { CreateAdminLlmModelRequest } from '@/services/studioLlmService'
import type { LlmProvider, ModelCategory } from './types'

const MODEL_TYPE_OPTIONS: Array<{ label: string; value: ModelCategory }> = [
  { label: 'Chat', value: 'chat' },
  { label: 'Embeddings', value: 'embeddings' },
  { label: 'Reranking', value: 'reranking' },
  { label: 'Code', value: 'code' }
]

type CreateFormState = {
  modelId: string
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
    modelId: providerModelIdMap.get(provider)?.[0] ?? '',
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
  const defaultProvider = useMemo<LlmProvider>(
    () => providerOptions[0]?.value ?? 'openai',
    [providerOptions]
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
    form.modelId.trim().length > 0
    && form.name.trim().length > 0
    && form.provider.trim().length > 0

  const handleProviderChange = (provider: LlmProvider) => {
    setForm((prev) => ({
      ...prev,
      provider,
      modelId: providerModelIdMap.get(provider)?.[0] ?? '',
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
        modelId: form.modelId.trim(),
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
      title="添加模型"
      footerRight={
        <>
          <ModalCancelButton onClick={handleClose} disabled={isSubmitting} />
          <ModalPrimaryButton onClick={handleSubmit} disabled={!isValid || isSubmitting} loading={isSubmitting}>
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
            value={form.name}
            onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))}
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
            value={form.baseUrl}
            onChange={(event) => setForm((prev) => ({ ...prev, baseUrl: event.target.value }))}
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
              type={isApiKeyVisible ? 'text' : 'password'}
              value={form.apiKey}
              onChange={(event) => setForm((prev) => ({ ...prev, apiKey: event.target.value }))}
              placeholder="sk-..."
              className="w-full pl-3 pr-10 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600"
            />
            <Button
              type="button"
              onClick={() => setIsApiKeyVisible((prev) => !prev)}
              variant="ghost"
              size="tiny"
              className="absolute inset-y-0 right-0 px-3 flex items-center text-slate-400 dark:text-slate-500 hover:text-indigo-500 transition-colors"
              aria-label={isApiKeyVisible ? '隐藏 API Key' : '显示 API Key'}
            >
              {isApiKeyVisible ? <EyeOff size={14} /> : <Eye size={14} />}
            </Button>
          </div>
        </div>

        <div>
          <label className="block text-caption font-medium text-slate-700 dark:text-slate-300 mb-1.5">
            Provider <span className="text-rose-500">*</span>
          </label>
          <Select
            value={form.provider}
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
            value={form.modelId}
            onChange={(value) => setForm((prev) => ({ ...prev, modelId: value }))}
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
            value={form.modelType}
            onChange={(value) => setForm((prev) => ({ ...prev, modelType: value as ModelCategory }))}
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
            value={form.description}
            onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))}
            placeholder="模型描述（可选）"
            className="w-full min-h-[88px] px-3 py-2 text-body-sm text-slate-800 dark:text-slate-200 border border-slate-300 dark:border-slate-600 rounded-xl bg-white dark:bg-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all placeholder:text-slate-400 dark:placeholder:text-slate-600 resize-none"
          />
        </div>
      </div>
    </Modal>
  )
}
