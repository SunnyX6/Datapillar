import { type JSX, type KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react'
import NVL, {
  d3ForceLayoutType,
  type Node as NvlNode,
  type Relationship as NvlRelationship,
  type NvlOptions
} from '@neo4j-nvl/base'
import {
  PanInteraction,
  ZoomInteraction,
  DragNodeInteraction,
  ClickInteraction,
  HoverInteraction
} from '@neo4j-nvl/interaction-handlers'
import {
  Activity,
  ArrowUp,
  BookA,
  BrainCircuit,
  Clock,
  Columns3,
  Database,
  Layers,
  ListChecks,
  Loader2,
  MoreHorizontal,
  Network,
  RefreshCw,
  Scale,
  Shield,
  Sparkles,
  Table as TableIcon,
  Tag,
  Target,
  User,
  Waypoints,
  X,
  Zap
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { panelWidthClassMap, panelHeightClassMap } from '@/design-tokens/dimensions'
import { useIsDark } from '@/state'
import { useKnowledgeGraphStore } from '@/features/governance/state'
import {
  rebuildGraph,
  setKnowledgeEmbeddingModel,
  type GraphData,
  type GraphNode,
  type GraphLink
} from '@/services/knowledgeGraphService'
import { listCurrentUserModels } from '@/services/studioLlmService'
import { Tooltip } from '@/components/ui'
import { ChatInput, type ChatCommandOption, type ChatModelOption } from '@/components/ui/ChatInput'
import { Select, type SelectOption } from '@/components/ui/Select'

type NvlNodeWithMeta = NvlNode & { raw: GraphNode }
type NvlRelWithMeta = NvlRelationship & { raw: GraphLink }

type NodeTypePalette = {
  fill: string
  badgeBg: string
  badgeText: string
  icon: JSX.Element
}

const TYPE_PALETTE_MAP: Array<{ keys: string[]; palette: NodeTypePalette }> = [
  {
    keys: ['knowledge'],
    palette: {
      fill: '#7c3aed',
      badgeBg: 'bg-purple-500/20',
      badgeText: 'text-purple-400',
      icon: <Network size={16} />
    }
  },
  {
    keys: ['catalog'],
    palette: {
      fill: '#f97316',
      badgeBg: 'bg-orange-500/20',
      badgeText: 'text-orange-400',
      icon: <Layers size={16} />
    }
  },
  {
    keys: ['sql'],
    palette: {
      fill: '#ec4899',
      badgeBg: 'bg-pink-500/20',
      badgeText: 'text-pink-400',
      icon: <Waypoints size={16} />
    }
  },
  {
    keys: ['schema'],
    palette: {
      fill: '#eab308',
      badgeBg: 'bg-yellow-500/20',
      badgeText: 'text-yellow-400',
      icon: <Database size={16} />
    }
  },
  {
    keys: ['table'],
    palette: {
      fill: '#2563eb',
      badgeBg: 'bg-blue-500/20',
      badgeText: 'text-blue-400',
      icon: <TableIcon size={16} />
    }
  },
  {
    keys: ['column'],
    palette: {
      fill: '#f97316',
      badgeBg: 'bg-orange-500/20',
      badgeText: 'text-orange-400',
      icon: <Columns3 size={16} />
    }
  },
  {
    keys: ['atomicmetric'],
    palette: {
      fill: '#9333ea',
      badgeBg: 'bg-purple-500/20',
      badgeText: 'text-purple-400',
      icon: <Target size={16} />
    }
  },
  {
    keys: ['derivedmetric'],
    palette: {
      fill: '#2563eb',
      badgeBg: 'bg-blue-500/20',
      badgeText: 'text-blue-400',
      icon: <Zap size={16} />
    }
  },
  {
    keys: ['compositemetric'],
    palette: {
      fill: '#10b981',
      badgeBg: 'bg-emerald-500/20',
      badgeText: 'text-emerald-400',
      icon: <Layers size={16} />
    }
  },
  {
    keys: ['tag'],
    palette: {
      fill: '#f43f5e',
      badgeBg: 'bg-rose-500/20',
      badgeText: 'text-rose-400',
      icon: <Tag size={16} />
    }
  },
  {
    keys: ['wordroot'],
    palette: {
      fill: '#2563eb',
      badgeBg: 'bg-blue-500/20',
      badgeText: 'text-blue-400',
      icon: <BookA size={16} />
    }
  },
  {
    keys: ['modifier'],
    palette: {
      fill: '#3b82f6',
      badgeBg: 'bg-blue-500/20',
      badgeText: 'text-blue-400',
      icon: <Sparkles size={16} />
    }
  },
  {
    keys: ['unit'],
    palette: {
      fill: '#f59e0b',
      badgeBg: 'bg-amber-500/20',
      badgeText: 'text-amber-400',
      icon: <Scale size={16} />
    }
  },
  {
    keys: ['valuedomain'],
    palette: {
      fill: '#10b981',
      badgeBg: 'bg-emerald-500/20',
      badgeText: 'text-emerald-400',
      icon: <ListChecks size={16} />
    }
  }
]

const FILTER_TYPE_GROUPS: Record<string, string[]> = {
  knowledge: ['knowledge'],
  catalog: ['catalog'],
  schema: ['schema'],
  table: ['table'],
  column: ['column'],
  sql: ['sql'],
  tag: ['tag'],
  atomicmetric: ['atomicmetric'],
  derivedmetric: ['derivedmetric'],
  compositemetric: ['compositemetric'],
  wordroot: ['wordroot'],
  modifier: ['modifier'],
  unit: ['unit'],
  valuedomain: ['valuedomain']
}

const FILTER_TYPE_OPTIONS = [
  { key: 'knowledge', palette: getNodeTypePalette('knowledge') },
  { key: 'catalog', palette: getNodeTypePalette('catalog') },
  { key: 'schema', palette: getNodeTypePalette('schema') },
  { key: 'table', palette: getNodeTypePalette('table') },
  { key: 'column', palette: getNodeTypePalette('column') },
  { key: 'sql', palette: getNodeTypePalette('sql') },
  { key: 'tag', palette: getNodeTypePalette('tag') },
  { key: 'atomicmetric', palette: getNodeTypePalette('atomicmetric') },
  { key: 'derivedmetric', palette: getNodeTypePalette('derivedmetric') },
  { key: 'compositemetric', palette: getNodeTypePalette('compositemetric') },
  { key: 'wordroot', palette: getNodeTypePalette('wordroot') },
  { key: 'modifier', palette: getNodeTypePalette('modifier') },
  { key: 'unit', palette: getNodeTypePalette('unit') },
  { key: 'valuedomain', palette: getNodeTypePalette('valuedomain') }
] as const

const TYPE_TOOLTIP_LABELS: Record<string, string> = {
  knowledge: 'Knowledge',
  catalog: 'Catalog',
  schema: 'Schema',
  table: 'Table',
  column: 'Column',
  sql: 'SQL',
  tag: 'Tag',
  atomicmetric: 'Atomic Metric',
  derivedmetric: 'Derived Metric',
  compositemetric: 'Composite Metric',
  wordroot: 'Word Root',
  modifier: 'Modifier',
  unit: 'Unit',
  valuedomain: 'Value Domain'
}

const DEFAULT_TYPE_PALETTE: NodeTypePalette = {
  fill: '#4f46e5',
  badgeBg: 'bg-indigo-500/20',
  badgeText: 'text-indigo-400',
  icon: <User size={16} />
}

function getNodeTypePalette(type?: string): NodeTypePalette {
  const normalized = (type || '').toLowerCase()
  const matched = TYPE_PALETTE_MAP.find(entry => entry.keys.includes(normalized))
  return matched?.palette ?? DEFAULT_TYPE_PALETTE
}

function hexToRgba(hex: string, alpha: number): string {
  const normalizedHex = hex.replace('#', '')
  if (normalizedHex.length !== 6) return hex

  const num = parseInt(normalizedHex, 16)
  const r = (num >> 16) & 0xff
  const g = (num >> 8) & 0xff
  const b = num & 0xff

  const clampedAlpha = Math.min(Math.max(alpha, 0), 1)
  return `rgba(${r}, ${g}, ${b}, ${clampedAlpha})`
}

function getNodeColor(node: GraphNode): string {
  const type = (node.type || '').toLowerCase()
  const typeColor = getNodeTypePalette(type).fill

  if (type === 'table') {
    return typeColor
  }
  return typeColor
}

function resolveModelOptionAiModelId(model: ChatModelOption): number | null {
  if (typeof model.aiModelId === 'number' && Number.isInteger(model.aiModelId) && model.aiModelId > 0) {
    return model.aiModelId
  }
  if (typeof model.id === 'number' && Number.isInteger(model.id) && model.id > 0) {
    return model.id
  }
  return null
}

function hasModelOption(options: ChatModelOption[], aiModelId: number | null): boolean {
  if (aiModelId === null) {
    return false
  }
  return options.some((model) => resolveModelOptionAiModelId(model) === aiModelId)
}

function resolveBackendDefaultAiModelId(options: ChatModelOption[]): number | null {
  const defaultModel = options.find((model) => model.isDefault === true)
  if (!defaultModel) {
    return null
  }
  return resolveModelOptionAiModelId(defaultModel)
}

function resolveModelOptionLabel(model: ChatModelOption, fallbackLabel: string): string {
  const normalizedName = model.modelName?.trim() || model.label?.trim()
  if (normalizedName) {
    return normalizedName
  }
  const providerModelId = model.providerModelId?.trim()
  if (providerModelId) {
    return providerModelId
  }
  const aiModelId = resolveModelOptionAiModelId(model)
  if (aiModelId) {
    return `Model-${aiModelId}`
  }
  return fallbackLabel
}

function resolveErrorMessage(error: unknown, fallbackMessage: string): string {
  if (error instanceof Error && error.message) {
    return error.message
  }
  if (typeof error === 'string' && error.trim().length > 0) {
    return error
  }
  if (typeof error === 'object' && error !== null) {
    const maybeError = error as {
      response?: {
        data?: {
          message?: unknown
          error?: unknown
        }
      }
    }
    const responseMessage = maybeError.response?.data?.message
    if (typeof responseMessage === 'string' && responseMessage.trim().length > 0) {
      return responseMessage
    }
    const responseError = maybeError.response?.data?.error
    if (typeof responseError === 'string' && responseError.trim().length > 0) {
      return responseError
    }
  }
  return fallbackMessage
}

function filterGraphByType(type: string | null, graph: GraphData): GraphData {
  if (!type) return graph
  const normalized = type.toLowerCase()
  const typeMap = new Map(graph.nodes.map(node => [node.id, (node.type || '').toLowerCase()]))
  const targetTypes = FILTER_TYPE_GROUPS[normalized] ?? [normalized]

  const isTableView = normalized === 'table'
  const isColumnView = normalized === 'column'

  if (isColumnView) {
    const nodeIds = new Set<string>()
    graph.links.forEach(link => {
      const sourceId = String(link.source)
      const targetId = String(link.target)
      const sourceType = typeMap.get(sourceId)
      const targetType = typeMap.get(targetId)

      const isTableColumnPair =
        (sourceType === 'table' && targetType === 'column') ||
        (sourceType === 'column' && targetType === 'table')

      if (isTableColumnPair) {
        nodeIds.add(sourceId)
        nodeIds.add(targetId)
      }
    })

    if (!nodeIds.size) return { nodes: [], links: [] }

    const nodes = graph.nodes.filter(node => nodeIds.has(node.id))
    const links = graph.links.filter(link => {
      const sourceId = String(link.source)
      const targetId = String(link.target)
      const sourceType = typeMap.get(sourceId)
      const targetType = typeMap.get(targetId)
      const isTableColumnPair =
        (sourceType === 'table' && targetType === 'column') ||
        (sourceType === 'column' && targetType === 'table')
      return isTableColumnPair && nodeIds.has(sourceId) && nodeIds.has(targetId)
    })
    return { nodes, links }
  }

  const seedNodes = graph.nodes.filter(node => targetTypes.includes((node.type || '').toLowerCase()))

  if (!seedNodes.length) return { nodes: [], links: [] }

  const nodeIds = new Set(seedNodes.map(node => node.id))

  if (isTableView) {
    graph.links.forEach(link => {
      const sourceId = String(link.source)
      const targetId = String(link.target)
      const sourceType = typeMap.get(sourceId)
      const targetType = typeMap.get(targetId)

      const isTableColumnPair =
        (sourceType === 'table' && targetType === 'column') ||
        (sourceType === 'column' && targetType === 'table')
      if (!isTableColumnPair) {
        return
      }
      if (nodeIds.has(sourceId) || nodeIds.has(targetId)) {
        nodeIds.add(sourceId)
        nodeIds.add(targetId)
      }
    })
  }

  const nodes = graph.nodes.filter(node => nodeIds.has(node.id))

  const links = graph.links.filter(link => {
    const sourceIn = nodeIds.has(String(link.source))
    const targetIn = nodeIds.has(String(link.target))
    return sourceIn && targetIn
  })

  return { nodes, links }
}

type GovernanceIssue = {
  id: number
  type: string
  title: string
  desc: string
}

const GOVERNANCE_ISSUES: GovernanceIssue[] = [
  {
    id: 1,
    type: 'Metadata',
    title: 'Missing Descriptions',
    desc: '2 tables in Silver Layer lack business descriptions.'
  },
  {
    id: 2,
    type: 'Quality',
    title: 'Schema Drift',
    desc: 'Source schema changed for ingest_logs.'
  }
]

const ENABLE_AI_SENTINEL = false

export function KnowledgeGraphView() {
  const { t } = useTranslation('knowledgeGraph')
  const nvlRef = useRef<NVL | null>(null)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const sentinelListRef = useRef<HTMLDivElement | null>(null)
  const isCardPinnedRef = useRef(false)
  const selectedNodeIdRef = useRef<string | null>(null)
  const filterTypeRef = useRef<string | null>(null)
  const searchAbortRef = useRef<AbortController | null>(null)
  const commandNoticeHideTimerRef = useRef<number | null>(null)
  const commandNoticeClearTimerRef = useRef<number | null>(null)
  const isComposingRef = useRef(false)

  // Use global store Manage graph data
  const {
    allGraphData,
    isLoading: storeLoading,
    loadInitialGraph,
    searchAndMerge,
    refresh
  } = useKnowledgeGraphStore()

  const [graphData, setGraphData] = useState<GraphData>({ nodes: [], links: [] })
  const [filterType, setFilterType] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [hoverNodeId, setHoverNodeId] = useState<string | null>(null)
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [isCardPinned, setIsCardPinned] = useState(false)
  const [pendingFocusId, setPendingFocusId] = useState<string | null>(null)
  const [issues, setIssues] = useState(GOVERNANCE_ISSUES)
  const [fixingIssueId, setFixingIssueId] = useState<number | null>(null)
  const [inputValue, setInputValue] = useState('')
  const [isExecuting, setIsExecuting] = useState(false)
  const [embeddingModelOptions, setEmbeddingModelOptions] = useState<ChatModelOption[]>([])
  const [defaultEmbeddingAiModelId, setDefaultEmbeddingAiModelId] = useState<number | null>(null)
  const [selectedEmbeddingAiModelId, setSelectedEmbeddingAiModelId] = useState<number | null>(null)
  const [chatModelOptions, setChatModelOptions] = useState<ChatModelOption[]>([])
  const [defaultChatAiModelId, setDefaultChatAiModelId] = useState<number | null>(null)
  const [selectedChatAiModelId, setSelectedChatAiModelId] = useState<number | null>(null)
  const [isApplyingModel, setIsApplyingModel] = useState(false)
  const [isRebuilding, setIsRebuilding] = useState(false)
  const [commandNotice, setCommandNotice] = useState<string | null>(null)
  const [commandNoticeVisible, setCommandNoticeVisible] = useState(false)
  const [isSentinelCollapsed, setIsSentinelCollapsed] = useState(false)
  const knowledgeGraphCommandOptions = useMemo<ChatCommandOption[]>(() => [
    {
      id: 'rebuild',
      label: '/rebuild',
      title: t('commands.rebuild.title'),
      description: t('commands.rebuild.description')
    }
  ], [t])
  const isDark = useIsDark()
  const graphBackground = isDark ? '#020617' : '#f8fafc'
  const activeNodeId = selectedNodeId
  const activeNode = useMemo(
    () => graphData.nodes.find((node) => node.id === activeNodeId) ?? null,
    [activeNodeId, graphData.nodes]
  )
  const activeIcon = useMemo(() => (activeNode ? getNodeTypePalette(activeNode.type).icon : <User size={16} />), [activeNode])
  const activeNodeColor = useMemo(() => (activeNode ? getNodeColor(activeNode) : null), [activeNode])
  const embeddingModelSelectOptions = useMemo<SelectOption[]>(() => {
    return embeddingModelOptions.reduce<SelectOption[]>((result, model) => {
      const aiModelId = resolveModelOptionAiModelId(model)
      if (aiModelId === null) {
        return result
      }
      result.push({
        value: String(aiModelId),
        label: resolveModelOptionLabel(model, t('model.unknown'))
      })
      return result
    }, [])
  }, [embeddingModelOptions, t])
  const selectedEmbeddingModelValue = useMemo(() => {
    if (hasModelOption(embeddingModelOptions, selectedEmbeddingAiModelId) && selectedEmbeddingAiModelId !== null) {
      return String(selectedEmbeddingAiModelId)
    }
    if (hasModelOption(embeddingModelOptions, defaultEmbeddingAiModelId) && defaultEmbeddingAiModelId !== null) {
      return String(defaultEmbeddingAiModelId)
    }
    const fallbackAiModelId = resolveModelOptionAiModelId(embeddingModelOptions[0] ?? {})
    return fallbackAiModelId !== null ? String(fallbackAiModelId) : ''
  }, [defaultEmbeddingAiModelId, embeddingModelOptions, selectedEmbeddingAiModelId])
  const resolveTypeLabel = (type: string) => {
    const normalizedType = type.toLowerCase()
    return t(`typeLabels.${normalizedType}`, {
      defaultValue: normalizedType.charAt(0).toUpperCase() + normalizedType.slice(1)
    })
  }
  const resolveTypeTooltipLabel = (type: string) => {
    const normalizedType = type.toLowerCase()
    return TYPE_TOOLTIP_LABELS[normalizedType] ?? normalizedType.charAt(0).toUpperCase() + normalizedType.slice(1)
  }

  const nvlNodes = useMemo<NvlNodeWithMeta[]>(() => {
    return graphData.nodes.map(node => {
      let color = getNodeColor(node)
      if (node.health === 'warning') color = '#DC2626'

      // Priority display service returned name；If its missing, tell me everything label/id
      const captionText =
        (node.name ?? '').toString().trim() ||
        (node as GraphNode & { label?: string }).label ||
        node.displayName ||
        node.id

      return {
        id: node.id,
        captions: [{ value: captionText }],
        color,
        size: node.val,
        raw: node
      }
    })
  }, [graphData.nodes])

  const nvlRelationships = useMemo<NvlRelWithMeta[]>(() => {
    return graphData.links.map((link, index) => {
      const relId =
        (link as GraphLink & { id?: string }).id ??
        `${link.source}->${link.target}:${link.type ?? 'rel'}:${index}`

      return {
        id: relId,
        from: String(link.source),
        to: String(link.target),
        type: link.type,
        caption: link.type,
        color: isDark ? '#94a3b8' : '#cbd5e1',
        raw: link
      }
    })
  }, [graphData.links, isDark])

  const nvlOptions = useMemo<NvlOptions>(
    () => ({
      renderer: 'canvas',
      disableWebGL: true,
      disableWebWorkers: true,
      initialZoom: 0.9,
      minZoom: 0.02,
      maxZoom: 16,
      allowDynamicMinZoom: true,
      layout: d3ForceLayoutType,
      styling: {
        defaultNodeColor: isDark ? '#1f2937' : '#e2e8f0',
        defaultRelationshipColor: isDark ? '#475569' : '#cbd5e1',
        nodeDefaultBorderColor: isDark ? '#0ea5e9' : '#6366f1',
        selectedBorderColor: '#f97316',
        selectedInnerBorderColor: '#fbbf24',
        dropShadowColor: isDark ? '#0ea5e9' : '#1e293b',
        minimapViewportBoxColor: isDark ? '#cbd5e1' : '#475569'
      },
      disableTelemetry: true
    }),
    [isDark]
  )

  useEffect(() => {
    let active = true
    const loadModels = async () => {
      try {
        const rows = await listCurrentUserModels()
        if (!active) {
          return
        }
        const embeddingOptions = rows
          .filter((row) => row.modelType?.toLowerCase() === 'embeddings')
          .filter((row) => resolveModelOptionAiModelId(row) !== null)
        const chatOptions = rows
          .filter((row) => row.modelType?.toLowerCase() === 'chat')
          .filter((row) => resolveModelOptionAiModelId(row) !== null)

        setEmbeddingModelOptions(embeddingOptions)
        if (embeddingOptions.length === 0) {
          setDefaultEmbeddingAiModelId(null)
          setSelectedEmbeddingAiModelId(null)
        } else {
          const fallbackEmbeddingAiModelId = resolveModelOptionAiModelId(embeddingOptions[0])
          const backendDefaultEmbeddingAiModelId = resolveBackendDefaultAiModelId(embeddingOptions) ?? fallbackEmbeddingAiModelId
          setDefaultEmbeddingAiModelId(backendDefaultEmbeddingAiModelId)
          setSelectedEmbeddingAiModelId((current) => {
            if (hasModelOption(embeddingOptions, current)) {
              return current
            }
            return backendDefaultEmbeddingAiModelId
          })
        }

        setChatModelOptions(chatOptions)
        if (chatOptions.length === 0) {
          setDefaultChatAiModelId(null)
          setSelectedChatAiModelId(null)
        } else {
          const fallbackChatAiModelId = resolveModelOptionAiModelId(chatOptions[0])
          const backendDefaultChatAiModelId = resolveBackendDefaultAiModelId(chatOptions) ?? fallbackChatAiModelId
          setDefaultChatAiModelId(backendDefaultChatAiModelId)
          setSelectedChatAiModelId((current) => {
            if (hasModelOption(chatOptions, current)) {
              return current
            }
            return backendDefaultChatAiModelId
          })
        }
      } catch {
        if (active) {
          setEmbeddingModelOptions([])
          setDefaultEmbeddingAiModelId(null)
          setSelectedEmbeddingAiModelId(null)
          setChatModelOptions([])
          setDefaultChatAiModelId(null)
          setSelectedChatAiModelId(null)
        }
      }
    }

    void loadModels()
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    isCardPinnedRef.current = isCardPinned
  }, [isCardPinned])

  useEffect(() => {
    filterTypeRef.current = filterType
  }, [filterType])

  useEffect(() => {
    selectedNodeIdRef.current = selectedNodeId
  }, [selectedNodeId])

  useEffect(() => {
    if (activeNodeId && !graphData.nodes.some(node => node.id === activeNodeId)) {
      setSelectedNodeId(null)
      setHoverNodeId(null)
      setIsCardPinned(false)
    }
  }, [activeNodeId, graphData.nodes])

  // when store of allGraphData or filterType When changing，Update displayed graphData
  useEffect(() => {
    setGraphData(filterGraphByType(filterType, allGraphData))
  }, [filterType, allGraphData])

  // Load initial data（store The cache is checked internally）
  useEffect(() => {
    const load = async () => {
      setLoading(true)
      await loadInitialGraph(100)
      setLoading(false)
    }
    load()
  }, [loadInitialGraph])

  // store Synchronize when data changes loading Status
  useEffect(() => {
    if (!storeLoading && allGraphData.nodes.length > 0) {
      setLoading(false)
    }
  }, [storeLoading, allGraphData.nodes.length])

  useEffect(() => {
    return () => {
      if (commandNoticeHideTimerRef.current) {
        window.clearTimeout(commandNoticeHideTimerRef.current)
        commandNoticeHideTimerRef.current = null
      }
      if (commandNoticeClearTimerRef.current) {
        window.clearTimeout(commandNoticeClearTimerRef.current)
        commandNoticeClearTimerRef.current = null
      }
      if (searchAbortRef.current) {
        searchAbortRef.current.abort()
        searchAbortRef.current = null
      }
    }
  }, [])

  // NVL Instance initialization and interaction handler management
  useEffect(() => {
    if (loading || !containerRef.current) return

    // initialization NVL Example
    const nvl = new NVL(containerRef.current, nvlNodes, nvlRelationships, nvlOptions)
    nvlRef.current = nvl

    // Initialize interaction handler
    const panInteraction = new PanInteraction(nvl)
    const zoomInteraction = new ZoomInteraction(nvl)
    const dragInteraction = new DragNodeInteraction(nvl)
    const clickInteraction = new ClickInteraction(nvl)
    const hoverInteraction = new HoverInteraction(nvl)

    // Listen for the transition end event，The sidebar is re-adapted after the folding animation is completed.
    const container = containerRef.current
    const handleTransitionEnd = (e: TransitionEvent) => {
      // Checks whether it is the parent element containing this container width end of transition
      if (e.propertyName === 'width' && (e.target as HTMLElement)?.contains(container)) {
        nvlRef.current?.fit([], { animated: false })
      }
    }
    document.addEventListener('transitionend', handleTransitionEnd)

    // status tracking（Avoid full recalculation）
    let lastSelectedId: string | null = null

    // Click callback：Click on the node to select and pin the card
    clickInteraction.updateCallback('onNodeClick', (node: NvlNode) => {
      if (lastSelectedId && lastSelectedId !== node.id) {
        nvl.updateElementsInGraph([{ id: lastSelectedId, selected: false }], [])
      }
      nvl.updateElementsInGraph([{ id: node.id, selected: true, hovered: true }], [])
      nvl.pinNode(node.id)
      lastSelectedId = node.id
      setSelectedNodeId(node.id)
      setHoverNodeId(node.id)
      setIsCardPinned(true)
    })

    // Click callback：Double-click to unpin
    clickInteraction.updateCallback('onNodeDoubleClick', (node: NvlNode) => {
      nvl.unPinNode([node.id])
      setIsCardPinned(false)
    })

    // Click callback：relationship click（Uncheck）
    clickInteraction.updateCallback('onRelationshipClick', () => {
      if (lastSelectedId) {
        nvl.updateElementsInGraph([{ id: lastSelectedId, selected: false }], [])
        nvl.unPinNode([lastSelectedId])
        lastSelectedId = null
      }
      setSelectedNodeId(null)
      setIsCardPinned(false)
    })

    // Click callback：Canvas click（Uncheck and hover）
    clickInteraction.updateCallback('onCanvasClick', () => {
      if (lastSelectedId) {
        nvl.updateElementsInGraph([{ id: lastSelectedId, selected: false }], [])
        nvl.unPinNode([lastSelectedId])
        lastSelectedId = null
      }
      setSelectedNodeId(null)
      setHoverNodeId(null)
      setIsCardPinned(false)
    })

    // Drag callback：Fixed node after dragging
    dragInteraction.updateCallback('onDragEnd', (nodes) => {
      if (!nodes?.length) return
      nvl.updateElementsInGraph(
        nodes.map((n: NvlNode) => ({ id: n.id, pinned: true })),
        []
      )
    })

    // Hover callback：Only update a single node hovered Status，Does not trigger full recalculation
    let lastHoveredId: string | null = null
    hoverInteraction.updateCallback('onHover', (element, _hitElements, _event) => {
      const node = element && !('from' in element) ? element : null
      if (lastHoveredId && lastHoveredId !== node?.id) {
        nvl.updateElementsInGraph([{ id: lastHoveredId, hovered: false }], [])
      }

      if (node) {
        nvl.updateElementsInGraph([{ id: node.id, hovered: true }], [])
        lastHoveredId = node.id
        setHoverNodeId(node.id)
      } else {
        lastHoveredId = null
        setHoverNodeId(null)
      }
    })

    // Cleanup function
    return () => {
      document.removeEventListener('transitionend', handleTransitionEnd)
      panInteraction.destroy()
      zoomInteraction.destroy()
      dragInteraction.destroy()
      clickInteraction.destroy()
      hoverInteraction.destroy()
      nvl.destroy()
      nvlRef.current = null
    }
    // nvlNodes/nvlRelationships Used here only for initialization，Data is updated independently from below useEffect Process
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, nvlOptions])

  // Sync to when data is updated NVL
  useEffect(() => {
    const nvl = nvlRef.current
    if (!nvl || loading) return

    const existingNodes = (typeof nvl.getNodes === 'function' ? nvl.getNodes() : []) as Array<{ id: string }>
    const existingRels = (typeof nvl.getRelationships === 'function' ? nvl.getRelationships() : []) as Array<{ id: string }>

    const nextNodeIds = new Set(nvlNodes.map(node => node.id))
    const nextRelIds = new Set(nvlRelationships.map(rel => rel.id))

    const removeRelIds = existingRels
      .map(rel => rel.id)
      .filter(id => !nextRelIds.has(id))
    if (removeRelIds.length && typeof nvl.removeRelationshipsWithIds === 'function') {
      nvl.removeRelationshipsWithIds(removeRelIds)
    }

    const removeNodeIds = existingNodes
      .map(node => node.id)
      .filter(id => !nextNodeIds.has(id))
    if (removeNodeIds.length && typeof nvl.removeNodesWithIds === 'function') {
      nvl.removeNodesWithIds(removeNodeIds)
    }

    nvl.addAndUpdateElementsInGraph(nvlNodes, nvlRelationships)
  }, [nvlNodes, nvlRelationships, loading])

  useEffect(() => {
    document.body.style.cursor = hoverNodeId ? 'grab' : 'default'
    return () => {
      document.body.style.cursor = 'default'
    }
  }, [hoverNodeId])

  useEffect(() => {
    if (!pendingFocusId) return
    if (!graphData.nodes.some(node => node.id === pendingFocusId)) return
    const instance = nvlRef.current
    if (instance && typeof instance.fit === 'function') {
      instance.fit([pendingFocusId], { animated: true })
      setHoverNodeId(pendingFocusId)
      setSelectedNodeId(pendingFocusId)
      setIsCardPinned(true)
      setPendingFocusId(null)
    }
  }, [graphData.nodes, pendingFocusId])

  useEffect(() => {
    if (!import.meta.env.DEV) return
    const instance = nvlRef.current
    if (!instance) return
    // Facilitates local investigation，exposed NVL with node data
    ;(window as unknown as { __kg?: unknown }).__kg = {
      nvl: instance,
      nodes: graphData.nodes,
      options: instance.getCurrentOptions?.()
    }
    if (graphData.nodes.length) {
      console.info('[KG][debug] renderer options', instance.getCurrentOptions?.())
      console.info('[KG][debug] sample nodes', graphData.nodes.slice(0, 3))
    }
  }, [graphData.nodes])

  const handleFixIssue = async (issueId: number) => {
    setFixingIssueId(issueId)
    await new Promise((resolve) => setTimeout(resolve, 2000))
    setIssues((prev) => prev.filter((item) => item.id !== issueId))
    setFixingIssueId(null)

    // Simulation repair：Update local display graphData（does not affect store cache）
    setGraphData((prev) => ({
      ...prev,
      nodes: prev.nodes.map((node) =>
        node.health === 'warning' && Math.random() > 0.5 ? { ...node, health: 'healthy' } : node
      )
    }))
  }

  const showCommandNotice = (message: string) => {
    const totalDuration = 3000
    const fadeDuration = 200
    const hideDelay = Math.max(0, totalDuration - fadeDuration)
    setCommandNotice(message)
    setCommandNoticeVisible(true)
    if (commandNoticeHideTimerRef.current) {
      window.clearTimeout(commandNoticeHideTimerRef.current)
    }
    if (commandNoticeClearTimerRef.current) {
      window.clearTimeout(commandNoticeClearTimerRef.current)
    }
    commandNoticeHideTimerRef.current = window.setTimeout(() => {
      setCommandNoticeVisible(false)
      commandNoticeHideTimerRef.current = null
    }, hideDelay)
    commandNoticeClearTimerRef.current = window.setTimeout(() => {
      setCommandNotice(null)
      commandNoticeClearTimerRef.current = null
    }, totalDuration)
  }

  const handleCommandAction = (commandId: string) => {
    if (commandId === 'rebuild') {
      void handleRebuildCommand()
    }
  }

  const handleEmbeddingModelChange = async (
    aiModelId: number,
    trigger: 'switch' | 'default'
  ) => {
    if (!Number.isInteger(aiModelId) || aiModelId <= 0) {
      return
    }
    if (isApplyingModel) {
      return
    }

    const previousSelectedAiModelId = selectedEmbeddingAiModelId
    const previousDefaultAiModelId = defaultEmbeddingAiModelId
    setSelectedEmbeddingAiModelId(aiModelId)
    setIsApplyingModel(true)
    try {
      const response = await setKnowledgeEmbeddingModel(aiModelId)
      setDefaultEmbeddingAiModelId(response.aiModelId)
      setSelectedEmbeddingAiModelId(response.aiModelId)
      showCommandNotice(
        trigger === 'default'
          ? t('notifications.embeddingModelApplied', {
            aiModelId: response.aiModelId,
            revision: response.revision
          })
          : t('notifications.embeddingModelSwitched', {
            aiModelId: response.aiModelId,
            revision: response.revision
          })
      )
    } catch (error) {
      setSelectedEmbeddingAiModelId(previousSelectedAiModelId)
      setDefaultEmbeddingAiModelId(previousDefaultAiModelId)
      toast.error(
        t('notifications.setEmbeddingModelFailed', {
          message: resolveErrorMessage(error, t('errors.unknown'))
        })
      )
    } finally {
      setIsApplyingModel(false)
    }
  }

  const handleRebuildCommand = async () => {
    if (isRebuilding) {
      return
    }
    setIsRebuilding(true)
    setLoading(true)
    try {
      const response = await rebuildGraph()
      await refresh()
      showCommandNotice(
        t('notifications.rebuildAccepted', {
          graphUpserts: response.graphUpserts,
          embeddingTasks: response.embeddingTasks
        })
      )
    } catch (error) {
      toast.error(
        t('notifications.rebuildFailed', {
          message: resolveErrorMessage(error, t('errors.unknown'))
        })
      )
    } finally {
      setLoading(false)
      setIsRebuilding(false)
    }
  }

  const handleCommand = async () => {
    if (isExecuting || isRebuilding) return
    const prompt = inputValue.trim()
    if (!prompt) return
    setInputValue('')
    if (prompt === '/rebuild') {
      await handleRebuildCommand()
      return
    }
    setIsExecuting(true)
    if (searchAbortRef.current) {
      searchAbortRef.current.abort()
    }
    const controller = new AbortController()
    searchAbortRef.current = controller

    try {
      const searchResult = await searchAndMerge(prompt, 10, controller.signal)

      if (searchResult.nodes.length > 0) {
        const firstNode = searchResult.nodes[0]
        if (firstNode) setPendingFocusId(firstNode.id)
      }
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
      if (error instanceof Error && error.name === 'AbortError') {
        return
      }
      console.error('[KG] Search failed:', error)
    } finally {
      if (searchAbortRef.current === controller) {
        searchAbortRef.current = null
      }
      setIsExecuting(false)
    }
  }

  const handleAbort = () => {
    if (!isExecuting) return
    if (searchAbortRef.current) {
      searchAbortRef.current.abort()
      searchAbortRef.current = null
    }
    setIsExecuting(false)
  }

  const handleCompositionStart = () => {
    isComposingRef.current = true
  }

  const handleCompositionEnd = () => {
    isComposingRef.current = false
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.nativeEvent.isComposing || isComposingRef.current) {
      return
    }
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      handleCommand()
    }
  }
  const canSend = inputValue.trim().length > 0
  const isEmbeddingModelSelectDisabled = isApplyingModel || embeddingModelSelectOptions.length === 0
  const embeddingModelPlaceholder = isApplyingModel
    ? t('model.applying')
    : embeddingModelSelectOptions.length > 0
      ? t('model.select')
      : t('model.none')

  const handleTopEmbeddingModelChange = (nextModelValue: string) => {
    const aiModelId = Number.parseInt(nextModelValue, 10)
    if (!Number.isInteger(aiModelId) || aiModelId <= 0) {
      return
    }
    void handleEmbeddingModelChange(aiModelId, 'switch')
  }

  return (
    <div
      className={`relative h-full w-full overflow-hidden font-sans selection:bg-indigo-500/30 transition-colors duration-300 ${
        isDark ? 'bg-[#020617] text-white' : 'bg-slate-50 text-slate-900'
      }`}
    >
      <style>{`
        @keyframes scan {
          0% { top: 0%; opacity: 0; }
          10% { opacity: 0.5; }
          90% { opacity: 0.5; }
          100% { top: 100%; opacity: 0; }
        }
        .animate-scan {
          animation: scan 3s linear infinite;
        }
      `}</style>

      <div className="absolute inset-0">
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <div className="flex flex-col items-center gap-4">
              <Loader2 size={48} className="animate-spin text-indigo-500" />
              <p className={`text-sm ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                {t('loading.graph')}
              </p>
            </div>
          </div>
        ) : (
          <div
            ref={containerRef}
            className="h-full w-full transition-none"
            style={{
              backgroundColor: graphBackground,
              willChange: 'auto',
              contain: 'layout style paint'
            }}
          />
        )}
      </div>

      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-6 left-0 right-0 flex justify-center z-30">
          <div
            className={`pointer-events-auto flex items-center gap-4 px-4 py-2 backdrop-blur-md rounded-full shadow-xl max-w-[calc(100vw-1.5rem)] overflow-x-auto scrollbar-hide ${
              isDark
                ? 'bg-slate-900/60 border border-white/5'
                : 'bg-white/90 border border-slate-200 text-slate-700 shadow-slate-200/80'
            }`}
          >
            <div className="flex items-center gap-2">
              <Activity size={14} className="text-emerald-500" />
              <span className={`text-xs font-medium ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                {t('topStats.nodeQuality')}: <span className="text-emerald-500 font-mono">98.4%</span>
              </span>
            </div>
            <div className={`h-3 w-px ${isDark ? 'bg-white/10' : 'bg-slate-200'}`} />
            <div className="flex items-center gap-2">
              <Layers size={14} className="text-blue-500" />
              <span className={`text-xs font-medium ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                {t('topStats.nodes')}:{' '}
                <span className={`${isDark ? 'text-slate-200' : 'text-slate-800'} font-mono`}>
                  {graphData.nodes.length}
                </span>
              </span>
            </div>
            <div className={`h-3 w-px ${isDark ? 'bg-white/10' : 'bg-slate-200'}`} />
            <div className="flex items-center gap-2 shrink-0">
              <span className={`text-xs font-medium whitespace-nowrap ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                {t('topStats.currentEmbeddingModel')}
              </span>
              <Select
                value={selectedEmbeddingModelValue}
                onChange={handleTopEmbeddingModelChange}
                options={embeddingModelSelectOptions}
                disabled={isEmbeddingModelSelectDisabled}
                fullWidth={false}
                placeholder={embeddingModelPlaceholder}
                dropdownHeader={t('commands.selectEmbeddingModelHeader')}
                size="xs"
                className={`!w-auto !border-0 !bg-transparent !shadow-none !rounded-none !px-0 !py-0 hover:!bg-transparent focus:!ring-0 focus:!border-transparent ${
                  isDark ? '!text-slate-200' : '!text-slate-800'
                }`}
              />
              {isApplyingModel && <Loader2 size={12} className="animate-spin text-indigo-500 flex-shrink-0" />}
            </div>
          </div>
        </div>

        {ENABLE_AI_SENTINEL ? (
          <div className="absolute right-6 top-1/2 -translate-y-1/2 z-50 hidden lg:block pointer-events-auto">
            {isSentinelCollapsed ? (
              <div
                className={`w-16 ${panelHeightClassMap.limited} rounded-2xl shadow-2xl flex flex-col py-3 overflow-hidden ${
                  isDark ? 'bg-slate-900/70 border border-white/10' : 'bg-white/95 border border-slate-200'
                }`}
              >
                <div className="flex flex-col items-center gap-3 px-2">
                  <Tooltip content={t('sentinel.title')} side="left" className="w-full flex justify-center">
                    <button
                      type="button"
                      aria-label={t('sentinel.title')}
                      onClick={() => {
                        setFilterType(null)
                        setSelectedNodeId(null)
                        setHoverNodeId(null)
                        setIsSentinelCollapsed(false)
                      }}
                      className={`h-10 w-10 flex items-center justify-center rounded-lg border transition-colors ${
                        isDark ? 'border-white/10 hover:border-emerald-300/50' : 'border-slate-200 hover:border-emerald-400/60'
                      }`}
                    >
                      <BrainCircuit size={18} className="text-emerald-400" />
                    </button>
                  </Tooltip>
                  <div className={`h-px w-8 ${isDark ? 'bg-white/10' : 'bg-slate-200'}`} />
                </div>

                <div
                  ref={sentinelListRef}
                  className="mt-2 flex-1 min-h-0 px-2 pb-2 overflow-y-auto scrollbar-hide"
                >
                  <div className="flex flex-col items-center gap-3 py-2">
                  {FILTER_TYPE_OPTIONS.map(({ key, palette }) => {
                    const active = filterType === key
                    const keyLabel = resolveTypeLabel(key)
                    const tooltipLabel = resolveTypeTooltipLabel(key)
                    return (
                      <Tooltip
                        key={key}
                        content={tooltipLabel}
                        side="left"
                        className="w-full flex justify-center"
                      >
                          <button
                            type="button"
                            aria-label={keyLabel}
                            onClick={() => {
                              const nextType = active ? null : key
                              setFilterType(nextType)
                              setSelectedNodeId(null)
                              setHoverNodeId(null)
                              setIsSentinelCollapsed(true)
                            }}
                            className="h-10 w-10 flex items-center justify-center rounded-lg border transition-all"
                            style={{
                              backgroundColor: hexToRgba(palette.fill, active ? 0.2 : 0.08),
                              borderColor: hexToRgba(palette.fill, active ? 0.7 : 0.35),
                              boxShadow: active ? `0 0 0 2px ${hexToRgba(palette.fill, 0.4)}` : 'none',
                              color: palette.fill
                            }}
                          >
                            {palette.icon}
                          </button>
                        </Tooltip>
                      )
                    })}
                  </div>
                </div>
                <div className="flex flex-col items-center justify-center gap-1 pb-1 pt-1 text-slate-400/80 dark:text-slate-500/80">
                  <button
                    type="button"
                    aria-label={t('sentinel.moreActions')}
                    className="p-1 rounded-md transition-colors hover:text-slate-600 hover:bg-slate-50 dark:hover:text-slate-300 dark:hover:bg-slate-800"
                  >
                    <MoreHorizontal size={14} />
                  </button>
                </div>
              </div>
            ) : (
              <div
                className={`${panelWidthClassMap.narrow} ${panelHeightClassMap.limited} backdrop-blur-xl rounded-2xl shadow-2xl overflow-hidden relative flex flex-col ${
                  isDark ? 'bg-slate-900/60 border border-white/10' : 'bg-white/95 border border-slate-200'
                }`}
              >
                <div
                  className={`p-5 border-b relative ${
                    isDark
                      ? 'border-white/10 bg-gradient-to-b from-indigo-500/10 to-transparent'
                      : 'border-slate-200 bg-gradient-to-b from-indigo-50/60 to-transparent'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1 relative z-10">
                    <div className="flex items-center gap-2">
                      <BrainCircuit size={18} className="text-emerald-400" />
                      <h3 className={`text-sm font-bold tracking-wide ${isDark ? 'text-white' : 'text-slate-900'}`}>
                        {t('sentinel.title')}
                      </h3>
                    </div>
                    <div className="flex items-center gap-2">
                      {filterType && (
                        <span
                          className={`px-2 py-0.5 rounded text-micro font-bold uppercase tracking-wider ${
                            isDark ? 'bg-white/10 text-white border border-white/15' : 'bg-slate-100 text-slate-700 border border-slate-200'
                          }`}
                        >
                          {t('sentinel.filterLabel', { type: resolveTypeLabel(filterType) })}
                        </span>
                      )}
                      <button
                        type="button"
                        onClick={() => setIsSentinelCollapsed(true)}
                        className={`p-1.5 rounded-lg transition-colors ${
                          isDark ? 'hover:bg-white/10 text-slate-400' : 'hover:bg-slate-100 text-slate-500'
                        }`}
                      >
                        <X size={16} />
                      </button>
                    </div>
                  </div>
                  <p className={`text-micro ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                    {t('sentinel.description')}
                  </p>
                  <div
                    className={`absolute top-0 left-0 right-0 h-0.5 bg-gradient-to-r from-transparent via-emerald-500 to-transparent opacity-50 animate-scan pointer-events-none ${
                      isDark ? '' : 'opacity-60'
                    }`}
                  />
                </div>

                <div className="flex-1 overflow-y-auto p-4 space-y-3 custom-scrollbar max-h-[42vh]">
                  {issues.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-full text-center opacity-40">
                      <Shield size={48} className="text-emerald-500 mb-4" />
                      <h4 className={`text-sm font-medium ${isDark ? 'text-slate-300' : 'text-slate-600'}`}>
                        {t('sentinel.secureTitle')}
                      </h4>
                      <p className={`text-xs ${isDark ? 'text-slate-500' : 'text-slate-500'}`}>
                        {t('sentinel.secureDescription')}
                      </p>
                    </div>
                  ) : (
                    issues.map((issue) => (
                      <div
                        key={issue.id}
                        className={`rounded-xl p-4 transition-all group relative overflow-hidden ${
                          isDark
                            ? 'bg-slate-900/50 border border-white/10 hover:border-emerald-500/30'
                            : 'bg-slate-50 border border-slate-200 hover:border-emerald-200'
                        }`}
                      >
                        {fixingIssueId === issue.id && (
                          <div
                            className={`absolute inset-0 backdrop-blur-[1px] z-10 flex items-center justify-center ${
                              isDark ? 'bg-slate-950/80' : 'bg-white/70'
                            }`}
                          >
                            <div className="flex flex-col items-center gap-2">
                              <RefreshCw size={24} className="text-emerald-400 animate-spin" />
                              <span className="text-micro font-mono text-emerald-300">
                                {t('issues.healingProtocol')}
                              </span>
                            </div>
                          </div>
                        )}
                        <div className="flex justify-between items-start mb-2">
                          <span className="text-micro font-bold text-amber-500 uppercase tracking-wider">
                            {issue.type} Alert
                          </span>
                          <span className={`text-micro font-mono ${isDark ? 'text-slate-600' : 'text-slate-400'}`}>
                            ID:{issue.id.toString().padStart(3, '0')}
                          </span>
                        </div>
                        <h4 className={`text-sm font-bold mb-1 ${isDark ? 'text-slate-200' : 'text-slate-800'}`}>
                          {issue.title}
                        </h4>
                        <p className={`text-xs mb-4 leading-relaxed ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                          {issue.desc}
                        </p>
                        <button
                          type="button"
                          onClick={() => handleFixIssue(issue.id)}
                          className={`w-full py-2 border rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-2 group-hover:scale-[1.02] ${
                            isDark
                              ? 'bg-emerald-600/20 hover:bg-emerald-600/30 border-emerald-500/50 text-emerald-300 hover:text-white'
                              : 'bg-emerald-50 hover:bg-emerald-100 border-emerald-200 text-emerald-700 hover:text-emerald-800'
                          }`}
                        >
                          <Zap size={12} />
                          {t('issues.executeAutoFix')}
                        </button>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="absolute right-6 top-1/2 -translate-y-1/2 z-50 hidden lg:block pointer-events-auto">
            <div
              className={`w-16 ${panelHeightClassMap.limited} rounded-2xl shadow-2xl flex flex-col py-3 overflow-hidden ${
                isDark ? 'bg-slate-900/70 border border-white/10' : 'bg-white/95 border border-slate-200'
              }`}
            >
              <div className="flex flex-col items-center gap-3 px-2">
                <Tooltip content={t('sentinel.disabled')} side="left" className="w-full flex justify-center">
                  <button
                    type="button"
                    disabled
                    aria-label={t('sentinel.title')}
                    className={`h-10 w-10 flex items-center justify-center rounded-lg border cursor-not-allowed opacity-50 ${
                      isDark ? 'border-white/10' : 'border-slate-200'
                    }`}
                  >
                    <BrainCircuit size={18} className="text-emerald-400" />
                  </button>
                </Tooltip>
                <div className={`h-px w-8 ${isDark ? 'bg-white/10' : 'bg-slate-200'}`} />
              </div>

              <div
                ref={sentinelListRef}
                className="mt-2 flex-1 min-h-0 px-2 pb-2 overflow-y-auto scrollbar-hide"
              >
                <div className="flex flex-col items-center gap-3 py-2">
                  {FILTER_TYPE_OPTIONS.map(({ key, palette }) => {
                    const active = filterType === key
                    const keyLabel = resolveTypeLabel(key)
                    const tooltipLabel = resolveTypeTooltipLabel(key)
                    return (
                      <Tooltip
                        key={key}
                        content={tooltipLabel}
                        side="left"
                        className="w-full flex justify-center"
                      >
                        <button
                          type="button"
                          aria-label={keyLabel}
                          onClick={() => {
                            const nextType = active ? null : key
                            setFilterType(nextType)
                            setSelectedNodeId(null)
                            setHoverNodeId(null)
                          }}
                          className="h-10 w-10 flex items-center justify-center rounded-lg border transition-all"
                          style={{
                            backgroundColor: hexToRgba(palette.fill, active ? 0.2 : 0.08),
                            borderColor: hexToRgba(palette.fill, active ? 0.7 : 0.35),
                            boxShadow: active ? `0 0 0 2px ${hexToRgba(palette.fill, 0.4)}` : 'none',
                            color: palette.fill
                          }}
                        >
                          {palette.icon}
                        </button>
                      </Tooltip>
                    )
                  })}
                </div>
              </div>
            </div>
          </div>
        )}

        {activeNode && (
            <div
              className={`absolute z-50 inset-x-4 bottom-4 lg:inset-auto lg:left-6 lg:right-auto lg:top-1/2 lg:-translate-y-1/2 ${panelWidthClassMap.narrow} ${panelHeightClassMap.limited} backdrop-blur-xl rounded-2xl shadow-2xl flex flex-col pointer-events-auto overflow-hidden ${
                isDark ? 'bg-[#0B1121]/90 border border-white/10' : 'bg-white/95 border border-slate-200'
              }`}
            style={
              activeNodeColor
                ? {
                    borderColor: hexToRgba(activeNodeColor, 0.35)
                  }
                : undefined
            }
          >
              <div
                className={`p-4 border-b flex justify-between items-start ${
                  isDark ? 'border-white/10 bg-transparent' : 'border-slate-200 bg-transparent'
                }`}
                style={
                  activeNodeColor
                    ? {
                        background: `linear-gradient(90deg, ${hexToRgba(activeNodeColor, isDark ? 0.1 : 0.14)} 0%, transparent 65%)`,
                        borderColor: hexToRgba(activeNodeColor, 0.35)
                      }
                    : undefined
                }
              >
                <div>
                  <div className="flex items-center gap-2 mb-2">
                    <div
                      className="p-1.5 rounded-lg border"
                      style={
                        activeNodeColor
                          ? {
                              backgroundColor: hexToRgba(activeNodeColor, isDark ? 0.22 : 0.16),
                              borderColor: hexToRgba(activeNodeColor, 0.45),
                              color: activeNodeColor
                            }
                          : undefined
                      }
                    >
                      {activeIcon}
                    </div>
                    <span
                      className={`text-micro font-bold uppercase tracking-wider ${isDark ? 'text-slate-400' : 'text-slate-500'}`}
                      style={activeNodeColor ? { color: activeNodeColor } : undefined}
                    >
                      {resolveTypeLabel(activeNode.type)}
                    </span>
                    {isCardPinned && (
                      <span
                        className={`text-micro px-2 py-0.5 rounded-full font-bold ${
                          isDark ? 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/30' : 'bg-emerald-50 text-emerald-600 border-emerald-200'
                        }`}
                      >
                        {t('nodeDetail.pinned')}
                      </span>
                    )}
                  </div>
                  <h2 className={`text-base font-semibold tracking-tight ${isDark ? 'text-white' : 'text-slate-900'}`}>
                    {activeNode.name}
                  </h2>
                  <p className={`text-xs font-mono ${isDark ? 'text-slate-500' : 'text-slate-500'}`}>
                    {t('nodeDetail.nodeId', { id: activeNode.id })}
                  </p>
                </div>
                <button
                  type="button"
                onClick={() => {
                  if (activeNode) {
                    nvlRef.current?.unPinNode([activeNode.id])
                  }
                  setSelectedNodeId(null)
                  setHoverNodeId(null)
                  setIsCardPinned(false)
                }}
                className={`transition-colors ${isDark ? 'text-slate-500 hover:text-white' : 'text-slate-400 hover:text-slate-900'}`}
              >
                <X size={20} />
              </button>
              </div>

              <div className="flex-1 overflow-y-auto p-4 space-y-4 custom-scrollbar max-h-full h-full">
                <div>
                  <h4 className={`text-xs font-bold uppercase tracking-wider mb-2 ${isDark ? 'text-slate-500' : 'text-slate-500'}`}>
                    {t('nodeDetail.displayName')}
                  </h4>
                  <p
                    className={`text-xs leading-relaxed p-3 rounded-lg border ${
                      isDark ? 'text-slate-300 bg-slate-900/50 border-white/5' : 'text-slate-700 bg-slate-50 border-slate-200'
                    }`}
                  >
                    {activeNode.displayName || t('nodeDetail.noDisplayName')}
                  </p>
                </div>

                <div>
                  <h4 className={`text-xs font-bold uppercase tracking-wider mb-2 ${isDark ? 'text-slate-500' : 'text-slate-500'}`}>
                    {t('nodeDetail.description')}
                  </h4>
                  <p
                    className={`text-xs leading-relaxed p-3 rounded-lg border ${
                      isDark ? 'text-slate-300 bg-slate-900/50 border-white/5' : 'text-slate-700 bg-slate-50 border-slate-200'
                    }`}
                  >
                    {activeNode.description ||
                      t('nodeDetail.noDescription')}
                  </p>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h4
                      className={`text-xs font-bold uppercase tracking-wider mb-2 flex items-center gap-1 ${
                        isDark ? 'text-slate-500' : 'text-slate-500'
                      }`}
                    >
                      <User size={12} /> {t('nodeDetail.owner')}
                    </h4>
                    <span className={`text-sm font-medium ${isDark ? 'text-slate-200' : 'text-slate-800'}`}>
                      {activeNode.owner || t('nodeDetail.unassigned')}
                    </span>
                  </div>
                  <div>
                    <h4
                      className={`text-xs font-bold uppercase tracking-wider mb-2 flex items-center gap-1 ${
                        isDark ? 'text-slate-500' : 'text-slate-500'
                      }`}
                    >
                      <Clock size={12} /> {t('nodeDetail.updated')}
                    </h4>
                    <span className={`text-sm font-medium ${isDark ? 'text-slate-200' : 'text-slate-800'}`}>
                      {activeNode.lastUpdated || t('nodeDetail.unknown')}
                    </span>
                  </div>
                </div>

                {activeNode.tags && (
                  <div>
                    <h4
                      className={`text-xs font-bold uppercase tracking-wider mb-2 flex items-center gap-1 ${
                        isDark ? 'text-slate-500' : 'text-slate-500'
                      }`}
                    >
                      <Tag size={12} /> {t('nodeDetail.tags')}
                    </h4>
                    <div className="flex flex-wrap gap-2">
                      {activeNode.tags.map((tag) => (
                        <span
                          key={tag}
                          className={`px-2 py-1 rounded border text-xs ${
                            isDark ? 'bg-slate-800 border-white/10 text-slate-300' : 'bg-slate-100 border-slate-200 text-slate-700'
                          }`}
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {activeNode.schema && (
                  <div>
                    <div className="flex justify-between items-center mb-2">
                      <h4
                        className={`text-xs font-bold uppercase tracking-wider flex items-center gap-1 ${
                          isDark ? 'text-slate-500' : 'text-slate-500'
                        }`}
                      >
                        <Database size={12} /> {t('nodeDetail.schema')}
                      </h4>
                      <button
                        type="button"
                        className={`text-micro flex items-center gap-1 ${
                          isDark ? 'text-indigo-400 hover:text-indigo-300' : 'text-indigo-600 hover:text-indigo-500'
                        }`}
                      >
                        {t('nodeDetail.viewFullDdl')} <ArrowUp size={10} className="rotate-45" />
                      </button>
                    </div>
                    <div
                      className={`rounded-lg overflow-hidden border ${
                        isDark ? 'bg-slate-900 border-white/10' : 'bg-white border-slate-200'
                      }`}
                    >
                      <table className="w-full text-left text-xs">
                        <thead className={isDark ? 'bg-slate-800/50 text-slate-400' : 'bg-slate-100 text-slate-500'}>
                          <tr>
                            <th className="px-3 py-2 font-medium">{t('nodeDetail.table.column')}</th>
                            <th className="px-3 py-2 font-medium">{t('nodeDetail.table.type')}</th>
                            <th className="px-3 py-2 text-right">{t('nodeDetail.table.attr')}</th>
                          </tr>
                        </thead>
                        <tbody className={isDark ? 'divide-y divide-white/5' : 'divide-y divide-slate-200'}>
                          {activeNode.schema.map((col, index) => (
                            <tr
                              key={index}
                              className={`transition-colors group ${isDark ? 'hover:bg-white/5' : 'hover:bg-slate-50'}`}
                            >
                              <td className={`px-3 py-2 font-mono ${isDark ? 'text-slate-200' : 'text-slate-800'}`}>{col.name}</td>
                              <td className={`px-3 py-2 ${isDark ? 'text-slate-500' : 'text-slate-600'}`}>{col.type}</td>
                              <td className="px-3 py-2 text-right">
                                {col.key && (
                                  <span
                                    className={`text-nano px-1 py-0.5 rounded border ${
                                      isDark
                                        ? 'bg-amber-500/10 text-amber-500 border-amber-500/20'
                                        : 'bg-amber-50 text-amber-600 border-amber-200'
                                    }`}
                                  >
                                    PK
                                  </span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>

              <div
                className={`p-4 flex gap-2 border-t ${
                  isDark ? 'border-white/10 bg-slate-900/50' : 'border-slate-200 bg-slate-50'
                }`}
              >
                <button
                  type="button"
                  className="flex-1 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-colors shadow-lg shadow-indigo-500/20"
                >
                  {t('actions.lineage')}
                </button>
                <button
                  type="button"
                  className={`flex-1 py-2 rounded-lg text-xs font-bold transition-colors border ${
                    isDark
                      ? 'bg-slate-800 hover:bg-slate-700 text-slate-200 border-white/5'
                      : 'bg-white hover:bg-slate-100 text-slate-700 border-slate-200'
                  }`}
                >
                  {t('actions.profile')}
                </button>
                <button
                  type="button"
                  className={`p-2 rounded-lg transition-colors border ${
                    isDark
                      ? 'bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white border-white/5'
                      : 'bg-white hover:bg-slate-100 text-slate-500 hover:text-slate-900 border-slate-200'
                  }`}
                >
                  <MoreHorizontal size={16} />
                </button>
              </div>
            </div>
        )}

        <div className="absolute bottom-6 left-0 right-0 z-40 pointer-events-auto">
          <div className="relative mx-auto w-full max-w-screen-sm px-4">
            {commandNotice && (
              <div className="pointer-events-none absolute inset-0 z-50 flex items-center justify-center px-16">
                <div
                  className={`rounded-lg border border-blue-100/80 dark:border-blue-900/60 bg-blue-50/90 dark:bg-blue-900/30 px-3 py-1.5 text-xs font-medium text-blue-700 dark:text-blue-300 shadow-sm transition-all duration-200 ease-out ${
                    commandNoticeVisible ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-1'
                  }`}
                >
                  {commandNotice}
                </div>
              </div>
            )}
            <ChatInput
              input={inputValue}
              onInputChange={setInputValue}
              onCompositionStart={handleCompositionStart}
              onCompositionEnd={handleCompositionEnd}
              onKeyDown={handleKeyDown}
              onSend={handleCommand}
              onAbort={handleAbort}
              isGenerating={isExecuting}
              isWaitingForResume={false}
              canSend={canSend}
              selectedModelId={selectedChatAiModelId}
              defaultModelId={defaultChatAiModelId}
              modelOptions={chatModelOptions}
              onModelChange={setSelectedChatAiModelId}
              modelDropdownHeader={t('commands.selectChatModelHeader')}
              text={{
                placeholder: t('chatInput.placeholder'),
                placeholderResume: t('chatInput.placeholderResume'),
                sendLabel: t('chatInput.send'),
                stopLabel: t('chatInput.stop'),
                continueLabel: t('chatInput.continue'),
                noModelPermissionHint: t('chatInput.noModelPermissionHint'),
                modelButtonTitle: t('chatInput.modelButtonTitle'),
                commandLibrary: t('chatInput.commandLibrary'),
                intelligenceEnhancement: t('chatInput.intelligenceEnhancement'),
                safetyHintDefault: t('chatInput.safetyHintDefault'),
                safetyHintResume: t('chatInput.safetyHintResume'),
                defaultModel: t('chatInput.defaultModel'),
                setAsDefaultModel: t('chatInput.setAsDefaultModel')
              }}
              commandOptions={knowledgeGraphCommandOptions}
              onCommand={handleCommandAction}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
