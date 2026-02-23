import { useMemo, useState } from 'react'
import type { LucideIcon } from 'lucide-react'
import {
  ArrowUpDown,
  Check,
  Copy,
  Key,
  LayoutGrid,
  List,
  Maximize2,
  Play,
  Plus,
  Search,
  SlidersHorizontal
} from 'lucide-react'
import { Button, Card, Modal } from '@/components/ui'
import { contentMaxWidthClassMap, iconSizeToken, tableColumnWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import type { CreateAdminLlmModelRequest } from '@/services/studioLlmService'
import { resolveProviderLabel } from './modelAdapters'
import type { LlmProvider, ModelFilters, ModelRecord } from './types'
import { MAX_COMPARE_MODELS, filterModels, toggleModelSelection } from './utils'
import { CreateLLMModal } from './CreateLLMModal'

const getProviderLabel = (model: ModelRecord) => resolveProviderLabel(model.provider, model.providerLabel)

export function LLMModels({
  models,
  isLoadingModels,
  filters,
  providerOptions,
  providerModelIdMap,
  providerBaseUrlMap,
  activeModelId,
  connectedModelIds,
  onModelSelect,
  onOpenTest,
  onCreateModel
}: {
  models: ModelRecord[]
  isLoadingModels: boolean
  filters: ModelFilters
  providerOptions: Array<{ label: string; value: LlmProvider }>
  providerModelIdMap: Map<LlmProvider, string[]>
  providerBaseUrlMap: Map<LlmProvider, string>
  activeModelId: string | null
  connectedModelIds: string[]
  onModelSelect: (modelId: string, isConnected: boolean) => void
  onOpenTest: (modelId: string, tab: 'config' | 'playground') => void
  onCreateModel: (request: CreateAdminLlmModelRequest) => Promise<boolean>
}) {
  const [searchQuery, setSearchQuery] = useState('')
  const [viewMode, setViewMode] = useState<'list' | 'grid'>('list')
  const [selectedModelIds, setSelectedModelIds] = useState<string[]>([])
  const [isCompareOpen, setIsCompareOpen] = useState(false)
  const [isCreateOpen, setIsCreateOpen] = useState(false)

  const filteredModels = useMemo(
    () => filterModels(models, searchQuery, filters),
    [models, searchQuery, filters]
  )

  const selectedModels = useMemo(
    () => models.filter((model) => selectedModelIds.includes(model.id)),
    [models, selectedModelIds]
  )

  const toggleSelection = (id: string) => {
    setSelectedModelIds((prev) => toggleModelSelection(prev, id))
  }

  const openCompare = () => {
    if (selectedModelIds.length === 0) return
    setIsCompareOpen(true)
  }

  return (
    <>
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
                    onSelect={() => onModelSelect(model.id, isConnected)}
                    onManage={() => onOpenTest(model.id, isConnected ? 'playground' : 'config')}
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
                    onSelect={() => onModelSelect(model.id, isConnected)}
                    onManage={() => onOpenTest(model.id, isConnected ? 'playground' : 'config')}
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
      <CreateLLMModal
        isOpen={isCreateOpen}
        providerOptions={providerOptions}
        providerModelIdMap={providerModelIdMap}
        providerBaseUrlMap={providerBaseUrlMap}
        onClose={() => setIsCreateOpen(false)}
        onSubmit={onCreateModel}
      />
    </>
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
                  void navigator.clipboard.writeText(model.id)
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
