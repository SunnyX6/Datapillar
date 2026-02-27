import { useEffect, useMemo, useState } from 'react'
import { Search, Database, ChevronRight, FileText, Sliders, Zap, Layers, Command, Globe } from 'lucide-react'
import { toast } from 'sonner'
import { Card } from '@/components/ui'
import { Select, type SelectOption } from '@/components/ui/Select'
import { cardWidthClassMap, contentMaxWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { retrieve } from '@/services/knowledgeWikiService'
import type { Document, SearchResult } from '../utils/types'
import { mapSearchHitToResult } from '../utils'

interface Props {
  spaceId: string
  spaceName: string
  documents: Document[]
  isNamespaceCollapsed: boolean
}

type RetrievalMode = 'semantic' | 'hybrid'

const RETRIEVAL_MODE_OPTIONS: SelectOption[] = [
  { value: 'semantic', label: '语义' },
  { value: 'hybrid', label: '混合' }
]

export default function RetrievalPlayground({ spaceId, spaceName, documents, isNamespaceCollapsed }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchResult[] | null>(null)
  const [isSearching, setIsSearching] = useState(false)
  const [showConfig, setShowConfig] = useState(false)
  const [searchScope, setSearchScope] = useState<string>('all')
  const [retrievalMode, setRetrievalMode] = useState<RetrievalMode>('hybrid')
  const [enableRerank, setEnableRerank] = useState(true)
  const [topK, setTopK] = useState(5)
  const [threshold, setThreshold] = useState(0.7)
  const [latencyMs, setLatencyMs] = useState<number | null>(null)
  const searchWidthClass = `mx-auto w-full ${cardWidthClassMap.half} md:${cardWidthClassMap.medium} xl:${cardWidthClassMap.wide}`
  const resultsWidthClass = `mx-auto w-full ${
    isNamespaceCollapsed ? contentMaxWidthClassMap.normal : cardWidthClassMap.superWide
  }`

  const searchScopeOptions = useMemo<SelectOption[]>(() => {
    return [
      { value: 'all', label: '全库检索 (All Documents)' },
      ...documents.map((doc) => ({ value: doc.id, label: doc.title }))
    ]
  }, [documents])

  useEffect(() => {
    if (searchScope !== 'all' && !documents.some((doc) => doc.id === searchScope)) {
      setSearchScope('all')
    }
  }, [documents, searchScope])

  const handleSearch = async () => {
    if (!query.trim()) return
    if (!spaceId) {
      toast.warning('请先选择知识空间')
      return
    }
    const namespaceId = Number(spaceId)
    if (!Number.isFinite(namespaceId)) {
      toast.error('知识空间 ID 无效')
      return
    }
    setIsSearching(true)
    setShowConfig(false)
    try {
      const response = await retrieve({
        namespace_id: namespaceId,
        query: query.trim(),
        search_scope: searchScope,
        retrieval_mode: retrievalMode,
        rerank_enabled: enableRerank,
        top_k: topK,
        score_threshold: threshold
      })
      setResults(response.hits.map(mapSearchHitToResult))
      setLatencyMs(response.latency_ms)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`检索失败：${message}`)
      setResults([])
      setLatencyMs(null)
    } finally {
      setIsSearching(false)
    }
  }


  return (
    <div className="flex flex-col h-full bg-slate-50/50 dark:bg-slate-950/40 relative overflow-visible">
      <div className="absolute top-0 inset-x-0 h-64 bg-gradient-to-b from-indigo-50/50 to-transparent dark:from-indigo-500/10 pointer-events-none" />

      <div className={`relative z-10 flex flex-col h-full ${contentMaxWidthClassMap.extraWide} mx-auto w-full p-4`}>
        <div className="flex flex-col items-center justify-center pt-6 pb-4 space-y-5 flex-shrink-0">
          <div className="text-center space-y-2">
            <h3 className={`${TYPOGRAPHY.subtitle} font-bold text-slate-900 dark:text-slate-100 tracking-tight flex items-center justify-center`}>
              <Zap className="mr-2 text-indigo-500" size={20} />
              检索召回测试
            </h3>
            <p className={`${TYPOGRAPHY.bodySm} text-slate-500 dark:text-slate-400`}>模拟 Agent 运行时环境，调试混合检索与重排序效果</p>
            <p className={`${TYPOGRAPHY.caption} text-slate-400`}>当前空间：{spaceName}</p>
          </div>

          <div className="w-full relative z-20">
            <div className={searchWidthClass}>
              <div className="relative group transition-all duration-200">
                <div className="absolute -inset-0.5 bg-gradient-to-r from-indigo-400 to-purple-400 rounded-xl opacity-20 group-hover:opacity-40 transition duration-200 blur"></div>
                <div className="relative flex items-center bg-white dark:bg-slate-900 rounded-xl shadow-lg border border-slate-100 dark:border-slate-800 overflow-hidden h-10">
                  <div className="pl-4 text-slate-400">
                    {isSearching ? (
                      <div className="w-5 h-5 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
                    ) : (
                      <Search size={18} />
                    )}
                  </div>
                  <input
                    type="text"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                    className={`w-full h-full px-4 text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-none bg-transparent ${TYPOGRAPHY.caption}`}
                    placeholder="输入测试问题，按回车键发起检索..."
                  />

                  <div className="pr-3 flex items-center">
                    <button
                      onClick={() => setShowConfig(!showConfig)}
                      className={`p-2 rounded-lg transition-all duration-200 ${showConfig ? 'bg-indigo-50 text-indigo-600 ring-1 ring-indigo-200 dark:bg-indigo-500/10 dark:text-indigo-200' : 'text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-600'}`}
                      title="参数配置"
                    >
                      <Sliders size={16} />
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {showConfig && (
              <Card
                padding="none"
                className={`mt-3 ${searchWidthClass} p-1 shadow-xl z-30 animate-in fade-in slide-in-from-top-2 duration-200`}
              >
                <div className="bg-slate-50/50 dark:bg-slate-800/60 rounded-xl p-4 space-y-5">
                  <div className="flex items-center justify-between">
                    <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase tracking-wider flex items-center shrink-0`}>
                      <Globe size={12} className="mr-1.5" /> 检索范围
                    </label>
                    <div className="w-44">
                      <Select
                        value={searchScope}
                        onChange={(value) => setSearchScope(value)}
                        options={searchScopeOptions}
                        dropdownHeader="检索范围"
                        size="xs"
                      />
                    </div>
                  </div>

                  <div className="h-px bg-slate-200/60 dark:bg-slate-700/60"></div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div className="flex items-center justify-between">
                      <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase tracking-wider flex items-center shrink-0`}>
                        <Database size={12} className="mr-1.5" /> 检索模式
                      </label>
                      <div className="w-32">
                        <Select
                          value={retrievalMode}
                          onChange={(value) => setRetrievalMode(value as RetrievalMode)}
                          options={RETRIEVAL_MODE_OPTIONS}
                          dropdownHeader="检索模式"
                          size="xs"
                        />
                      </div>
                    </div>

                    <div className="flex items-center justify-between">
                      <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase tracking-wider flex items-center shrink-0`}>
                        <Layers size={12} className="mr-1.5" /> 重排序
                      </label>
                      <div className="flex items-center space-x-3">
                        {enableRerank && (
                          <span className={`${TYPOGRAPHY.micro} text-emerald-700 bg-emerald-50 dark:bg-emerald-500/10 dark:text-emerald-200 px-1.5 py-0.5 rounded border border-emerald-100 font-mono`}>
                            BGE-Reranker-v2
                          </span>
                        )}
                        <button
                          onClick={() => setEnableRerank(!enableRerank)}
                          className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none ${enableRerank ? 'bg-indigo-600' : 'bg-slate-200 dark:bg-slate-700'}`}
                        >
                          <span className={`inline-block h-3 w-3 transform rounded-full bg-white transition transition-transform duration-200 ${enableRerank ? 'translate-x-5' : 'translate-x-1'}`} />
                        </button>
                      </div>
                    </div>
                  </div>

                  <div className="h-px bg-slate-200/60 dark:bg-slate-700/60"></div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                    <div>
                      <div className="flex justify-between items-center mb-3">
                        <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase`}>Top K</label>
                        <span className={`${TYPOGRAPHY.caption} font-mono font-bold text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 px-2 py-0.5 rounded shadow-sm`}>{topK}</span>
                      </div>
                      <input
                        type="range"
                        min="1"
                        max="20"
                        step="1"
                        value={topK}
                        onChange={(e) => setTopK(parseInt(e.target.value, 10))}
                        className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-slate-900"
                      />
                    </div>
                    <div>
                      <div className="flex justify-between items-center mb-3">
                        <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase`}>Score Threshold</label>
                        <span className={`${TYPOGRAPHY.caption} font-mono font-bold text-slate-900 dark:text-slate-100 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 px-2 py-0.5 rounded shadow-sm`}>{threshold}</span>
                      </div>
                      <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.05"
                        value={threshold}
                        onChange={(e) => setThreshold(parseFloat(e.target.value))}
                        className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-slate-900"
                      />
                    </div>
                  </div>
                </div>
              </Card>
            )}
          </div>
        </div>

        <div className="flex-1 overflow-y-auto pb-10 custom-scrollbar">
          {!results && !isSearching ? (
            <div className={`${resultsWidthClass} text-center mt-10 opacity-0 animate-in fade-in duration-700 slide-in-from-bottom-4 fill-mode-forwards`} style={{ animationDelay: '0.1s' }}>
              <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 mb-4 shadow-sm">
                <Command className="text-slate-300" size={24} />
              </div>
              <p className={`${TYPOGRAPHY.bodySm} text-slate-400`}>准备就绪，输入问题并回车</p>
            </div>
          ) : (
            <div className="space-y-4">
              <div className={`${resultsWidthClass} flex items-center justify-between ${TYPOGRAPHY.bodySm} px-1`}>
                <div className="flex items-center space-x-2">
                  <span className="font-semibold text-slate-700 dark:text-slate-200 flex items-center">
                    召回结果 ({results ? results.length : 0})
                  </span>
                  {searchScope !== 'all' && (
                    <span className={`px-1.5 py-0.5 bg-blue-50 text-blue-600 ${TYPOGRAPHY.micro} rounded border border-blue-100 flex items-center max-w-40 truncate dark:bg-blue-500/10 dark:text-blue-200 dark:border-blue-500/20`}>
                      <FileText size={10} className="mr-1 flex-shrink-0" />
                      {documents.find((doc) => doc.id === searchScope)?.title}
                    </span>
                  )}
                  <span className={`px-1.5 py-0.5 bg-indigo-50 text-indigo-600 ${TYPOGRAPHY.micro} rounded border border-indigo-100 capitalize dark:bg-indigo-500/10 dark:text-indigo-200 dark:border-indigo-500/20`}>
                    {retrievalMode} {enableRerank ? '+ Rerank' : ''}
                  </span>
                </div>
                <span className={`${TYPOGRAPHY.caption} text-slate-400`}>耗时 {latencyMs ?? '-'}ms</span>
              </div>

              {results?.map((result) => (
                <Card
                  key={result.chunkId}
                  padding="none"
                  className={`${resultsWidthClass} p-5 shadow-sm hover:shadow-md group relative overflow-hidden`}
                >
                  <div className={`absolute left-0 top-0 bottom-0 w-1 transition-opacity ${result.similarity > threshold ? 'bg-indigo-500 opacity-100' : 'bg-slate-300 opacity-30'}`}></div>

                  <div className="flex justify-between items-start mb-3">
                    <div className="flex items-center space-x-2">
                      <span className={`px-2 py-1 bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 ${TYPOGRAPHY.micro} font-bold rounded uppercase tracking-wider flex items-center`}>
                        <FileText size={10} className="mr-1" />
                        {result.docTitle}
                      </span>
                      <span className="text-slate-300">/</span>
                      <span className={`${TYPOGRAPHY.caption} text-slate-400 font-mono`}>#{result.chunkId}</span>
                    </div>
                    <div className="flex items-center space-x-3">
                      <div className="text-right">
                        <div className={`${TYPOGRAPHY.micro} text-slate-400 uppercase font-bold`}>Similarity</div>
                        <div className={`${TYPOGRAPHY.bodySm} font-bold font-mono ${result.similarity > 0.8 ? 'text-emerald-600' : 'text-amber-600'}`}>
                          {result.similarity.toFixed(4)}
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className={`bg-slate-50 dark:bg-slate-800 rounded-lg p-3 ${TYPOGRAPHY.bodySm} text-slate-800 dark:text-slate-100 leading-relaxed border border-slate-100 dark:border-slate-700 group-hover:border-indigo-100 group-hover:bg-indigo-50/30 dark:group-hover:bg-indigo-500/10 transition-colors`}>
                    {result.content}
                  </div>

                  <div className="mt-3 flex justify-between items-center opacity-60 group-hover:opacity-100 transition-opacity">
                    <div className="flex space-x-2">
                      <span className={`${TYPOGRAPHY.micro} bg-slate-100 dark:bg-slate-800 px-1.5 py-0.5 rounded text-slate-500 dark:text-slate-300`}>Token ID: 89-124</span>
                    </div>
                    <button className={`${TYPOGRAPHY.caption} text-indigo-600 dark:text-indigo-200 font-medium flex items-center hover:underline`}>
                      上下文预览 <ChevronRight size={12} />
                    </button>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
