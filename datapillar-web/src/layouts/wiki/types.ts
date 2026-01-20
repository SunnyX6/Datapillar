export type WikiTab = 'DOCUMENTS' | 'CHUNKS' | 'RETRIEVAL_TEST'

export interface KnowledgeSpace {
  id: string
  name: string
  description: string
  docCount: number
  color: string
}

export type DocumentType = 'pdf' | 'docx' | 'md' | 'txt'
export type DocumentStatus = 'indexed' | 'processing' | 'error'

export interface Document {
  id: string
  spaceId: string
  title: string
  type: DocumentType
  size: string
  uploadDate: string
  status: DocumentStatus
  chunkCount: number
  tokenCount: number
}

export type EmbeddingStatus = 'synced' | 'pending'

export interface Chunk {
  id: string
  docId: string
  docTitle: string
  content: string
  tokenCount: number
  lastModified: string
  embeddingStatus: EmbeddingStatus
}

export interface SearchResult {
  chunkId: string
  docTitle: string
  similarity: number
  content: string
}
