/**
 * 知识图谱 API 服务
 *
 * 知识图谱使用非 SSE 接口（一次性 JSON 返回）
 */

import { fetchWithAuthRetry } from '@/lib/api/client'

/**
 * 后端返回的节点结构（Neo4j 格式）
 */
interface Neo4jNode {
  id: number
  type: string  // 节点类型，如 "Table", "Column", "Metric" 等
  level: number // 层级，用于分层布局
  properties: {
    name: string
    displayName?: string
    description?: string
    [key: string]: unknown
  }
}

/**
 * 后端返回的关系结构（Neo4j 格式）
 */
interface Neo4jRelationship {
  id: number
  start: number
  end: number
  type: string
  properties: Record<string, unknown>
}

/**
 * 前端图节点结构
 */
export interface GraphNode {
  id: string
  type: string  // 节点类型，如 "Table", "Column", "Metric" 等
  level: number // 层级，用于分层布局（0=Domain, 1=Catalog, 2=Schema, 3=Table, 4=Column...）
  group: number
  name: string
  val: number
  health: 'healthy' | 'warning' | 'error'
  displayName?: string  // 中文名称（详情面板显示）
  description?: string
  owner?: string
  tags?: string[]
  lastUpdated?: string
  schema?: Array<{ name: string; type: string; key?: boolean }>  // 表结构信息
}

/**
 * 前端图关系结构
 */
export interface GraphLink {
  source: string
  target: string
  type: string  // 关系类型，如 HAS_COLUMN, BELONGS_TO 等
  value: number
}

/**
 * 图数据
 */
export interface GraphData {
  nodes: GraphNode[]
  links: GraphLink[]
}

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
  const response = await fetchWithAuthRetry(`/api/ai/knowledge/initial?limit=${limit}`, {
    method: 'GET',
    credentials: 'include'
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const data = (await response.json()) as { nodes: Neo4jNode[]; relationships: Neo4jRelationship[] }
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
  const response = await fetchWithAuthRetry('/api/ai/knowledge/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ query, top_k: topK }),
    signal
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const data = (await response.json()) as { nodes: Neo4jNode[]; relationships: Neo4jRelationship[] }
  const nodes = (data.nodes ?? []).map(transformNode)
  const links = (data.relationships ?? []).map(transformRelationship)
  return { nodes, links }
}
