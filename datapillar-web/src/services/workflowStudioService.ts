export type WorkflowNodeType = 'source' | 'transform' | 'quality' | 'sink'

export interface WorkflowNodeDefinition {
  id: string
  label: string
  type: WorkflowNodeType
  description: string
  column: number  // 逻辑列位置
  row: number     // 逻辑行位置
}

export interface WorkflowEdgeDefinition {
  id: string
  source: string
  target: string
}

export interface WorkflowStats {
  nodes: number
  edges: number
  runtimeMinutes: number
  qualityScore: number
}

export interface WorkflowGraph {
  name: string
  summary: string
  lastUpdated: number
  nodes: WorkflowNodeDefinition[]
  edges: WorkflowEdgeDefinition[]
  stats: WorkflowStats
}

export const emptyWorkflowGraph: WorkflowGraph = {
  name: 'Workflow Studio',
  summary: '描述你的数据目标，AI 会在数秒内生成编排蓝图。',
  lastUpdated: Date.now(),
  nodes: [],
  edges: [],
  stats: {
    nodes: 0,
    edges: 0,
    runtimeMinutes: 0,
    qualityScore: 0
  }
}

type KeywordTemplate = {
  keywords: string[]
  label: string
  description: string
}

const SOURCE_TEMPLATES: KeywordTemplate[] = [
  {
    keywords: ['mysql', 'mariadb', 'rds', 'aurora'],
    label: 'MySQL Source',
    description: 'Incremental ingest from transactional MySQL clusters'
  },
  {
    keywords: ['postgres', 'postgresql', 'redshift'],
    label: 'Postgres Source',
    description: 'Full + CDC replica from Postgres warehouse'
  },
  {
    keywords: ['kafka', 'pulsar', 'stream'],
    label: 'Kafka Topic',
    description: 'Streaming events via Kafka consumer group'
  },
  {
    keywords: ['s3', 'object storage', 'oss', 'lake'],
    label: 'Object Storage',
    description: 'Batch ingest from partitioned data lake buckets'
  },
  {
    keywords: ['salesforce', 'crm'],
    label: 'Salesforce Connector',
    description: 'Nightly extract via bulk API'
  }
]

const TRANSFORM_TEMPLATES: KeywordTemplate[] = [
  {
    keywords: ['clean', 'standard', 'normalize'],
    label: 'Schema Standardization',
    description: 'Unify column naming, types, surrogate keys'
  },
  {
    keywords: ['join', 'enrich', 'lookup', 'combine'],
    label: 'Dimension Join',
    description: 'Join facts with enrichment dimensions'
  },
  {
    keywords: ['aggregate', 'rollup', 'summary'],
    label: 'Aggregation Layer',
    description: 'Window + rollup metrics across business keys'
  },
  {
    keywords: ['ml', 'detect', 'score', 'predict'],
    label: 'Model Scoring',
    description: 'Invoke feature store + inference routines'
  }
]

const QUALITY_KEYWORDS = ['quality', 'validate', 'anomaly', 'sla', 'test']

const SINK_TEMPLATES: KeywordTemplate[] = [
  {
    keywords: ['bigquery', 'bq'],
    label: 'BigQuery Serving',
    description: 'Publish curated tables into BigQuery datasets'
  },
  {
    keywords: ['snowflake'],
    label: 'Snowflake Warehouse',
    description: 'Push DWH tables with zero-copy clones'
  },
  {
    keywords: ['lakehouse', 'delta', 'databricks'],
    label: 'Delta Lakehouse',
    description: 'Optimized Delta tables with Z-Order + VACUUM'
  },
  {
    keywords: ['clickhouse'],
    label: 'ClickHouse OLAP',
    description: 'Serve sub-second OLAP API workloads'
  }
]

const DEFAULT_SOURCE: KeywordTemplate = {
  keywords: [],
  label: 'Operational DB',
  description: 'Generic CDC ingestion from operational systems'
}

const DEFAULT_TRANSFORM: KeywordTemplate = {
  keywords: [],
  label: 'Transform Orchestrator',
  description: 'Standard transformation + orchestration layer'
}

const DEFAULT_SINK: KeywordTemplate = {
  keywords: [],
  label: 'Unified Lakehouse',
  description: 'Serve curated tables into the analytics lakehouse'
}

const slugify = (value: string) => value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '')

const pickTemplates = (normalizedPrompt: string, templates: KeywordTemplate[], fallback: KeywordTemplate, max = 2) => {
  const matches: KeywordTemplate[] = []
  templates.forEach((template) => {
    if (template.keywords.some((keyword) => normalizedPrompt.includes(keyword))) {
      matches.push(template)
    }
  })

  if (matches.length === 0) {
    matches.push(fallback)
  }

  return matches.slice(0, max)
}

