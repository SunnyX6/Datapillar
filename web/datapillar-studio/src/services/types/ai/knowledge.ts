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

export interface Neo4jNode {
  id: number
  type: string
  level: number
  properties: {
    name: string
    displayName?: string
    description?: string
    [key: string]: unknown
  }
}

export interface Neo4jRelationship {
  id: number
  start: number
  end: number
  type: string
  properties: Record<string, unknown>
}

export interface GraphNode {
  id: string
  type: string
  level: number
  group: number
  name: string
  val: number
  health: 'healthy' | 'warning' | 'error'
  displayName?: string
  description?: string
  owner?: string
  tags?: string[]
  lastUpdated?: string
  schema?: Array<{ name: string; type: string; key?: boolean }>
}

export interface GraphLink {
  source: string
  target: string
  type: string
  value: number
}

export interface GraphData {
  nodes: GraphNode[]
  links: GraphLink[]
}
