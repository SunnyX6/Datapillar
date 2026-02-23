/**
 * 知识 Wiki API 服务
 *
 * 对接 /api/ai/biz/knowledge/wiki
 */

import {
  API_BASE,
  API_PATH,
  requestData,
  requestEnvelope,
  requestUploadData
} from '@/lib/api'
import type { ApiResponse } from '@/types/api'
import type {
  ChunkApi,
  ChunkConfigPayload,
  ChunkJobResponse,
  DocumentApi,
  NamespaceApi,
  PageResult,
  RetrieveResponseApi,
  UploadResponse
} from '@/types/ai/knowledge'

export type {
  ChunkApi,
  ChunkConfigPayload,
  ChunkJobResponse,
  DocumentApi,
  NamespaceApi,
  PageResult,
  RetrieveHitApi,
  RetrieveResponseApi,
  UploadResponse
} from '@/types/ai/knowledge'

function buildPageResult<T>(payload: ApiResponse<T[]>): PageResult<T> {
  return {
    items: payload.data ?? [],
    total: payload.total ?? 0,
    limit: payload.limit ?? 20,
    offset: payload.offset ?? 0
  }
}

export async function listNamespaces(
  limit: number = 50,
  offset: number = 0
): Promise<PageResult<NamespaceApi>> {
  const response = await requestEnvelope<NamespaceApi[]>({
    baseURL: API_BASE.aiKnowledgeWiki,
    url: API_PATH.knowledgeWiki.namespaces,
    params: { limit, offset }
  })
  return buildPageResult(response)
}

export async function createNamespace(payload: {
  namespace: string
  description?: string | null
}): Promise<number> {
  const data = await requestData<{ namespace_id: number }, { namespace: string; description?: string | null }>({
    baseURL: API_BASE.aiKnowledgeWiki,
    url: API_PATH.knowledgeWiki.namespaces,
    method: 'POST',
    data: payload
  })
  return data.namespace_id
}

export async function listDocuments(
  namespaceId: number,
  options: { status?: string; keyword?: string; limit?: number; offset?: number } = {}
): Promise<PageResult<DocumentApi>> {
  const response = await requestEnvelope<DocumentApi[]>({
    baseURL: API_BASE.aiKnowledgeWiki,
    url: API_PATH.knowledgeWiki.namespaceDocuments(namespaceId),
    params: {
      status: options.status,
      keyword: options.keyword,
      limit: options.limit ?? 50,
      offset: options.offset ?? 0
    }
  })
  return buildPageResult(response)
}

export async function getDocument(documentId: number): Promise<DocumentApi> {
  return requestData<DocumentApi>({
    baseURL: API_BASE.aiKnowledgeWiki,
    url: API_PATH.knowledgeWiki.document(documentId)
  })
}

export async function uploadDocument(
  namespaceId: number,
  file: File,
  title?: string
): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  if (title) {
    formData.append('title', title)
  }

  return requestUploadData<UploadResponse>({
    baseURL: API_BASE.aiKnowledgeWiki,
    url: API_PATH.knowledgeWiki.namespaceDocumentsUpload(namespaceId),
    method: 'POST',
    data: formData
  })
}

export async function startChunkJob(
  documentId: number,
  payload: ChunkConfigPayload
): Promise<ChunkJobResponse> {
  return requestData<ChunkJobResponse, ChunkConfigPayload>({
    baseURL: API_BASE.aiKnowledgeWiki,
    url: API_PATH.knowledgeWiki.documentChunk(documentId),
    method: 'POST',
    data: payload
  })
}

export async function listChunks(
  documentId: number,
  limit: number = 200,
  offset: number = 0
): Promise<PageResult<ChunkApi>> {
  const response = await requestEnvelope<ChunkApi[]>({
    baseURL: API_BASE.aiKnowledgeWiki,
    url: API_PATH.knowledgeWiki.documentChunks(documentId),
    params: { limit, offset }
  })
  return buildPageResult(response)
}

export async function updateChunk(chunkId: string, content: string): Promise<ChunkJobResponse> {
  return requestData<ChunkJobResponse, { content: string }>({
    baseURL: API_BASE.aiKnowledgeWiki,
    url: API_PATH.knowledgeWiki.chunk(chunkId),
    method: 'PATCH',
    data: { content }
  })
}

export async function deleteChunk(chunkId: string): Promise<number> {
  const data = await requestData<{ deleted: number }>({
    baseURL: API_BASE.aiKnowledgeWiki,
    url: API_PATH.knowledgeWiki.chunk(chunkId),
    method: 'DELETE'
  })
  return data.deleted
}

export async function retrieve(payload: {
  namespace_id: number
  query: string
  search_scope?: string
  document_ids?: Array<string | number>
  retrieval_mode?: string
  rerank_enabled?: boolean
  rerank_model?: string | null
  top_k?: number
  score_threshold?: number | null
}): Promise<RetrieveResponseApi> {
  return requestData<RetrieveResponseApi, {
    namespace_id: number
    query: string
    search_scope?: string
    document_ids?: Array<string | number>
    retrieval_mode?: string
    rerank_enabled?: boolean
    rerank_model?: string | null
    top_k?: number
    score_threshold?: number | null
  }>({
    baseURL: API_BASE.aiKnowledgeWiki,
    url: API_PATH.knowledgeWiki.retrieve,
    method: 'POST',
    data: payload
  })
}