const buildNodesFromTemplates = (templates: KeywordTemplate[], type: WorkflowNodeType, columnIndex: number) => {
  return templates.map((template, idx) => ({
    id: `${type}-${slugify(template.label)}`,
    label: template.label,
    type,
    description: template.description,
    column: columnIndex,
    row: idx
  }))
}

const generateQualityNode = (columnIndex: number): WorkflowNodeDefinition => ({
  id: 'quality-data-guardian',
  label: 'Quality Guardrails',
  type: 'quality',
  description: 'Great Expectations assertions + anomaly alarms',
  column: columnIndex,
  row: 0
})

const composeSummary = (sources: KeywordTemplate[], transforms: KeywordTemplate[], sink: KeywordTemplate, includesQuality: boolean) => {
  const sourceNames = sources.map((s) => s.label).join(' + ')
  const transformNames = transforms.map((s) => s.label).join(' → ')
  const qualityFragment = includesQuality ? 'Quality guardrails enforce SLA before publish. ' : ''
  return `${sourceNames} → ${transformNames} → ${sink.label}. ${qualityFragment}${sink.description}`
}

const computeStats = (nodes: WorkflowNodeDefinition[], edges: WorkflowEdgeDefinition[], quality: boolean): WorkflowStats => {
  const runtimeMinutes = Math.max(5, Math.round(nodes.length * 4.2 + (quality ? 6 : 0)))
  const qualityScore = Math.min(99, Math.round(78 + nodes.length * 1.2 + (quality ? 8 : 0)))
  return {
    nodes: nodes.length,
    edges: edges.length,
    runtimeMinutes,
    qualityScore
  }
}

const buildChainEdges = (sources: WorkflowNodeDefinition[], sequentialNodes: WorkflowNodeDefinition[]) => {
  const edges: WorkflowEdgeDefinition[] = []
  if (sequentialNodes.length === 0) {
    return edges
  }

  const first = sequentialNodes[0]
  sources.forEach((source) => {
    edges.push({
      id: `edge-${source.id}-${first.id}`,
      source: source.id,
      target: first.id
    })
  })

  for (let i = 1; i < sequentialNodes.length; i += 1) {
    const prev = sequentialNodes[i - 1]
    const current = sequentialNodes[i]
    edges.push({
      id: `edge-${prev.id}-${current.id}`,
      source: prev.id,
      target: current.id
    })
  }

  return edges
}

const inferPipelineName = (prompt: string) => {
  const trimmed = prompt.trim()
  if (!trimmed) return 'Untitled pipeline'
  const words = trimmed.split(/\s+/).slice(0, 5)
  const titled = words
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ')
  return `${titled} Pipeline`
}

export async function generateWorkflowFromPrompt(prompt: string): Promise<WorkflowGraph> {
  const normalized = prompt.toLowerCase()
  const sources = pickTemplates(normalized, SOURCE_TEMPLATES, DEFAULT_SOURCE)
  const transforms = pickTemplates(normalized, TRANSFORM_TEMPLATES, DEFAULT_TRANSFORM, 3)
  const sink = pickTemplates(normalized, SINK_TEMPLATES, DEFAULT_SINK, 1)[0]
  const useQuality = QUALITY_KEYWORDS.some((keyword) => normalized.includes(keyword))

  const sourceNodes = buildNodesFromTemplates(sources, 'source', 0)
  const transformNodes = buildNodesFromTemplates(transforms, 'transform', 1)
  const sinkNodes = buildNodesFromTemplates([sink], 'sink', useQuality ? 3 : 2)
  const qualityNode = useQuality ? generateQualityNode(2) : undefined

  const orderedChain = [...transformNodes]
  if (qualityNode) {
    orderedChain.push(qualityNode)
  }
  orderedChain.push(...sinkNodes)

  const edges = buildChainEdges(sourceNodes, orderedChain)
  const nodes = [...sourceNodes, ...orderedChain]
  const summary = composeSummary(sources, transforms, sink, Boolean(qualityNode))
  const stats = computeStats(nodes, edges, Boolean(qualityNode))

  if (typeof window !== 'undefined') {
    await new Promise((resolve) => setTimeout(resolve, 600))
  }

  return {
    name: inferPipelineName(prompt),
    summary,
    lastUpdated: Date.now(),
    nodes,
    edges,
    stats
  }
}
