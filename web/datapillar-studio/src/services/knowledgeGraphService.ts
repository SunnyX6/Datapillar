/**
 * Knowledge graph API service
 *
 * Knowledge graph uses non-SSE interfaces (one-shot JSON responses).
 */

import { API_BASE, API_PATH, requestRaw } from '@/api'
import { useAuthStore } from '@/state/authStore'
import type {
  GraphData,
  GraphLink,
  GraphNode,
  Neo4jNode,
  Neo4jRelationship
} from '@/services/types/ai/knowledge'

export type {
  GraphData,
  GraphLink,
  GraphNode
} from '@/services/types/ai/knowledge'

interface OpenLineageSearchResponse {
  tenantId: number
  aiModelId: number
  revision: number
  nodes: Neo4jNode[]
}

interface OpenLineageRebuildResponse {
  status: string
  tenantId: number
  aiModelId: number
  revision: number
  graphUpserts: number
  embeddingTasks: number
}

interface OpenLineageSetEmbeddingResponse {
  tenantId: number
  scope: string
  aiModelId: number
  revision: number
  setBy: number
  setAt: string
}

function resolveTenantId(tenantId?: number): number {
  if (tenantId && tenantId > 0) {
    return tenantId
  }
  const fromStore = useAuthStore.getState().user?.tenantId
  if (!fromStore || fromStore <= 0) {
    throw new Error('Current tenant is missing')
  }
  return fromStore
}

/**
 * Convert Neo4j node to frontend node model.
 */
function transformNode(node: Neo4jNode): GraphNode {
  const { id, type, level, properties } = node
  const UNIFIED_VAL = 25
  const name = typeof properties.name === 'string' ? properties.name : String(properties.name ?? '')

  return {
    id: String(id),
    type,
    level: level ?? 99,
    group: 0,
    name: name || String(id),
    displayName: properties.displayName as string | undefined,
    val: UNIFIED_VAL,
    health: 'healthy',
    description: properties.description as string | undefined,
    owner: properties.owner as string | undefined,
    tags: properties.tags as string[] | undefined,
    lastUpdated: properties.updatedAt as string | undefined
  }
}

/**
 * Convert Neo4j relationship to frontend relationship model.
 */
function transformRelationship(rel: Neo4jRelationship): GraphLink {
  return {
    source: String(rel.start),
    target: String(rel.end),
    type: rel.type,
    value: 1
  }
}

/**
 * Fetch initial graph data.
 */
export async function fetchInitialGraph(
  limit: number = 500,
  onProgress?: (current: number, total: number) => void,
  tenantId?: number
): Promise<GraphData> {
  const resolvedTenantId = resolveTenantId(tenantId)
  const data = await requestRaw<
    { nodes: Neo4jNode[]; relationships: Neo4jRelationship[] },
    undefined,
    { limit: number; tenantId: number }
  >({
    baseURL: API_BASE.openlineage,
    url: API_PATH.knowledgeGraph.initial,
    params: { limit, tenantId: resolvedTenantId }
  })
  const nodes = (data.nodes ?? []).map(transformNode)
  const links = (data.relationships ?? []).map(transformRelationship)
  onProgress?.(nodes.length, nodes.length)
  return { nodes, links }
}

/**
 * Search graph nodes.
 */
export async function searchGraph(
  query: string,
  topK: number = 10,
  signal?: AbortSignal,
  tenantId?: number
): Promise<GraphData> {
  const resolvedTenantId = resolveTenantId(tenantId)
  const data = await requestRaw<OpenLineageSearchResponse, { tenantId: number; query: string; topK: number }>({
    baseURL: API_BASE.openlineage,
    url: API_PATH.knowledgeGraph.search,
    method: 'POST',
    data: { tenantId: resolvedTenantId, query, topK },
    signal
  })
  const nodes = (data.nodes ?? []).map(transformNode)
  return { nodes, links: [] }
}

/**
 * Trigger full tenant rebuild.
 */
export async function rebuildGraph(tenantId?: number): Promise<OpenLineageRebuildResponse> {
  const resolvedTenantId = resolveTenantId(tenantId)
  return requestRaw<OpenLineageRebuildResponse, { tenantId: number }>({
    baseURL: API_BASE.openlineage,
    url: API_PATH.knowledgeGraph.rebuild,
    method: 'POST',
    data: { tenantId: resolvedTenantId }
  })
}

/**
 * Set current DW embedding model.
 */
export async function setKnowledgeEmbeddingModel(
  aiModelId: number,
  tenantId?: number
): Promise<OpenLineageSetEmbeddingResponse> {
  const resolvedTenantId = resolveTenantId(tenantId)
  return requestRaw<OpenLineageSetEmbeddingResponse, { tenantId: number; aiModelId: number }>({
    baseURL: API_BASE.openlineage,
    url: API_PATH.knowledgeGraph.setEmbedding,
    method: 'POST',
    data: { tenantId: resolvedTenantId, aiModelId }
  })
}
