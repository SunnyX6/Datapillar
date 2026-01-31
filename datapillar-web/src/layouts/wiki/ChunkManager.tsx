import { useEffect, useMemo, useState } from 'react'
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
import { cardWidthClassMap, menuWidthClassMap, panelWidthClassMap, progressWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { Button, Card } from '@/components/ui'
import { toast } from 'sonner'
import { deleteChunk, listChunks, startChunkJob, updateChunk } from '@/services/knowledgeWikiService'
import type { Chunk, Document } from './types'
import { mapChunkToUi } from './utils'

type ChunkMode = 'general' | 'parent_child' | 'qa'

const CHUNK_MODES: ChunkMode[] = ['general', 'parent_child', 'qa']

interface Props {
  spaceId: string
  spaceName: string
  documents: Document[]
}

interface ChunkConfig {
  mode: ChunkMode
  general: {
    maxTokens: number
    overlap: number
    delimiter: string
  }
  parent_child: {
    parent: {
      maxTokens: number
      overlap: number
      delimiter: string
    }
    child: {
      maxTokens: number
      overlap: number
      delimiter: string
    }
  }
  qa: {
    pattern: string
  }
}

export default function ChunkManager({ spaceId, spaceName, documents }: Props) {
  const availableDocs = useMemo(() => documents.filter((doc) => doc.spaceId === spaceId), [documents, spaceId])
  const [selectedDocId, setSelectedDocId] = useState<string>(() => availableDocs[0]?.id ?? '')
  const [selectedChunkId, setSelectedChunkId] = useState<string | null>(null)
  const [editContentByChunkId, setEditContentByChunkId] = useState<Record<string, string>>({})
  const [showConfig, setShowConfig] = useState(false)
  const [chunks, setChunks] = useState<Chunk[]>([])
  const [isChunkLoading, setIsChunkLoading] = useState(false)
  const [isChunking, setIsChunking] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [config, setConfig] = useState<ChunkConfig>({
    mode: 'general',
    general: {
      maxTokens: 800,
      overlap: 120,
      delimiter: '\\n\\n'
    },
    parent_child: {
      parent: {
        maxTokens: 800,
        overlap: 120,
        delimiter: '\\n\\n'
      },
      child: {
        maxTokens: 200,
        overlap: 40,
        delimiter: '\\n\\n'
      }
    },
    qa: {
      pattern: 'Q\\d+:\\s*(.*?)\\s*A\\d+:\\s*([\\s\\S]*?)(?=Q\\d+:|$)'
    }
  })

  const activeDocId = useMemo(() => {
    if (availableDocs.some((doc) => doc.id === selectedDocId)) return selectedDocId
    return availableDocs[0]?.id ?? ''
  }, [availableDocs, selectedDocId])

  const currentDoc = useMemo(
    () => availableDocs.find((doc) => doc.id === activeDocId),
    [availableDocs, activeDocId]
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

  const referenceChunkSize = useMemo(() => {
    if (config.mode === 'general') return config.general.maxTokens
    if (config.mode === 'parent_child') return config.parent_child.child.maxTokens
    return 0
  }, [config])

  const modeSummary = useMemo(() => {
    if (config.mode === 'general') {
      return `${config.general.maxTokens} / ${config.general.overlap}`
    }
    if (config.mode === 'parent_child') {
      return `P:${config.parent_child.parent.maxTokens}/${config.parent_child.parent.overlap} C:${config.parent_child.child.maxTokens}/${config.parent_child.child.overlap}`
    }
    return 'QA'
  }, [config])

  const estimatedChunks = useMemo(() => {
    if (!referenceChunkSize) return 0
    const totalTokens = currentDoc?.tokenCount ?? 0
    if (!totalTokens) return 0
    return Math.ceil(totalTokens / referenceChunkSize)
  }, [currentDoc?.tokenCount, referenceChunkSize])

  const progressWidthClass = useMemo(() => {
    if (!activeChunk) return progressWidthClassMap.low
    const ratio = referenceChunkSize ? activeChunk.tokenCount / referenceChunkSize : 0
    if (ratio >= 0.85) return progressWidthClassMap.high
    if (ratio >= 0.7) return progressWidthClassMap.medium
    return progressWidthClassMap.low
  }, [activeChunk, referenceChunkSize])

  const embeddingSynced = activeChunk?.embeddingStatus === 'synced'

  useEffect(() => {
    if (!activeDocId) {
      setChunks([])
      return
    }
    const documentId = Number(activeDocId)
    if (!Number.isFinite(documentId)) {
      setChunks([])
      return
    }
    setSelectedChunkId(null)
    setIsChunkLoading(true)
    listChunks(documentId)
      .then(({ items }) => {
        setChunks(items.map(mapChunkToUi))
      })
      .catch((error) => {
        const message = error instanceof Error ? error.message : String(error)
        toast.error(`加载切片失败：${message}`)
        setChunks([])
      })
      .finally(() => {
        setIsChunkLoading(false)
      })
  }, [activeDocId])

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

  const buildChunkConfigPayload = () => ({
    chunk_mode: config.mode,
    chunk_config_json: {
      mode: config.mode,
      general: {
        max_tokens: config.general.maxTokens,
        overlap: config.general.overlap,
        delimiter: config.general.delimiter
      },
      parent_child: {
        parent: {
          max_tokens: config.parent_child.parent.maxTokens,
          overlap: config.parent_child.parent.overlap,
          delimiter: config.parent_child.parent.delimiter
        },
        child: {
          max_tokens: config.parent_child.child.maxTokens,
          overlap: config.parent_child.child.overlap,
          delimiter: config.parent_child.child.delimiter
        }
      },
      qa: {
        pattern: config.qa.pattern
      }
    }
  })

  const handleApplyConfig = async () => {
    if (!activeDocId) {
      toast.warning('请先选择文档')
      return
    }
    const documentId = Number(activeDocId)
    if (!Number.isFinite(documentId)) {
      toast.error('文档 ID 无效')
      return
    }
    try {
      setIsChunking(true)
      await startChunkJob(documentId, buildChunkConfigPayload())
      toast.success('切分任务已提交')
      setShowConfig(false)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`切分失败：${message}`)
    } finally {
      setIsChunking(false)
    }
  }

  const handleSaveChunk = async () => {
    if (!activeChunk) return
    const nextContent = editContent.trim()
    if (!nextContent) {
      toast.warning('切片内容不能为空')
      return
    }
    if (nextContent === activeChunk.content) return
    try {
      setIsSaving(true)
      await updateChunk(activeChunk.id, nextContent)
      setChunks((prev) =>
        prev.map((chunk) =>
          chunk.id === activeChunk.id
            ? { ...chunk, content: nextContent, embeddingStatus: 'pending' }
            : chunk
        )
      )
      const key = `${activeChunk.docId}:${activeChunk.id}`
      setEditContentByChunkId((prev) => ({ ...prev, [key]: nextContent }))
      toast.success('切片已更新')
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`更新失败：${message}`)
    } finally {
      setIsSaving(false)
    }
  }

  const handleResetChunk = () => {
    if (!activeChunk) return
    const key = `${activeChunk.docId}:${activeChunk.id}`
    setEditContentByChunkId((prev) => {
      const next = { ...prev }
      delete next[key]
      return next
    })
  }

  const handleDeleteChunk = async () => {
    if (!activeChunk) return
    try {
      setIsDeleting(true)
      const deleted = await deleteChunk(activeChunk.id)
      if (deleted) {
        setChunks((prev) => prev.filter((chunk) => chunk.id !== activeChunk.id))
        setSelectedChunkId(null)
      }
      toast.success('切片已移除')
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      toast.error(`移除失败：${message}`)
    } finally {
      setIsDeleting(false)
    }
  }

  const getModeLabel = (mode: ChunkMode) => {
    switch (mode) {
      case 'general':
        return 'General'
      case 'parent_child':
        return 'Parent/Child'
      case 'qa':
        return 'Q&A'
      default:
        return mode
    }
  }

  return (
    <div className="flex flex-col h-full bg-white dark:bg-slate-900 relative rounded-b-xl">
      <div className="h-14 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between px-5 bg-white dark:bg-slate-900 flex-shrink-0 z-20 shadow-sm relative">
        <div className="flex items-center gap-3 min-w-0">
          <div className={`relative group min-w-0 ${cardWidthClassMap.superWide}`}>
            <button className={`flex items-center ${TYPOGRAPHY.bodySm} font-semibold text-slate-900 dark:text-slate-100 px-2 py-1.5 rounded-lg transition-all text-left`}>
              <span className="flex items-center gap-1.5 min-w-0">
                <FileText size={16} className="text-indigo-600 shrink-0" />
                <span className={`min-w-0 truncate ${currentDoc ? '' : `${TYPOGRAPHY.caption} text-slate-400`}`}>
                  {currentDoc?.title || '暂无文档'}
                </span>
                <ChevronDown size={14} className="text-slate-400 shrink-0" />
              </span>
            </button>

            <div className={`absolute top-full left-0 mt-2 ${menuWidthClassMap.wide} bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-xl opacity-0 group-hover:opacity-100 invisible group-hover:visible transition-all z-50`}>
              <div className="p-2">
                <div className={`${TYPOGRAPHY.micro} text-slate-400 px-2 py-1 uppercase font-bold tracking-wider`}>切换文档</div>
                {availableDocs.length > 0 ? (
                  availableDocs.map((doc) => (
                    <div
                      key={doc.id}
                      onClick={() => handleDocSelect(doc.id)}
                      className={`flex items-center px-3 py-2.5 ${TYPOGRAPHY.bodySm} rounded-lg cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors ${activeDocId === doc.id ? 'bg-indigo-50 text-indigo-700 font-medium dark:bg-indigo-500/10 dark:text-indigo-200' : 'text-slate-700 dark:text-slate-300'}`}
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
            className="flex items-center h-9 px-4 rounded-lg transition-all group"
          >
            <Settings size={16} className={`mr-3 ${showConfig ? 'text-indigo-600' : 'text-slate-400 group-hover:text-slate-600 dark:group-hover:text-slate-200'}`} />

            <div className="flex items-center mr-3">
              <span className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase tracking-wider mr-2`}>切分策略:</span>
              <div className="flex items-center bg-slate-100 dark:bg-slate-800 rounded px-2 py-1">
                <span className={`${TYPOGRAPHY.caption} font-bold text-indigo-700 dark:text-indigo-200 mr-2`}>{getModeLabel(config.mode)}</span>
                <span className="text-slate-300 border-l border-slate-300 dark:border-slate-600 h-3 mx-2"></span>
                <span className={`${TYPOGRAPHY.caption} font-mono text-slate-600 dark:text-slate-300`}>{modeSummary}</span>
              </div>
            </div>

            <ChevronDown size={14} className={`text-slate-400 transition-transform duration-200 ml-1 ${showConfig ? 'rotate-180' : ''}`} />
          </button>

          {showConfig && (
            <div className={`absolute right-0 top-full mt-2 ${menuWidthClassMap.extraWide} bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-xl shadow-2xl z-50 animate-in fade-in slide-in-from-top-2 duration-200`}>
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
                  <label className={`block ${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase mb-2`}>Chunk Mode</label>
                  <div className="grid grid-cols-2 gap-2">
                    {CHUNK_MODES.map((mode) => (
                      <button
                        key={mode}
                        onClick={() => setConfig({ ...config, mode })}
                        className={`px-3 py-2 rounded-lg ${TYPOGRAPHY.caption} font-medium border text-left transition-all ${config.mode === mode ? 'bg-indigo-600 text-white border-indigo-600 shadow-md shadow-indigo-500/20' : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 hover:border-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800'}`}
                      >
                        {getModeLabel(mode)}
                      </button>
                    ))}
                  </div>
                </div>

                {config.mode === 'general' && (
                  <div className="space-y-4">
                    <div>
                      <div className="flex justify-between mb-2">
                        <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase`}>Chunk Length (Chars)</label>
                        <span className={`${TYPOGRAPHY.caption} font-mono font-bold text-indigo-600 bg-indigo-50 dark:bg-indigo-500/10 dark:text-indigo-200 px-2 py-0.5 rounded`}>{config.general.maxTokens}</span>
                      </div>
                      <input
                        type="range"
                        min="128"
                        max="2048"
                        step="64"
                        value={config.general.maxTokens}
                        onChange={(e) => setConfig({ ...config, general: { ...config.general, maxTokens: parseInt(e.target.value, 10) } })}
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
                        <span className={`${TYPOGRAPHY.caption} font-mono font-bold text-blue-600 bg-blue-50 dark:bg-blue-500/10 dark:text-blue-200 px-2 py-0.5 rounded`}>{config.general.overlap}</span>
                      </div>
                      <input
                        type="range"
                        min="0"
                        max="200"
                        step="10"
                        value={config.general.overlap}
                        onChange={(e) => setConfig({ ...config, general: { ...config.general, overlap: parseInt(e.target.value, 10) } })}
                        className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-blue-600"
                      />
                    </div>

                    <div>
                      <label className={`block ${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase mb-2 flex items-center`}>
                        Delimiter <Info size={12} className="ml-1 text-slate-400" />
                      </label>
                      <input
                        type="text"
                        value={config.general.delimiter}
                        onChange={(e) => setConfig({ ...config, general: { ...config.general, delimiter: e.target.value } })}
                        className={`w-full ${TYPOGRAPHY.caption} font-mono bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 outline-none`}
                      />
                    </div>
                  </div>
                )}

                {config.mode === 'parent_child' && (
                  <div className="space-y-5">
                    <div className="space-y-3">
                      <div className={`${TYPOGRAPHY.caption} font-semibold text-slate-600 dark:text-slate-300`}>Parent Chunk</div>
                      <div>
                        <div className="flex justify-between mb-2">
                          <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase`}>Parent Length (Chars)</label>
                          <span className={`${TYPOGRAPHY.caption} font-mono font-bold text-indigo-600 bg-indigo-50 dark:bg-indigo-500/10 dark:text-indigo-200 px-2 py-0.5 rounded`}>{config.parent_child.parent.maxTokens}</span>
                        </div>
                        <input
                          type="range"
                          min="128"
                          max="2048"
                          step="64"
                          value={config.parent_child.parent.maxTokens}
                          onChange={(e) => setConfig({ ...config, parent_child: { ...config.parent_child, parent: { ...config.parent_child.parent, maxTokens: parseInt(e.target.value, 10) } } })}
                          className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-indigo-600"
                        />
                        <div className={`flex justify-between ${TYPOGRAPHY.micro} text-slate-400 mt-1`}>
                          <span>128</span>
                          <span>2048</span>
                        </div>
                      </div>

                      <div>
                        <div className="flex justify-between mb-2">
                          <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase`}>Parent Overlap</label>
                          <span className={`${TYPOGRAPHY.caption} font-mono font-bold text-blue-600 bg-blue-50 dark:bg-blue-500/10 dark:text-blue-200 px-2 py-0.5 rounded`}>{config.parent_child.parent.overlap}</span>
                        </div>
                        <input
                          type="range"
                          min="0"
                          max="200"
                          step="10"
                          value={config.parent_child.parent.overlap}
                          onChange={(e) => setConfig({ ...config, parent_child: { ...config.parent_child, parent: { ...config.parent_child.parent, overlap: parseInt(e.target.value, 10) } } })}
                          className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-blue-600"
                        />
                      </div>

                      <div>
                        <label className={`block ${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase mb-2`}>Parent Delimiter</label>
                        <input
                          type="text"
                          value={config.parent_child.parent.delimiter}
                          onChange={(e) => setConfig({ ...config, parent_child: { ...config.parent_child, parent: { ...config.parent_child.parent, delimiter: e.target.value } } })}
                          className={`w-full ${TYPOGRAPHY.caption} font-mono bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 outline-none`}
                        />
                      </div>
                    </div>

                    <div className="space-y-3">
                      <div className={`${TYPOGRAPHY.caption} font-semibold text-slate-600 dark:text-slate-300`}>Child Chunk</div>
                      <div>
                        <div className="flex justify-between mb-2">
                          <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase`}>Child Length (Chars)</label>
                          <span className={`${TYPOGRAPHY.caption} font-mono font-bold text-indigo-600 bg-indigo-50 dark:bg-indigo-500/10 dark:text-indigo-200 px-2 py-0.5 rounded`}>{config.parent_child.child.maxTokens}</span>
                        </div>
                        <input
                          type="range"
                          min="64"
                          max="1024"
                          step="32"
                          value={config.parent_child.child.maxTokens}
                          onChange={(e) => setConfig({ ...config, parent_child: { ...config.parent_child, child: { ...config.parent_child.child, maxTokens: parseInt(e.target.value, 10) } } })}
                          className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-indigo-600"
                        />
                        <div className={`flex justify-between ${TYPOGRAPHY.micro} text-slate-400 mt-1`}>
                          <span>64</span>
                          <span>1024</span>
                        </div>
                      </div>

                      <div>
                        <div className="flex justify-between mb-2">
                          <label className={`${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase`}>Child Overlap</label>
                          <span className={`${TYPOGRAPHY.caption} font-mono font-bold text-blue-600 bg-blue-50 dark:bg-blue-500/10 dark:text-blue-200 px-2 py-0.5 rounded`}>{config.parent_child.child.overlap}</span>
                        </div>
                        <input
                          type="range"
                          min="0"
                          max="120"
                          step="10"
                          value={config.parent_child.child.overlap}
                          onChange={(e) => setConfig({ ...config, parent_child: { ...config.parent_child, child: { ...config.parent_child.child, overlap: parseInt(e.target.value, 10) } } })}
                          className="w-full h-1.5 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer accent-blue-600"
                        />
                      </div>

                      <div>
                        <label className={`block ${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase mb-2`}>Child Delimiter</label>
                        <input
                          type="text"
                          value={config.parent_child.child.delimiter}
                          onChange={(e) => setConfig({ ...config, parent_child: { ...config.parent_child, child: { ...config.parent_child.child, delimiter: e.target.value } } })}
                          className={`w-full ${TYPOGRAPHY.caption} font-mono bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 outline-none`}
                        />
                      </div>
                    </div>
                  </div>
                )}

                {config.mode === 'qa' && (
                  <div>
                    <label className={`block ${TYPOGRAPHY.legal} font-bold text-slate-500 uppercase mb-2`}>Q&A Pattern</label>
                    <input
                      type="text"
                      value={config.qa.pattern}
                      onChange={(e) => setConfig({ ...config, qa: { ...config.qa, pattern: e.target.value } })}
                      className={`w-full ${TYPOGRAPHY.caption} font-mono bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 outline-none`}
                    />
                  </div>
                )}
              </div>

              <div className="px-4 py-3 bg-slate-50 dark:bg-slate-800/60 border-t border-slate-100 dark:border-slate-800 rounded-b-xl flex justify-between items-center">
                <div className={`${TYPOGRAPHY.micro} text-slate-500`}>
                  预计生成: <span className="font-bold text-slate-900 dark:text-slate-100">~{estimatedChunks} chunks</span>
                </div>
                <Button
                  onClick={handleApplyConfig}
                  variant="primary"
                  size="normal"
                  className={`${TYPOGRAPHY.caption} font-bold`}
                  disabled={isChunking || !activeDocId}
                >
                  应用配置并重新切分
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        <div className={`${panelWidthClassMap.narrow} border-r border-slate-200 dark:border-slate-800 flex flex-col bg-white dark:bg-slate-900 rounded-bl-xl`}>
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
            {isChunkLoading ? (
              <div className={`px-3 py-6 text-center ${TYPOGRAPHY.caption} text-slate-400`}>加载切片中...</div>
            ) : chunks.length === 0 ? (
              <div className={`px-3 py-6 text-center ${TYPOGRAPHY.caption} text-slate-400`}>暂无切片</div>
            ) : (
              chunks.map((chunk, idx) => (
                <div
                  key={chunk.id}
                  onClick={() => handleChunkSelect(chunk)}
                  className={`p-3 border-b border-slate-50 dark:border-slate-800 cursor-pointer transition-all hover:bg-slate-50 dark:hover:bg-slate-800 ${activeChunk?.id === chunk.id ? 'bg-indigo-50/60 dark:bg-indigo-500/10 border-l-4 border-l-indigo-600' : 'border-l-4 border-l-transparent'}`}
                >
                  <div className="flex justify-between items-center mb-1.5">
                    <span className={`${TYPOGRAPHY.micro} font-mono font-medium text-slate-500 bg-slate-100 dark:bg-slate-800 px-1.5 py-0.5 rounded`}>#{idx + 1}</span>
                    <div className="flex items-center space-x-1">
                      <span className={`${TYPOGRAPHY.micro} text-slate-400`}>{chunk.tokenCount}len</span>
                      <span className={`w-1.5 h-1.5 rounded-full ${chunk.embeddingStatus === 'synced' ? 'bg-emerald-400' : 'bg-amber-400'}`} />
                    </div>
                  </div>
                  <p className={`${TYPOGRAPHY.caption} line-clamp-3 leading-relaxed ${activeChunk?.id === chunk.id ? 'text-slate-900 dark:text-slate-100 font-medium' : 'text-slate-500 dark:text-slate-400'}`}>
                    {chunk.content}
                  </p>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="flex-1 flex flex-col bg-slate-50/30 dark:bg-slate-950/40 rounded-br-xl">
          {activeChunk ? (
            <>
              <div className="px-6 py-4 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex justify-between items-center shadow-sm z-10">
                <div>
                  <h2 className={`${TYPOGRAPHY.bodySm} font-bold text-slate-900 dark:text-slate-100 flex items-center`}>
                    切片内容详情
                    <span className={`ml-2 px-2 py-0.5 bg-slate-100 dark:bg-slate-800 ${TYPOGRAPHY.micro} text-slate-500 rounded-full font-normal`}>ID: {activeChunk.id}</span>
                  </h2>
                </div>
                <div className="flex space-x-2">
                  <button
                    onClick={handleDeleteChunk}
                    disabled={isDeleting || !activeChunk}
                    className={`flex items-center px-3 py-1.5 ${TYPOGRAPHY.caption} font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800 rounded-lg transition-colors ${isDeleting ? 'opacity-60 cursor-not-allowed' : ''}`}
                  >
                    <Trash2 size={14} className="mr-1.5" /> 移除
                  </button>
                  <button
                    onClick={handleResetChunk}
                    disabled={!activeChunk}
                    className={`flex items-center px-3 py-1.5 ${TYPOGRAPHY.caption} font-medium text-indigo-600 bg-indigo-50 dark:bg-indigo-500/10 dark:text-indigo-200 hover:bg-indigo-100 border border-indigo-100 dark:border-indigo-500/30 rounded-lg transition-colors ${!activeChunk ? 'opacity-60 cursor-not-allowed' : ''}`}
                  >
                    <RefreshCcw size={14} className="mr-1.5" /> 重置
                  </button>
                </div>
              </div>

              <div className="flex-1 p-8 overflow-y-auto rounded-br-xl">
                <Card
                  padding="none"
                  className="p-1 shadow-sm ring-4 ring-slate-50/50 dark:ring-slate-900/60"
                >
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
                    className={`w-full h-[320px] p-5 ${TYPOGRAPHY.bodySm} text-slate-800 dark:text-slate-100 focus:outline-none resize-none font-mono leading-relaxed bg-white dark:bg-slate-900 rounded-b-lg selection:bg-indigo-100 selection:text-indigo-900`}
                    spellCheck={false}
                  />
                </Card>

                <div className="mt-6 grid grid-cols-2 gap-6">
                  <Card padding="sm" className="shadow-sm">
                    <h4 className={`${TYPOGRAPHY.legal} font-bold text-slate-400 uppercase tracking-wider mb-3`}>Source Metadata</h4>
                    <div className="space-y-3">
                      <div className="flex justify-between items-center pb-2 border-b border-dashed border-slate-100 dark:border-slate-800">
                        <span className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400`}>Source Page</span>
                        <span className={`${TYPOGRAPHY.caption} text-slate-900 dark:text-slate-100 font-mono font-medium`}>Page 4</span>
                      </div>
                      <div className="flex justify-between items-center pb-2 border-b border-dashed border-slate-100 dark:border-slate-800">
                        <span className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400`}>Length Usage</span>
                        <span className={`${TYPOGRAPHY.caption} text-slate-900 dark:text-slate-100 font-mono font-medium`}>{activeChunk.tokenCount} / {referenceChunkSize}</span>
                      </div>
                      <div className="w-full bg-slate-100 dark:bg-slate-800 rounded-full h-1.5 mt-1 overflow-hidden">
                        <div className={`bg-indigo-500 h-1.5 rounded-full ${progressWidthClass}`}></div>
                      </div>
                    </div>
                  </Card>
                  <Card padding="sm" className="shadow-sm">
                    <h4 className={`${TYPOGRAPHY.legal} font-bold text-slate-400 uppercase tracking-wider mb-3`}>Embedding Status</h4>
                    <div className="flex flex-col space-y-2">
                      <div className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400`}>
                        Model: <span className="text-slate-500 dark:text-slate-300 font-mono font-medium">未配置</span>
                      </div>
                      <div className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400`}>
                        Vector ID: <span className="font-mono text-slate-400">-</span>
                      </div>
                      <div className={`mt-2 inline-flex items-center ${TYPOGRAPHY.caption} font-medium px-2 py-1 rounded-md self-start border ${
                        embeddingSynced
                          ? 'text-emerald-600 bg-emerald-50 dark:bg-emerald-500/10 dark:text-emerald-200 border-emerald-100 dark:border-emerald-500/20'
                          : 'text-amber-600 bg-amber-50 dark:bg-amber-500/10 dark:text-amber-200 border-amber-100 dark:border-amber-500/20'
                      }`}
                      >
                        <div className={`w-1.5 h-1.5 rounded-full mr-1.5 ${embeddingSynced ? 'bg-emerald-500' : 'bg-amber-500'}`}></div>
                        {embeddingSynced ? 'Vector Synced' : 'Vector Pending'}
                      </div>
                    </div>
                  </Card>
                </div>
              </div>

              <div className="px-6 py-3 border-t border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex justify-between items-center rounded-br-xl">
                <div className={`flex items-center ${TYPOGRAPHY.caption} text-slate-400`}>
                  <Info size={12} className="mr-1.5" />
                  <span>手动修改内容会触发向量重新计算</span>
                </div>
                <Button
                  variant="primary"
                  size="normal"
                  className={`px-5 py-2 ${TYPOGRAPHY.caption} font-bold hover:-translate-y-0.5`}
                  onClick={handleSaveChunk}
                  disabled={isSaving || !activeChunk}
                >
                  <Save size={14} />
                  保存并更新索引
                </Button>
              </div>
            </>
          ) : (
            <div className="flex flex-col items-center justify-center h-full text-slate-400">
              <div className="w-16 h-16 bg-slate-100 dark:bg-slate-800 rounded-full flex items-center justify-center mb-4">
                <Edit2 size={24} className="text-slate-300" />
              </div>
              <p className={`${TYPOGRAPHY.bodySm} font-medium text-slate-500`}>请从左侧列表选择一个切片</p>
              <p className={`${TYPOGRAPHY.caption} text-slate-400 mt-1`}>您可以查看详情或手动优化内容</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
