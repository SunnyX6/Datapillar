import { useMemo, useState } from 'react'
import {
  Edit2,
  Save,
  Trash2,
  RefreshCcw,
  FileText,
  ChevronDown,
  Search,
  Settings,
  Sliders,
  Info,
  X
} from 'lucide-react'
import { cardWidthClassMap, progressWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import type { Chunk, Document } from './types'

const mockDocuments: Document[] = [
  { id: '1', spaceId: 'ks1', title: 'DataButterfly_API_v2.0.pdf', type: 'pdf', size: '2.4 MB', uploadDate: '2023-10-24', status: 'indexed', chunkCount: 142, tokenCount: 45000 },
  { id: '2', spaceId: 'ks1', title: 'System_Architecture.docx', type: 'docx', size: '1.2 MB', uploadDate: '2023-10-25', status: 'indexed', chunkCount: 85, tokenCount: 22000 },
  { id: '3', spaceId: 'ks2', title: 'Product_Design_Notes.md', type: 'md', size: '48 KB', uploadDate: '2023-10-27', status: 'indexed', chunkCount: 96, tokenCount: 18000 }
]

const mockChunks: Record<string, Chunk[]> = {
  '1': [
    { id: 'c1', docId: '1', docTitle: 'DataButterfly_API_v2.0.pdf', content: 'Rate Limits: The API is limited to 1000 requests per minute per IP address. Exceeding this limit will result in a 429 Too Many Requests response.', tokenCount: 42, lastModified: '10 mins ago', embeddingStatus: 'synced' },
    { id: 'c2', docId: '1', docTitle: 'DataButterfly_API_v2.0.pdf', content: 'Authentication: All requests must be authenticated using a Bearer Token in the Authorization header.', tokenCount: 38, lastModified: '12 mins ago', embeddingStatus: 'synced' },
    { id: 'c3', docId: '1', docTitle: 'DataButterfly_API_v2.0.pdf', content: 'Pagination: Endpoints returning lists of resources support pagination via cursor-based parameters.', tokenCount: 45, lastModified: '15 mins ago', embeddingStatus: 'synced' }
  ],
  '2': [
    { id: 'c4', docId: '2', docTitle: 'System_Architecture.docx', content: 'The system uses a microservices architecture based on Kubernetes.', tokenCount: 20, lastModified: '1 day ago', embeddingStatus: 'synced' }
  ],
  '3': [
    { id: 'c5', docId: '3', docTitle: 'Product_Design_Notes.md', content: 'Primary navigation emphasizes knowledge space context before artifact operations.', tokenCount: 26, lastModified: '2 days ago', embeddingStatus: 'pending' }
  ]
}

type ChunkMethod = 'recursive' | 'token' | 'markdown' | 'semantic'

const CHUNK_METHODS: ChunkMethod[] = ['recursive', 'token', 'markdown', 'semantic']

interface Props {
  spaceId: string
  spaceName: string
}

interface ChunkConfig {
  method: ChunkMethod
  chunkSize: number
  overlap: number
  separators: string
}

export default function ChunkManager({ spaceId, spaceName }: Props) {
  const availableDocs = useMemo(() => mockDocuments.filter((doc) => doc.spaceId === spaceId), [spaceId])
  const [selectedDocId, setSelectedDocId] = useState<string>(() => availableDocs[0]?.id ?? '')
  const [selectedChunkId, setSelectedChunkId] = useState<string | null>(null)
  const [editContentByChunkId, setEditContentByChunkId] = useState<Record<string, string>>({})
  const [showConfig, setShowConfig] = useState(false)
  const [config, setConfig] = useState<ChunkConfig>({
    method: 'recursive',
    chunkSize: 512,
    overlap: 50,
    separators: '\\n\\n, \\n, " ", ""'
  })

  const activeDocId = useMemo(() => {
    if (availableDocs.some((doc) => doc.id === selectedDocId)) return selectedDocId
    return availableDocs[0]?.id ?? ''
  }, [availableDocs, selectedDocId])

  const currentDoc = useMemo(
    () => availableDocs.find((doc) => doc.id === activeDocId),
    [availableDocs, activeDocId]
  )

  const chunks = useMemo(
    () => (activeDocId ? mockChunks[activeDocId] || [] : []),
    [activeDocId]
  )

  const activeChunk = useMemo(() => {
    if (chunks.length === 0) return null
    if (selectedChunkId) {
      const match = chunks.find((chunk) => chunk.id === selectedChunkId)
      if (match) return match
    }
    return chunks[0]
  }, [chunks, selectedChunkId])

  const editContentKey = activeChunk ? `${activeChunk.docId}:${activeChunk.id}` : ''
  const editContent = activeChunk
    ? editContentByChunkId[editContentKey] ?? activeChunk.content
    : ''

  const progressWidthClass = useMemo(() => {
    if (!activeChunk) return progressWidthClassMap.low
    const ratio = activeChunk.tokenCount / config.chunkSize
    if (ratio >= 0.85) return progressWidthClassMap.high
    if (ratio >= 0.7) return progressWidthClassMap.medium
    return progressWidthClassMap.low
  }, [activeChunk, config.chunkSize])

  const handleChunkSelect = (chunk: Chunk) => {
    setSelectedChunkId(chunk.id)
  }

  const handleDocSelect = (docId: string) => {
    setSelectedDocId(docId)
    setSelectedChunkId(null)
  }

  const handleEditContentChange = (value: string) => {
    if (!activeChunk) return
    const key = `${activeChunk.docId}:${activeChunk.id}`
    setEditContentByChunkId((current) => ({ ...current, [key]: value }))
  }

  const getMethodLabel = (method: ChunkMethod) => {
    switch (method) {
      case 'recursive':
        return 'Recursive'
      case 'token':
        return 'Fixed Token'
      case 'markdown':
        return 'Markdown'
      case 'semantic':
        return 'Semantic'
      default:
        return method
    }
  }

  return (
    <div className="flex flex-col h-full bg-white dark:bg-slate-900 relative rounded-b-xl">
      <div className="h-16 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between px-6 bg-white dark:bg-slate-900 flex-shrink-0 z-20 shadow-sm relative">
        <div className="flex items-center gap-3 min-w-0">
          <div className={`relative group min-w-0 ${cardWidthClassMap.superWide}`}>
            <button className={`flex items-center ${TYPOGRAPHY.body} font-semibold text-slate-900 dark:text-slate-100 px-2 py-1.5 rounded-lg transition-all text-left`}>
              <span className="flex items-center gap-1.5 min-w-0">
                <FileText size={16} className="text-indigo-600 shrink-0" />
                <span className={`min-w-0 truncate ${currentDoc ? '' : `${TYPOGRAPHY.caption} text-slate-400`}`}>
                  {currentDoc?.title || '暂无文档'}
                </span>
                <ChevronDown size={14} className="text-slate-400 shrink-0" />
              </span>
            </button>

            <div className="absolute top-full left-0 mt-2 w-72 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-xl opacity-0 group-hover:opacity-100 invisible group-hover:visible transition-all z-50">
              <div className="p-2">
                <div className={`${TYPOGRAPHY.micro} text-slate-400 px-2 py-1 uppercase font-bold tracking-wider`}>切换文档</div>
                {availableDocs.length > 0 ? (
                  availableDocs.map((doc) => (
                    <div
                      key={doc.id}
                      onClick={() => handleDocSelect(doc.id)}
                      className={`flex items-center px-3 py-2.5 ${TYPOGRAPHY.body} rounded-lg cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors ${activeDocId === doc.id ? 'bg-indigo-50 text-indigo-700 font-medium dark:bg-indigo-500/10 dark:text-indigo-200' : 'text-slate-700 dark:text-slate-300'}`}
                    >
                      <FileText size={16} className={`mr-3 ${activeDocId === doc.id ? 'text-indigo-500' : 'text-slate-400'}`} />
                      <span className="truncate">{doc.title}</span>
                    </div>
                  ))
                ) : (
                  <div className={`px-3 py-2 ${TYPOGRAPHY.caption} text-slate-400`}>当前空间暂无文档</div>
                )}
              </div>
            </div>
          </div>

          <div className="h-6 w-px bg-slate-200 dark:bg-slate-700 mx-2"></div>

          <div className="flex flex-col">
            <span className={`${TYPOGRAPHY.micro} text-slate-400 uppercase tracking-wider font-bold`}>Total Chunks</span>
            <span className={`${TYPOGRAPHY.bodySm} font-mono font-medium text-slate-700 dark:text-slate-200`}>{currentDoc?.chunkCount ?? 0}</span>
          </div>

          <div className="flex flex-col pl-4 border-l border-slate-200 dark:border-slate-700">
            <span className={`${TYPOGRAPHY.micro} text-slate-400 uppercase tracking-wider font-bold`}>Current Space</span>
            <span className={`${TYPOGRAPHY.caption} font-medium text-slate-600 dark:text-slate-300`}>{spaceName}</span>
          </div>
        </div>

        <div className="relative">
          <button
            onClick={() => setShowConfig(!showConfig)}
            className="flex items-center h-10 px-4 rounded-lg transition-all group"
          >
            <Settings size={16} className={`mr-3 ${showConfig ? 'text-indigo-600' : 'text-slate-400 group-hover:text-slate-600 dark:group-hover:text-slate-200'}`} />

            <div className="flex items-center mr-3">
              <span className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase tracking-wider mr-2`}>切分策略:</span>
              <div className="flex items-center bg-slate-100 dark:bg-slate-800 rounded px-2 py-1">
                <span className={`${TYPOGRAPHY.caption} font-bold text-indigo-700 dark:text-indigo-200 mr-2`}>{getMethodLabel(config.method)}</span>
                <span className="text-slate-300 border-l border-slate-300 dark:border-slate-600 h-3 mx-2"></span>
                <span className={`${TYPOGRAPHY.caption} font-mono text-slate-600 dark:text-slate-300`}>{config.chunkSize}</span>
                <span className={`text-slate-300 ${TYPOGRAPHY.caption} mx-1`}>/</span>
                <span className={`${TYPOGRAPHY.caption} font-mono text-slate-600 dark:text-slate-300`}>{config.overlap}</span>
              </div>
            </div>

            <ChevronDown size={14} className={`text-slate-400 transition-transform duration-200 ml-1 ${showConfig ? 'rotate-180' : ''}`} />
          </button>

          {showConfig && (
            <div className="absolute right-0 top-full mt-2 w-96 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-2xl z-50 animate-in fade-in slide-in-from-top-2 duration-200">
              <div className="px-4 py-3 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center bg-slate-50/50 dark:bg-slate-800/40 rounded-t-xl">
                <div className={`flex items-center space-x-2 ${TYPOGRAPHY.caption} text-slate-800 dark:text-slate-100 font-semibold`}>
                  <Sliders size={16} />
                  <span>切分参数配置</span>
                </div>
                <button onClick={() => setShowConfig(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200">
                  <X size={16} />
                </button>
              </div>

              <div className="p-4 space-y-4">
                <div>
                  <label className={`block ${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase mb-2`}>Splitter Method</label>
                  <div className="grid grid-cols-2 gap-2">
                    {CHUNK_METHODS.map((method) => (
                      <button
                        key={method}
                        onClick={() => setConfig({ ...config, method })}
                        className={`px-3 py-2 rounded-lg ${TYPOGRAPHY.caption} font-medium border text-left transition-all ${config.method === method ? 'bg-indigo-600 text-white border-indigo-600 shadow-md shadow-indigo-500/20' : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 hover:border-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800'}`}
                      >
                        {getMethodLabel(method)}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="space-y-4">
                  <div>
                    <div className="flex justify-between mb-2">
                      <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase`}>Chunk Size (Tokens)</label>
                      <span className={`${TYPOGRAPHY.caption} font-mono font-bold text-indigo-600 bg-indigo-50 dark:bg-indigo-500/10 dark:text-indigo-200 px-2 py-0.5 rounded`}>{config.chunkSize}</span>
                    </div>
                    <input
                      type="range"
                      min="128"
                      max="2048"
                      step="64"
                      value={config.chunkSize}
                      onChange={(e) => setConfig({ ...config, chunkSize: parseInt(e.target.value, 10) })}
                      className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-indigo-600"
                    />
                    <div className={`flex justify-between ${TYPOGRAPHY.micro} text-slate-400 mt-1`}>
                      <span>128</span>
                      <span>2048</span>
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between mb-2">
                      <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase`}>Overlap</label>
                      <span className={`${TYPOGRAPHY.caption} font-mono font-bold text-blue-600 bg-blue-50 dark:bg-blue-500/10 dark:text-blue-200 px-2 py-0.5 rounded`}>{config.overlap}</span>
                    </div>
                    <input
                      type="range"
                      min="0"
                      max="200"
                      step="10"
                      value={config.overlap}
                      onChange={(e) => setConfig({ ...config, overlap: parseInt(e.target.value, 10) })}
                      className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-blue-600"
                    />
                  </div>
                </div>

                {config.method === 'recursive' && (
                  <div>
                    <label className={`block ${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase mb-2 flex items-center`}>
                      Separators <Info size={12} className="ml-1 text-slate-400" />
                    </label>
                    <input
                      type="text"
                      value={config.separators}
                      onChange={(e) => setConfig({ ...config, separators: e.target.value })}
                      className={`w-full ${TYPOGRAPHY.caption} font-mono bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 outline-none`}
                    />
                  </div>
                )}
              </div>

            <div className="px-4 py-3 bg-slate-50 dark:bg-slate-800/60 border-t border-slate-100 dark:border-slate-800 rounded-b-xl flex justify-between items-center">
              <div className={`${TYPOGRAPHY.micro} text-slate-500`}>
                预计生成: <span className="font-bold text-slate-900 dark:text-slate-100">~{Math.ceil((currentDoc?.tokenCount || 0) / config.chunkSize)} chunks</span>
              </div>
              <button
                onClick={() => setShowConfig(false)}
                className={`px-4 py-2 bg-slate-900 hover:bg-black text-white ${TYPOGRAPHY.caption} font-bold rounded-lg transition-colors shadow-sm`}
              >
                应用配置并重新切分
              </button>
            </div>
            </div>
          )}
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        <div className="w-80 border-r border-slate-200 dark:border-slate-800 flex flex-col bg-white dark:bg-slate-900 rounded-bl-xl">
          <div className="p-3 border-b border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900">
            <div className="relative group">
              <Search className="absolute left-2.5 top-2.5 text-slate-400 group-focus-within:text-indigo-500 transition-colors" size={14} />
              <input
                type="text"
                placeholder="搜索切片..."
                className={`w-full pl-9 pr-3 py-2 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg ${TYPOGRAPHY.bodySm} focus:outline-none focus:border-indigo-400 focus:bg-white dark:focus:bg-slate-900 transition-all placeholder-slate-400`}
              />
            </div>
          </div>
          <div className="flex-1 overflow-y-auto custom-scrollbar rounded-bl-xl">
            {chunks.map((chunk, idx) => (
              <div
                key={chunk.id}
                onClick={() => handleChunkSelect(chunk)}
                className={`p-4 border-b border-slate-50 dark:border-slate-800 cursor-pointer transition-all hover:bg-slate-50 dark:hover:bg-slate-800 ${activeChunk?.id === chunk.id ? 'bg-indigo-50/60 dark:bg-indigo-500/10 border-l-4 border-l-indigo-600' : 'border-l-4 border-l-transparent'}`}
              >
                <div className="flex justify-between items-center mb-1.5">
                  <span className={`${TYPOGRAPHY.micro} font-mono font-medium text-slate-500 bg-slate-100 dark:bg-slate-800 px-1.5 py-0.5 rounded`}>#{idx + 1}</span>
                  <div className="flex items-center space-x-1">
                    <span className={`${TYPOGRAPHY.micro} text-slate-400`}>{chunk.tokenCount}t</span>
                    <span className={`w-1.5 h-1.5 rounded-full ${chunk.embeddingStatus === 'synced' ? 'bg-emerald-400' : 'bg-amber-400'}`} />
                  </div>
                </div>
                <p className={`${TYPOGRAPHY.caption} line-clamp-3 leading-relaxed ${activeChunk?.id === chunk.id ? 'text-slate-900 dark:text-slate-100 font-medium' : 'text-slate-500 dark:text-slate-400'}`}>
                  {chunk.content}
                </p>
              </div>
            ))}
          </div>
        </div>

        <div className="flex-1 flex flex-col bg-slate-50/30 dark:bg-slate-950/40 rounded-br-xl">
          {activeChunk ? (
            <>
              <div className="px-8 py-5 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex justify-between items-center shadow-sm z-10">
                <div>
                  <h2 className={`${TYPOGRAPHY.body} font-bold text-slate-900 dark:text-slate-100 flex items-center`}>
                    切片内容详情
                    <span className={`ml-2 px-2 py-0.5 bg-slate-100 dark:bg-slate-800 ${TYPOGRAPHY.micro} text-slate-500 rounded-full font-normal`}>ID: {activeChunk.id}</span>
                  </h2>
                </div>
                <div className="flex space-x-2">
                  <button className={`flex items-center px-3 py-1.5 ${TYPOGRAPHY.caption} font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 rounded-lg transition-colors`}>
                    <Trash2 size={14} className="mr-1.5" /> 移除
                  </button>
                  <button className={`flex items-center px-3 py-1.5 ${TYPOGRAPHY.caption} font-medium text-indigo-600 bg-indigo-50 dark:bg-indigo-500/10 dark:text-indigo-200 hover:bg-indigo-100 border border-indigo-100 dark:border-indigo-500/30 rounded-lg transition-colors`}>
                    <RefreshCcw size={14} className="mr-1.5" /> 重置
                  </button>
                </div>
              </div>

              <div className="flex-1 p-8 overflow-y-auto rounded-br-xl">
                <div className="bg-white dark:bg-slate-900 p-1 rounded-xl shadow-sm border border-slate-200 dark:border-slate-800 ring-4 ring-slate-50/50 dark:ring-slate-900/60">
                  <div className="bg-slate-50 dark:bg-slate-800 px-4 py-2 border-b border-slate-100 dark:border-slate-700 rounded-t-lg flex justify-between items-center">
                    <div className="flex items-center space-x-2">
                      <div className="w-2 h-2 rounded-full bg-indigo-400 animate-pulse"></div>
                      <span className={`${TYPOGRAPHY.legal} font-bold text-slate-600 dark:text-slate-300 uppercase tracking-wide`}>Editor</span>
                    </div>
                    <span className={`${TYPOGRAPHY.micro} text-slate-400 font-mono border border-slate-200 dark:border-slate-700 px-1.5 py-0.5 rounded bg-white dark:bg-slate-900`}>{editContent.length} chars</span>
                  </div>
                  <textarea
                    value={editContent}
                    onChange={(e) => handleEditContentChange(e.target.value)}
                    className={`w-full h-[320px] p-6 ${TYPOGRAPHY.body} text-slate-800 dark:text-slate-100 focus:outline-none resize-none font-mono leading-relaxed bg-white dark:bg-slate-900 rounded-b-lg selection:bg-indigo-100 selection:text-indigo-900`}
                    spellCheck={false}
                  />
                </div>

                <div className="mt-6 grid grid-cols-2 gap-6">
                  <div className="bg-white dark:bg-slate-900 p-5 rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm">
                    <h4 className={`${TYPOGRAPHY.legal} font-bold text-slate-400 uppercase tracking-wider mb-3`}>Source Metadata</h4>
                    <div className="space-y-3">
                      <div className="flex justify-between items-center pb-2 border-b border-dashed border-slate-100 dark:border-slate-800">
                        <span className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400`}>Source Page</span>
                        <span className={`${TYPOGRAPHY.caption} text-slate-900 dark:text-slate-100 font-mono font-medium`}>Page 4</span>
                      </div>
                      <div className="flex justify-between items-center pb-2 border-b border-dashed border-slate-100 dark:border-slate-800">
                        <span className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400`}>Token Usage</span>
                        <span className={`${TYPOGRAPHY.caption} text-slate-900 dark:text-slate-100 font-mono font-medium`}>{activeChunk.tokenCount} / {config.chunkSize}</span>
                      </div>
                      <div className="w-full bg-slate-100 dark:bg-slate-800 rounded-full h-1.5 mt-1 overflow-hidden">
                        <div className={`bg-indigo-500 h-1.5 rounded-full ${progressWidthClass}`}></div>
                      </div>
                    </div>
                  </div>
                  <div className="bg-white dark:bg-slate-900 p-5 rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm">
                    <h4 className={`${TYPOGRAPHY.legal} font-bold text-slate-400 uppercase tracking-wider mb-3`}>Embedding Status</h4>
                    <div className="flex flex-col space-y-2">
                      <div className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400`}>
                        Model: <span className="text-indigo-600 dark:text-indigo-200 font-mono font-medium">text-embedding-3-large</span>
                      </div>
                      <div className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400`}>
                        Vector ID: <span className="font-mono text-slate-400">vec_8923...1a2</span>
                      </div>
                      <div className={`mt-2 inline-flex items-center ${TYPOGRAPHY.caption} font-medium text-emerald-600 bg-emerald-50 dark:bg-emerald-500/10 dark:text-emerald-200 px-2 py-1 rounded-md self-start border border-emerald-100 dark:border-emerald-500/20`}>
                        <div className="w-1.5 h-1.5 bg-emerald-500 rounded-full mr-1.5"></div>
                        Vector Synced
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="px-8 py-4 border-t border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex justify-between items-center rounded-br-xl">
                <div className={`flex items-center ${TYPOGRAPHY.caption} text-slate-400`}>
                  <Info size={12} className="mr-1.5" />
                  <span>手动修改内容会触发向量重新计算</span>
                </div>
                <button className={`flex items-center px-6 py-2.5 bg-slate-900 hover:bg-black text-white ${TYPOGRAPHY.caption} font-bold rounded-lg shadow-lg shadow-slate-200 transition-all transform hover:-translate-y-0.5`}>
                  <Save size={14} className="mr-2" />
                  保存并更新索引
                </button>
              </div>
            </>
          ) : (
            <div className="flex flex-col items-center justify-center h-full text-slate-400">
              <div className="w-16 h-16 bg-slate-100 dark:bg-slate-800 rounded-full flex items-center justify-center mb-4">
                <Edit2 size={24} className="text-slate-300" />
              </div>
              <p className={`${TYPOGRAPHY.body} font-medium text-slate-500`}>请从左侧列表选择一个切片</p>
              <p className={`${TYPOGRAPHY.caption} text-slate-400 mt-1`}>您可以查看详情或手动优化内容</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
