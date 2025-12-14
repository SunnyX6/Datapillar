/**
 * 知识图谱 API 服务
 *
 * 使用 SSE + MsgPack 进行流式数据传输
 */

import { decode } from '@msgpack/msgpack'

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
 * SSE 事件类型
 */
type KGEventType = 'stream_start' | 'nodes_batch' | 'rels_batch' | 'search_result' | 'stream_end' | 'error'

/**
 * SSE 事件数据
 */
interface KGStreamEvent {
  event_type: KGEventType
  data: string // Base64 编码的 MsgPack 数据
  total?: number
  current?: number
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
 * Base64 解码为 Uint8Array
 */
function base64ToUint8Array(base64: string): Uint8Array {
  const binaryString = atob(base64)
  const bytes = new Uint8Array(binaryString.length)
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i)
  }
  return bytes
}

/**
 * 解析 MsgPack 数据
 */
function decodeMsgPack<T>(base64Data: string): T {
  const bytes = base64ToUint8Array(base64Data)
  return decode(bytes) as T
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
  const nodes: GraphNode[] = []
  const links: GraphLink[] = []

  return new Promise((resolve, reject) => {
    const eventSource = new EventSource(`/api/ai/knowledge/initial?limit=${limit}`, {
      withCredentials: true
    })

    eventSource.addEventListener('stream_start', () => {
      console.log('[KG] 开始接收图数据')
    })

    eventSource.addEventListener('nodes_batch', (event) => {
      try {
        const eventData: KGStreamEvent = JSON.parse(event.data)
        const batchNodes = decodeMsgPack<Neo4jNode[]>(eventData.data)

        nodes.push(...batchNodes.map(transformNode))

        if (eventData.total && eventData.current && onProgress) {
          onProgress(eventData.current, eventData.total)
        }
      } catch (error) {
        console.error('[KG] 解析节点数据失败:', error)
      }
    })

    eventSource.addEventListener('rels_batch', (event) => {
      try {
        const eventData: KGStreamEvent = JSON.parse(event.data)
        const batchRels = decodeMsgPack<Neo4jRelationship[]>(eventData.data)

        links.push(...batchRels.map(transformRelationship))

        if (eventData.total && eventData.current && onProgress) {
          onProgress(eventData.current, eventData.total)
        }
      } catch (error) {
        console.error('[KG] 解析关系数据失败:', error)
      }
    })

    eventSource.addEventListener('stream_end', () => {
      console.log(`[KG] 数据接收完成: ${nodes.length} 节点, ${links.length} 关系`)
      eventSource.close()
      resolve({ nodes, links })
    })

    eventSource.addEventListener('error', () => {
      console.error('[KG] SSE 连接错误')
      eventSource.close()
      reject(new Error('Failed to fetch graph data'))
    })

    eventSource.onerror = () => {
      eventSource.close()
      reject(new Error('SSE connection failed'))
    }
  })
}

/**
 * 搜索知识图谱（SSE 流式）
 */
export async function searchGraph(
  query: string,
  topK: number = 10
): Promise<GraphData> {
  const nodes: GraphNode[] = []
  const links: GraphLink[] = []

  return new Promise((resolve, reject) => {
    fetch('/api/ai/knowledge/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ query, top_k: topK })
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }

        const reader = response.body?.getReader()
        if (!reader) {
          throw new Error('ReadableStream not supported')
        }

        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })

          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (!line.trim() || !line.startsWith('data:')) continue

            const jsonData = line.replace(/^data:\s*/, '')
            try {
              const eventData: KGStreamEvent = JSON.parse(jsonData)

              if (eventData.event_type === 'search_result') {
                const result = decodeMsgPack<{
                  nodes: Neo4jNode[]
                  relationships: Neo4jRelationship[]
                  highlight_node_ids: number[]
                }>(eventData.data)

                nodes.push(...result.nodes.map(transformNode))
                links.push(...result.relationships.map(transformRelationship))
              } else if (eventData.event_type === 'stream_end') {
                resolve({ nodes, links })
              } else if (eventData.event_type === 'error') {
                reject(new Error('Search failed'))
              }
            } catch (error) {
              console.error('[KG] 解析搜索结果失败:', error)
            }
          }
        }
      })
      .catch(reject)
  })
}
