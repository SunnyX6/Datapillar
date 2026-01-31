import { useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import type { LucideIcon } from 'lucide-react'
import {
  ArrowUpDown,
  ArrowRight,
  Bot,
  Check,
  ChevronDown,
  ChevronRight,
  Copy,
  Globe,
  Key,
  LayoutGrid,
  List,
  Play,
  Settings2,
  Send,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Maximize2,
  X
} from 'lucide-react'
import { Card, Modal } from '@/components/ui'
import { contentMaxWidthClassMap, drawerWidthClassMap, iconSizeToken, menuWidthClassMap, panelWidthClassMap, tableColumnWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { MAX_COMPARE_MODELS, connectModel, filterModels, toggleModelSelection } from './utils'
import type { LlmProvider, ModelCategory, ModelFilters, ModelRecord } from './types'

const PROVIDER_OPTIONS: Array<{ label: string; value: LlmProvider }> = [
  { label: 'OpenAI', value: 'openai' },
  { label: 'Anthropic', value: 'anthropic' },
  { label: 'Google', value: 'google' },
  { label: 'Meta', value: 'meta' },
  { label: 'Mistral', value: 'mistral' },
  { label: 'DeepSeek', value: 'deepseek' }
]

const TYPE_OPTIONS: Array<{ label: string; value: ModelCategory }> = [
  { label: 'Chat', value: 'chat' },
  { label: 'Embeddings', value: 'embeddings' },
  { label: 'Reranking', value: 'reranking' }
]

const CONTEXT_OPTIONS = ['4K', '8K', '32K', '64K', '128K', '1M+']

const MODEL_RECORDS: ModelRecord[] = [
  {
    id: 'deepseek/deepseek-chat-v3',
    name: 'DeepSeek: DeepSeek V3',
    provider: 'deepseek',
    description:
      'DeepSeek-V3 is a strong Mixture-of-Experts (MoE) language model with 671B total parameters with 37B active for each token. It achieves comparable performance to GPT-4 and Claude 3.5 Sonnet.',
    tags: ['chat'],
    type: 'chat',
    contextGroup: '64K',
    stats: { context: '64K context', inputPrice: '$0.14/M', outputPrice: '$0.28/M', params: '671B params' },
    isNew: true
  },
  {
    id: 'openai/gpt-4o',
    name: 'OpenAI: GPT-4o',
    provider: 'openai',
    description:
      "GPT-4o is OpenAI's flagship model that integrates text, audio, and image processing in real time. It offers state-of-the-art performance in reasoning and multimodal tasks.",
    tags: ['chat', 'vision'],
    type: 'chat',
    contextGroup: '128K',
    stats: { context: '128K context', inputPrice: '$5.00/M', outputPrice: '$15.00/M' }
  },
  {
    id: 'openai/text-embedding-3-large',
    name: 'OpenAI: Text Embedding 3 Large',
    provider: 'openai',
    description: 'Most capable embedding model for both English and non-English tasks.',
    tags: ['embeddings'],
    type: 'embeddings',
    contextGroup: '8K',
    stats: { context: '8K context', inputPrice: '$0.13/M', outputPrice: '-', params: '3072 dims' }
  },
  {
    id: 'anthropic/claude-3.5-sonnet',
    name: 'Anthropic: Claude 3.5 Sonnet',
    provider: 'anthropic',
    description:
      'Claude 3.5 Sonnet raises the industry bar for intelligence, outperforming competitor models and Claude 3 Opus on a wide range of evaluations.',
    tags: ['chat', 'vision', 'coding'],
    type: 'chat',
    contextGroup: '128K',
    stats: { context: '200K context', inputPrice: '$3.00/M', outputPrice: '$15.00/M' }
  },
  {
    id: 'meta-llama/llama-3-70b-instruct',
    name: 'Meta: Llama 3 70B Instruct (free)',
    provider: 'meta',
    description:
      'The Llama 3 instruction tuned models are optimized for dialogue use cases and outperform many of the available open source chat models on common industry benchmarks.',
    tags: ['chat', 'free', 'open-source'],
    type: 'chat',
    contextGroup: '8K',
    stats: { context: '8K context', inputPrice: '$0/M', outputPrice: '$0/M', params: '70B params' }
  },
  {
    id: 'google/gemini-pro-1.5',
    name: 'Google: Gemini Pro 1.5',
    provider: 'google',
    description:
      'Gemini 1.5 Pro is a mid-size multimodal model that is optimized for scaling across a wide range of tasks.',
    tags: ['chat', 'vision', 'long-context'],
    type: 'chat',
    contextGroup: '1M+',
    stats: { context: '1M context', inputPrice: '$3.50/M', outputPrice: '$10.50/M' }
  },
  {
    id: 'mistralai/mistral-large',
    name: 'Mistral: Mistral Large',
    provider: 'mistral',
    description: 'Mistral Large is the most advanced Large Language Model (LLM) developed by Mistral AI.',
    tags: ['chat'],
    type: 'chat',
    contextGroup: '32K',
    stats: { context: '32K context', inputPrice: '$8.00/M', outputPrice: '$24.00/M' }
  }
]

const PROVIDER_LABEL: Record<LlmProvider, string> = {
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  deepseek: 'DeepSeek',
  google: 'Google',
  meta: 'Meta',
  mistral: 'Mistral',
  custom: 'Custom'
}

export function ModelManagementView() {
  const [searchQuery, setSearchQuery] = useState('')
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('list')
  const [selectedModelIds, setSelectedModelIds] = useState<string[]>([])
  const [connectedModelIds, setConnectedModelIds] = useState<string[]>([])
  const [activeModelId, setActiveModelId] = useState<string | null>(null)
  const [drawerTab, setDrawerTab] = useState<'config' | 'playground'>('config')
  const [filters, setFilters] = useState<ModelFilters>({ providers: [], types: [], contexts: [] })
  const [isCompareOpen, setIsCompareOpen] = useState(false)

  const filteredModels = useMemo(() => filterModels(MODEL_RECORDS, searchQuery, filters), [searchQuery, filters])

  const toggleFilter = <T extends keyof ModelFilters>(group: T, value: ModelFilters[T][number]) => {
    setFilters((prev) => {
      const nextValues = prev[group].includes(value)
        ? prev[group].filter((item) => item !== value)
        : [...prev[group], value]
      return { ...prev, [group]: nextValues }
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

  const handleConnectModel = (modelId: string) => {
    setConnectedModelIds((prev) => connectModel(prev, modelId))
    setDrawerTab('playground')
  }

  const selectedModels = useMemo(
    () => MODEL_RECORDS.filter((model) => selectedModelIds.includes(model.id)),
    [selectedModelIds]
  )
  const activeModel = useMemo(
    () => MODEL_RECORDS.find((model) => model.id === activeModelId) ?? null,
    [activeModelId]
  )

  return (
    <section className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
      <aside className={`hidden @md:block ${panelWidthClassMap.mediumResponsive} flex-shrink-0 border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-5 @xl:pl-8 @xl:pr-6 pb-6 pt-8 overflow-y-auto custom-scrollbar`}>
        <FilterSection
          title="供应商 (Providers)"
          options={PROVIDER_OPTIONS}
          selected={filters.providers}
          onToggle={(value) => toggleFilter('providers', value as LlmProvider)}
          defaultOpen
        />
        <FilterSection
          title="模型类型 (Type)"
          options={TYPE_OPTIONS}
          selected={filters.types}
          onToggle={(value) => toggleFilter('types', value as ModelCategory)}
          defaultOpen
        />
        <FilterSection
          title="上下文长度 (Context)"
          options={CONTEXT_OPTIONS.map((value) => ({ label: value, value }))}
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
            <span>{filteredModels.length} 个模型</span>
            <button type="button" className="flex items-center hover:text-slate-900 dark:hover:text-slate-200 transition-colors">
              最新发布 (Newest)
              <ArrowUpDown size={14} className="ml-1.5" />
            </button>
          </div>

          {viewMode === 'list' ? (
            <div className="space-y-2">
              {filteredModels.map((model) => {
                const isConnected = connectedModelIds.includes(model.id)
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
                const isConnected = connectedModelIds.includes(model.id)
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

      {activeModel &&
        createPortal(
          <ModelDrawer
            key={activeModel.id}
            model={activeModel}
            isConnected={connectedModelIds.includes(activeModel.id)}
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
                    {PROVIDER_LABEL[model.provider]}
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
}

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
  onConnect: (modelId: string) => void
}) {
  const [activeTab, setActiveTab] = useState<'config' | 'playground'>(isConnected ? defaultTab : 'config')
  const [isEditing, setIsEditing] = useState(false)
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([buildWelcomeMessage(model)])
  const [draft, setDraft] = useState('')
  const [temperature, setTemperature] = useState(0.7)
  const [maxTokens, setMaxTokens] = useState(1024)
  const [systemInstruction, setSystemInstruction] = useState('你是 Datapillar 的模型评测助手，擅长回答与数据工程相关的问题。')

  useEffect(() => {
    setMessages([buildWelcomeMessage(model)])
    setDraft('')
    setApiKey('')
    setBaseUrl('')
    setIsEditing(false)
    setActiveTab(isConnected ? defaultTab : 'config')
  }, [model.id, defaultTab, isConnected])

  const handleSend = () => {
    const trimmed = draft.trim()
    if (!trimmed) return
    setMessages((prev) => [
      ...prev,
      {
        id: `user-${Date.now()}`,
        role: 'user',
        content: trimmed
      }
    ])
    setDraft('')
  }

  const handleReset = () => {
    setMessages([buildWelcomeMessage(model)])
    setDraft('')
  }

  const handleConnect = () => {
    if (!apiKey.trim()) return
    onConnect(model.id)
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
          <div className="w-full max-w-[480px] @md:max-w-[520px]">
            <div className="text-center mb-8">
              <div className="w-14 h-14 mx-auto rounded-2xl flex items-center justify-center mb-4 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 shadow-sm">
                <Key size={22} className="text-slate-400" />
              </div>
              <h2 className="text-subtitle @md:text-title font-black text-slate-900 dark:text-white">
                {isConnected ? `${PROVIDER_LABEL[model.provider]} 已连接` : `Connect to ${PROVIDER_LABEL[model.provider]}`}
              </h2>
              <p className="text-caption @md:text-body-sm text-slate-500 dark:text-slate-400 mt-2.5 max-w-sm mx-auto">
                {isConnected
                  ? 'API Key 已连接，可直接进入 Playground 测试模型。'
                  : `输入 API Key 以连接 ${PROVIDER_LABEL[model.provider]} 并启用 ${model.name}。`}
              </p>
            </div>

            <div className="bg-white dark:bg-slate-900 p-3.5 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm space-y-2.5 max-w-[320px] @md:max-w-[360px] mx-auto">
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
                      onClick={() => setIsEditing(true)}
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
                    <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-wide mb-1">
                      API Key <span className="text-rose-500">*</span>
                    </label>
                    <div className="relative group">
                      <Key size={12} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-indigo-500 transition-colors" />
                      <input
                        type="password"
                        value={apiKey}
                        onChange={(event) => setApiKey(event.target.value)}
                        placeholder={`sk-... (${PROVIDER_LABEL[model.provider]} Key)`}
                        className="w-full h-9 pl-8 pr-3 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl text-caption text-slate-700 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-500 transition-all font-mono"
                        autoFocus
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-wide mb-1">
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
                      onClick={handleConnect}
                      disabled={!apiKey.trim()}
                      className="w-full h-9 bg-indigo-400 hover:bg-indigo-500 text-white text-caption font-semibold rounded-xl transition-all shadow-sm flex items-center justify-center disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      Save &amp; Connect
                      <ArrowRight size={16} className="ml-2" />
                    </button>
                    <p className="text-[9px] text-center text-slate-400 mt-2 flex items-center justify-center">
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
              {messages.map((message) => (
                <div
                  key={message.id}
                  className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  {message.role === 'assistant' && (
                    <div className="mr-3 mt-1 size-7 rounded-full bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-300 flex items-center justify-center flex-shrink-0">
                      <Bot size={14} />
                    </div>
                  )}
                  <div
                    className={`max-w-[70%] rounded-2xl px-4 py-3 text-body-sm shadow-sm ${
                      message.role === 'assistant'
                        ? 'bg-white dark:bg-slate-800 border border-slate-100 dark:border-slate-700 text-slate-700 dark:text-slate-200'
                        : 'bg-indigo-600 text-white'
                    }`}
                  >
                    {message.content}
                  </div>
                </div>
              ))}
            </div>

            <div className="border-t border-slate-200 dark:border-slate-800 p-4">
              <div className="group flex items-center gap-3 bg-white/80 dark:bg-slate-900/80 border border-slate-200 dark:border-slate-700 rounded-xl px-3 py-2 transition-all shadow-sm focus-within:ring-2 focus-within:ring-indigo-500/20 focus-within:border-indigo-500/40 focus-within:shadow-md">
                <input
                  type="text"
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') handleSend()
                  }}
                  placeholder={`Message ${model.name}...`}
                  className="flex-1 bg-transparent outline-none text-body-sm text-slate-700 dark:text-slate-200 placeholder:text-slate-400 group-focus-within:placeholder:text-slate-500 transition-colors"
                />
                <button
                  type="button"
                  onClick={handleSend}
                  className={`size-8 rounded-lg flex items-center justify-center transition-all active:scale-95 ${
                    draft.trim()
                      ? 'bg-indigo-600 text-white hover:bg-indigo-500 shadow-md shadow-indigo-500/20'
                      : 'bg-slate-200 text-slate-400 cursor-not-allowed dark:bg-slate-700'
                  }`}
                  disabled={!draft.trim()}
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
                <label className="block text-caption font-semibold text-slate-600 dark:text-slate-300 mb-2">系统指令</label>
                <textarea
                  value={systemInstruction}
                  onChange={(event) => setSystemInstruction(event.target.value)}
                  rows={4}
                className="w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-2 text-caption text-slate-500 dark:text-slate-300 resize-none focus:outline-none focus:border-indigo-500/50"
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
                  <label className="text-caption font-semibold text-slate-600 dark:text-slate-300">Max Tokens</label>
                  <span className="text-micro font-mono text-slate-500">{maxTokens}</span>
                </div>
                <input
                  type="range"
                  min="256"
                  max="4096"
                  step="128"
                  value={maxTokens}
                  onChange={(event) => setMaxTokens(parseInt(event.target.value, 10))}
                  className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg cursor-pointer accent-blue-500 focus:outline-none"
                />
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
  defaultOpen = false
}: {
  title: string
  options: Array<{ label: string; value: string }>
  selected: string[]
  onToggle: (value: string) => void
  defaultOpen?: boolean
}) {
  const [isOpen, setIsOpen] = useState(defaultOpen)

  return (
    <div className="mb-6">
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        className="flex items-center justify-between w-full text-body-sm font-medium text-slate-900 dark:text-slate-100 mb-3 hover:text-slate-600 transition-colors"
      >
        {title}
        {isOpen ? <ChevronDown size={16} className="text-slate-400" /> : <ChevronRight size={16} className="text-slate-400" />}
      </button>

      {isOpen && (
        <div className="space-y-2.5">
          {options.map((option) => {
            const isChecked = selected.includes(option.value)
            return (
              <label key={option.value} className="flex items-center group cursor-pointer">
                <div className="relative flex items-center justify-center w-4 h-4 mr-3 border border-slate-300 dark:border-slate-600 rounded hover:border-slate-400 transition-colors bg-white dark:bg-slate-950">
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={() => onToggle(option.value)}
                    className="peer appearance-none w-full h-full cursor-pointer absolute inset-0 z-10"
                  />
                  <Check size={10} className="text-slate-900 dark:text-slate-100 opacity-0 peer-checked:opacity-100 transition-opacity" strokeWidth={3} />
                </div>
                <span className="text-body-sm text-slate-600 dark:text-slate-300 group-hover:text-slate-900 dark:group-hover:text-slate-100 transition-colors">
                  {option.label}
                </span>
              </label>
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
                className={`px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase border ${
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
              by <span className="text-slate-600 dark:text-slate-300 underline decoration-slate-300">{model.provider}</span>
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
                className={`px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase border ${
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
              <span className="underline decoration-slate-300">{PROVIDER_LABEL[model.provider]}</span>
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
