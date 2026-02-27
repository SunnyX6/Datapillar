/**
 * 知识图谱 API 服务
 *
 * 知识图谱使用非 SSE 接口（一次性 JSON 返回）
 */

import { API_BASE, API_PATH, requestRaw } from '@/api'
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

/**
 * 转换 Neo4j 节点为前端节点
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
    displayName: properties.displayName,
    val: UNIFIED_VAL,
    health: 'healthy',
    description: properties.description,
    owner: properties.owner as string | undefined,
    tags: properties.tags as string[] | undefined,
    lastUpdated: properties.updatedAt as string | undefined
  }
}

/**
 * 转换 Neo4j 关系为前端关系
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
 * 获取初始图数据（SSE 流式）
 */
export async function fetchInitialGraph(
  limit: number = 500,
  onProgress?: (current: number, total: number) => void
): Promise<GraphData> {
  const data = await requestRaw<{ nodes: Neo4jNode[]; relationships: Neo4jRelationship[] }, undefined, { limit: number }>({
    baseURL: API_BASE.aiKnowledge,
    url: API_PATH.knowledgeGraph.initial,
    params: { limit }
  })
  const nodes = (data.nodes ?? []).map(transformNode)
  const links = (data.relationships ?? []).map(transformRelationship)
  onProgress?.(nodes.length, nodes.length)
  return { nodes, links }
}

/**
 * 搜索知识图谱（非 SSE，一次性返回）
 */
export async function searchGraph(
  query: string,
  topK: number = 10,
  signal?: AbortSignal
): Promise<GraphData> {
  const data = await requestRaw<{ nodes: Neo4jNode[]; relationships: Neo4jRelationship[] }, { query: string; top_k: number }>({
    baseURL: API_BASE.aiKnowledge,
    url: API_PATH.knowledgeGraph.search,
    method: 'POST',
    data: { query, top_k: topK },
    signal
  })
  const nodes = (data.nodes ?? []).map(transformNode)
  const links = (data.relationships ?? []).map(transformRelationship)
  return { nodes, links }
}
