/**
 * 知识 Wiki API 服务
 *
 * 对接 /api/ai/knowledge/wiki
 */

import { createApiClient, fetchWithAuthRetry } from '@/lib/api/client'
import type { ApiResponse } from '@/types/api'

export interface PageResult<T> {
  items: T[]
  total: number
  limit: number
  offset: number
}

export interface NamespaceApi {
  namespace_id: number
  namespace: string
  description: string | null
  status: number
  created_by: number
  created_at: string
  updated_at: string
  doc_count?: number
}

export interface DocumentApi {
  document_id: number
  namespace_id: number
  doc_uid?: string | null
  title: string
  file_type: string
  size_bytes: number
  status: string
  chunk_count: number
  token_count: number
  error_message?: string | null
  chunk_mode?: string | null
  chunk_config_json?: Record<string, unknown> | null
  last_chunked_at?: string | null
  created_by?: number
  created_at: string
  updated_at: string
}

export interface UploadResponse {
  document_id: number
  status: string
}

export interface ChunkApi {
  chunk_id: string
  doc_id: string
  doc_title: string
  content: string
  token_count: number
  updated_at: string
  embedding_status: string
}

export interface ChunkJobResponse {
  job_id: number
  status: string
  sse_url?: string | null
}

export interface RetrieveHitApi {
  chunk_id: string
  doc_id: string
  doc_title: string
  score: number
  content: string
}

export interface RetrieveResponseApi {
  hits: RetrieveHitApi[]
  latency_ms: number
}

export interface ChunkConfigPayload {
  chunk_mode?: string | null
  chunk_config_json?: Record<string, unknown> | null
  reembed?: boolean
}

const wikiClient = createApiClient({
  baseURL: '/api/ai/knowledge/wiki',
  timeout: 30000
})

function extractErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as { response?: { data?: { message?: string } } }
    if (axiosError.response?.data?.message) {
      return axiosError.response.data.message
    }
  }
  if (error instanceof Error) {
    return error.message
  }
  return '未知错误'
}

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
  try {
    const response = await wikiClient.get<ApiResponse<NamespaceApi[]>>('/namespaces', {
      params: { limit, offset }
    })
    return buildPageResult(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export async function createNamespace(payload: {
  namespace: string
  description?: string | null
}): Promise<number> {
  try {
    const response = await wikiClient.post<ApiResponse<{ namespace_id: number }>>('/namespaces', payload)
    return response.data.data.namespace_id
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export async function listDocuments(
  namespaceId: number,
  options: { status?: string; keyword?: string; limit?: number; offset?: number } = {}
): Promise<PageResult<DocumentApi>> {
  try {
    const response = await wikiClient.get<ApiResponse<DocumentApi[]>>(
      `/namespaces/${namespaceId}/documents`,
      {
        params: {
          status: options.status,
          keyword: options.keyword,
          limit: options.limit ?? 50,
          offset: options.offset ?? 0
        }
      }
    )
    return buildPageResult(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export async function getDocument(documentId: number): Promise<DocumentApi> {
  try {
    const response = await wikiClient.get<ApiResponse<DocumentApi>>(`/documents/${documentId}`)
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
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

  const response = await fetchWithAuthRetry(`/api/ai/knowledge/wiki/namespaces/${namespaceId}/documents/upload`, {
    method: 'POST',
    body: formData,
    credentials: 'include'
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const payload = (await response.json()) as ApiResponse<UploadResponse>
  if (payload.status !== 200 || payload.code !== 'OK') {
    throw new Error(payload.message || '上传失败')
  }
  return payload.data
}

export async function startChunkJob(
  documentId: number,
  payload: ChunkConfigPayload
): Promise<ChunkJobResponse> {
  try {
    const response = await wikiClient.post<ApiResponse<ChunkJobResponse>>(
      `/documents/${documentId}/chunk`,
      payload
    )
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export async function listChunks(
  documentId: number,
  limit: number = 200,
  offset: number = 0
): Promise<PageResult<ChunkApi>> {
  try {
    const response = await wikiClient.get<ApiResponse<ChunkApi[]>>(
      `/documents/${documentId}/chunks`,
      {
        params: { limit, offset }
      }
    )
    return buildPageResult(response.data)
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export async function updateChunk(chunkId: string, content: string): Promise<ChunkJobResponse> {
  try {
    const response = await wikiClient.patch<ApiResponse<ChunkJobResponse>>(`/chunks/${chunkId}`, { content })
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

export async function deleteChunk(chunkId: string): Promise<number> {
  try {
    const response = await wikiClient.delete<ApiResponse<{ deleted: number }>>(`/chunks/${chunkId}`)
    return response.data.data.deleted
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
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
  try {
    const response = await wikiClient.post<ApiResponse<RetrieveResponseApi>>('/retrieve', payload)
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}
