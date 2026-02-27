import { formatTime } from '@/utils'
import type { Chunk, Document, KnowledgeSpace, SearchResult } from './types'

interface NamespaceSource {
  namespace_id: number | string
  namespace: string
  description?: string | null
  doc_count?: number
}

interface DocumentSource {
  document_id: number | string
  namespace_id: number | string
  title: string
  file_type?: string | null
  size_bytes?: number
  created_at?: string | null
  status?: string
  chunk_count?: number
  token_count?: number
}

interface ChunkSource {
  chunk_id: string
  doc_id: string
  doc_title: string
  content: string
  token_count?: number
  updated_at?: string | null
  embedding_status?: string
}

interface RetrieveHitSource {
  chunk_id: string
  doc_title: string
  score: number
  content: string
}

export const SPACE_COLOR_PALETTE = [
  'bg-indigo-500',
  'bg-rose-500',
  'bg-emerald-500',
  'bg-amber-500',
  'bg-sky-500',
  'bg-violet-500',
  'bg-teal-500'
] as const

export const normalizeSpaceName = (name: string) => name.trim().toLowerCase()

export const isSpaceNameUnique = (spaces: KnowledgeSpace[], name: string) => {
  const normalized = normalizeSpaceName(name)
  if (!normalized) return true
  return !spaces.some((space) => normalizeSpaceName(space.name) === normalized)
}

export const getNamespaceFormStatus = (spaces: KnowledgeSpace[], name: string) => {
  const trimmedName = name.trim()
  const isNameUnique = isSpaceNameUnique(spaces, trimmedName)
  const showNameError = trimmedName.length > 0 && !isNameUnique
  const canCreateSpace = trimmedName.length > 0 && isNameUnique
  return {
    trimmedName,
    isNameUnique,
    showNameError,
    canCreateSpace
  }
}

export const getNextSpaceId = (spaces: KnowledgeSpace[]) => {
  const used = new Set(spaces.map((space) => space.id))
  let index = 1
  while (used.has(`ks${index}`)) {
    index += 1
  }
  return `ks${index}`
}

export const getNextSpaceColor = (
  spaces: KnowledgeSpace[],
  palette: readonly string[] = SPACE_COLOR_PALETTE
) => {
  if (palette.length === 0) return 'bg-slate-500'
  const used = new Set(spaces.map((space) => space.color))
  const nextColor = palette.find((color) => !used.has(color))
  if (nextColor) return nextColor
  return palette[spaces.length % palette.length]
}

export const inferDocumentTypeFromName = (name: string): Document['type'] => {
  const normalized = name.trim().toLowerCase()
  if (normalized.endsWith('.pdf')) return 'pdf'
  if (normalized.endsWith('.docx')) return 'docx'
  if (normalized.endsWith('.md')) return 'md'
  if (normalized.endsWith('.txt')) return 'txt'
  return 'txt'
}

export const getFileNameFromUrl = (url: string) => {
  const trimmed = url.trim()
  if (!trimmed) return ''
  try {
    const parsed = new URL(trimmed)
    const segments = parsed.pathname.split('/').filter(Boolean)
    if (segments.length > 0) {
      const tail = segments[segments.length - 1]
      if (tail.includes('.')) {
        return tail
      }
    }
    return parsed.hostname
  } catch {
    return trimmed
  }
}

export const formatBytes = (bytes?: number): string => {
  if (!bytes || Number.isNaN(bytes)) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

export const normalizeDocumentStatus = (status?: string): Document['status'] => {
  if (status === 'indexed' || status === 'processing' || status === 'error') return status
  return 'processing'
}

const normalizeDocumentType = (fileType?: string, title?: string): Document['type'] => {
  const normalized = (fileType || '').trim().toLowerCase()
  if (normalized === 'pdf' || normalized === 'docx' || normalized === 'md' || normalized === 'txt') {
    return normalized
  }
  if (title) return inferDocumentTypeFromName(title)
  return 'txt'
}

export const mapNamespaceToSpace = (item: NamespaceSource, color: string): KnowledgeSpace => {
  return {
    id: String(item.namespace_id),
    name: item.namespace,
    description: item.description ?? '',
    docCount: item.doc_count ?? 0,
    color
  }
}

export const mapDocumentToUi = (item: DocumentSource): Document => {
  return {
    id: String(item.document_id),
    spaceId: String(item.namespace_id),
    title: item.title,
    type: normalizeDocumentType(item.file_type, item.title),
    size: formatBytes(item.size_bytes),
    uploadDate: formatTime(item.created_at),
    status: normalizeDocumentStatus(item.status),
    chunkCount: item.chunk_count ?? 0,
    tokenCount: item.token_count ?? 0
  }
}

export const mapChunkToUi = (item: ChunkSource): Chunk => {
  return {
    id: item.chunk_id,
    docId: item.doc_id,
    docTitle: item.doc_title,
    content: item.content,
    tokenCount: item.token_count ?? 0,
    lastModified: formatTime(item.updated_at),
    embeddingStatus: item.embedding_status === 'synced' ? 'synced' : 'pending'
  }
}

export const mapSearchHitToResult = (hit: RetrieveHitSource): SearchResult => {
  return {
    chunkId: hit.chunk_id,
    docTitle: hit.doc_title,
    similarity: hit.score,
    content: hit.content
  }
}
