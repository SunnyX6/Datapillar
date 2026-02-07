import { type JSX, type KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
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
  BrainCircuit,
  Clock,
  Columns3,
  Database,
  Globe,
  Layers,
  Loader2,
  MoreHorizontal,
  Network,
  RefreshCw,
  Shield,
  Table as TableIcon,
  Tag,
  User,
  Waypoints,
  X,
  Zap
} from 'lucide-react'
import { panelWidthClassMap, panelHeightClassMap } from '@/design-tokens/dimensions'
import { useIsDark, useKnowledgeGraphStore } from '@/stores'
import type { GraphData, GraphNode, GraphLink } from '@/services/knowledgeGraphService'
import { Tooltip } from '@/components/ui'
import { ChatInput, type ChatCommandOption } from '@/components/ui/ChatInput'
import { DEFAULT_WORKFLOW_MODEL_ID, WORKFLOW_MODEL_OPTIONS } from '@/config/workflowModels'

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
    keys: ['domain'],
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
    keys: ['subject'],
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
    keys: ['atomicmetric', 'derivedmetric', 'compositemetric'],
    palette: {
      fill: '#10b981',
      badgeBg: 'bg-emerald-500/20',
      badgeText: 'text-emerald-400',
      icon: <Activity size={16} />
    }
  },
  {
    keys: ['qualityrule'],
    palette: {
      fill: '#f43f5e',
      badgeBg: 'bg-rose-500/20',
      badgeText: 'text-rose-400',
      icon: <Shield size={16} />
    }
  }
]

const KNOWLEDGE_GRAPH_COMMAND_OPTIONS: ChatCommandOption[] = [
  {
    id: 'sync',
    label: '/sync',
    title: '同步',
    description: '同步元数据与关系'
  },
  {
    id: 'rebuild',
    label: '/rebuild',
    title: '向量重建',
    description: '重新构建向量索引'
  }
]

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

function filterGraphByType(type: string | null, graph: GraphData): GraphData {
  if (!type) return graph
  const normalized = type.toLowerCase()
  const typeMap = new Map(graph.nodes.map(node => [node.id, (node.type || '').toLowerCase()]))

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

  const seedNodes = graph.nodes.filter(node => {
    const nodeType = (node.type || '').toLowerCase()
    if (isTableView) {
      return nodeType === 'table' || nodeType === 'join'
    }
    return nodeType === normalized
  })

  if (!seedNodes.length) return { nodes: [], links: [] }

  const nodeIds = new Set(seedNodes.map(node => node.id))

  if (isTableView) {
    graph.links.forEach(link => {
      const sourceId = String(link.source)
      const targetId = String(link.target)
      const sourceType = typeMap.get(sourceId)
      const targetType = typeMap.get(targetId)

      const isSourceJoin = sourceType === 'join'
      const isTargetJoin = targetType === 'join'
      const isSourceColumn = sourceType === 'column'
      const isTargetColumn = targetType === 'column'

      // 只包含与 join 相连的列
      if (nodeIds.has(sourceId) && isSourceJoin && isTargetColumn) {
        nodeIds.add(targetId)
      }
      if (nodeIds.has(targetId) && isTargetJoin && isSourceColumn) {
        nodeIds.add(sourceId)
      }
    })
  }

  if (isColumnView) {
    const columnTableIds = new Set<string>()
    graph.links.forEach(link => {
      const sourceId = String(link.source)
      const targetId = String(link.target)
      const sourceType = typeMap.get(sourceId)
      const targetType = typeMap.get(targetId)

      const sourceIsColumn = sourceType === 'column'
      const targetIsColumn = targetType === 'column'
      const sourceIsTable = sourceType === 'table'
      const targetIsTable = targetType === 'table'

      const isColumnTablePair =
        (sourceIsColumn && targetIsTable) ||
        (targetIsColumn && sourceIsTable)

      if (isColumnTablePair) {
        columnTableIds.add(sourceId)
        columnTableIds.add(targetId)
      }
    })
    if (!columnTableIds.size) return { nodes: [], links: [] }
    columnTableIds.forEach(id => nodeIds.add(id))
  }

  const nodes = graph.nodes.filter(node => nodeIds.has(node.id))

  const links = graph.links.filter(link => {
    const sourceIn = nodeIds.has(String(link.source))
    const targetIn = nodeIds.has(String(link.target))
    return sourceIn && targetIn
  })

  return { nodes, links }
}

const GOVERNANCE_ISSUES = [
  {
    id: 1,
    type: 'Metadata',
    title: 'Missing Descriptions',
    desc: '2 tables in Silver Layer lack business descriptions.',
    impact: 'High'
  },
  {
    id: 2,
    type: 'Quality',
    title: 'Schema Drift',
    desc: 'Source schema changed for ingest_logs.',
    impact: 'Medium'
  }
]

export function KnowledgeGraphView() {
  const nvlRef = useRef<NVL | null>(null)
  const containerRef = useRef<HTMLDivElement | null>(null)
  const sentinelListRef = useRef<HTMLDivElement | null>(null)
  const isCardPinnedRef = useRef(false)
  const selectedNodeIdRef = useRef<string | null>(null)
  const filterTypeRef = useRef<string | null>(null)
  const navigate = useNavigate()
  const searchAbortRef = useRef<AbortController | null>(null)
  const commandNoticeHideTimerRef = useRef<number | null>(null)
  const commandNoticeClearTimerRef = useRef<number | null>(null)
  const isComposingRef = useRef(false)

  // 使用全局 store 管理图数据
  const {
    allGraphData,
    isLoading: storeLoading,
    loadInitialGraph,
    searchAndMerge
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
  const [selectedModelId, setSelectedModelId] = useState(DEFAULT_WORKFLOW_MODEL_ID)
  const [commandNotice, setCommandNotice] = useState<string | null>(null)
  const [commandNoticeVisible, setCommandNoticeVisible] = useState(false)
  const [isSentinelCollapsed, setIsSentinelCollapsed] = useState(false)
  const isDark = useIsDark()
  const graphBackground = isDark ? '#020617' : '#f8fafc'
  const activeNodeId = selectedNodeId
  const activeNode = useMemo(
    () => graphData.nodes.find((node) => node.id === activeNodeId) ?? null,
    [activeNodeId, graphData.nodes]
  )
  const activeIcon = useMemo(() => (activeNode ? getNodeTypePalette(activeNode.type).icon : <User size={16} />), [activeNode])
  const activeNodeColor = useMemo(() => (activeNode ? getNodeColor(activeNode) : null), [activeNode])

  const nvlNodes = useMemo<NvlNodeWithMeta[]>(() => {
    return graphData.nodes.map(node => {
      let color = getNodeColor(node)
      if (node.health === 'warning') color = '#DC2626'

      // 优先展示 service 返回的 name；若缺失则兜底 label/id
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

  // 当 store 的 allGraphData 或 filterType 变化时，更新显示的 graphData
  useEffect(() => {
    setGraphData(filterGraphByType(filterType, allGraphData))
  }, [filterType, allGraphData])

  // 加载初始数据（store 内部会检查缓存）
  useEffect(() => {
    const load = async () => {
      setLoading(true)
      await loadInitialGraph(100)
      setLoading(false)
    }
    load()
  }, [loadInitialGraph])

  // store 数据变化时同步 loading 状态
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

  // NVL 实例初始化和交互处理器管理
  useEffect(() => {
    if (loading || !containerRef.current) return

    // 初始化 NVL 实例
    const nvl = new NVL(containerRef.current, nvlNodes, nvlRelationships, nvlOptions)
    nvlRef.current = nvl

    // 初始化交互处理器
    const panInteraction = new PanInteraction(nvl)
    const zoomInteraction = new ZoomInteraction(nvl)
    const dragInteraction = new DragNodeInteraction(nvl)
    const clickInteraction = new ClickInteraction(nvl)
    const hoverInteraction = new HoverInteraction(nvl)

    // 监听过渡结束事件，侧边栏折叠动画完成后重新适配
    const container = containerRef.current
    const handleTransitionEnd = (e: TransitionEvent) => {
      // 检查是否是包含此容器的父元素的 width 过渡结束
      if (e.propertyName === 'width' && (e.target as HTMLElement)?.contains(container)) {
        nvlRef.current?.fit([], { animated: false })
      }
    }
    document.addEventListener('transitionend', handleTransitionEnd)

    // 状态追踪（避免全量重算）
    let lastSelectedId: string | null = null

    // 点击回调：节点点击选中并固定卡片
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

    // 点击回调：双击解除固定
    clickInteraction.updateCallback('onNodeDoubleClick', (node: NvlNode) => {
      nvl.unPinNode([node.id])
      setIsCardPinned(false)
    })

    // 点击回调：关系点击（取消选中）
    clickInteraction.updateCallback('onRelationshipClick', () => {
      if (lastSelectedId) {
        nvl.updateElementsInGraph([{ id: lastSelectedId, selected: false }], [])
        nvl.unPinNode([lastSelectedId])
        lastSelectedId = null
      }
      setSelectedNodeId(null)
      setIsCardPinned(false)
    })

    // 点击回调：画布点击（取消选中和悬停）
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

    // 拖拽回调：拖拽结束后固定节点
    dragInteraction.updateCallback('onDragEnd', (nodes) => {
      if (!nodes?.length) return
      nvl.updateElementsInGraph(
        nodes.map((n: NvlNode) => ({ id: n.id, pinned: true })),
        []
      )
    })

    // 悬停回调：只更新单个节点的 hovered 状态，不触发全量重算
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

    // 清理函数
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
    // nvlNodes/nvlRelationships 在此仅用于初始化，数据更新由下方独立 useEffect 处理
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, nvlOptions])

  // 数据更新时同步到 NVL
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
    // 便于本地排查，暴露 NVL 与节点数据
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

    // 模拟修复：更新本地显示的 graphData（不影响 store 缓存）
    setGraphData((prev) => ({
      ...prev,
      nodes: prev.nodes.map((node) =>
        node.health === 'warning' && Math.random() > 0.5 ? { ...node, health: 'healthy' } : node
      )
    }))
  }

  const handleManageModels = () => {
    navigate('/profile/llm/models')
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
    const command = KNOWLEDGE_GRAPH_COMMAND_OPTIONS.find((item) => item.id === commandId)
    const label = command?.label ?? `/${commandId}`
    if (commandId === 'sync' || commandId === 'rebuild') {
      showCommandNotice(`已触发 ${label}，待后端接入后生效`)
      return
    }
  }

  const handleCommand = async () => {
    if (isExecuting) return
    const prompt = inputValue.trim()
    if (!prompt) return
    setInputValue('')
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
      console.error('[KG] 搜索失败:', error)
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
                加载知识图谱数据...
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
            className={`flex items-center gap-6 px-6 py-2 backdrop-blur-md rounded-full shadow-xl ${
              isDark
                ? 'bg-slate-900/60 border border-white/5'
                : 'bg-white/90 border border-slate-200 text-slate-700 shadow-slate-200/80'
            }`}
          >
            <div className="flex items-center gap-2">
              <Activity size={14} className="text-emerald-500" />
              <span className={`text-xs font-medium ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                Data Quality: <span className="text-emerald-500 font-mono">98.4%</span>
              </span>
            </div>
            <div className={`h-3 w-px ${isDark ? 'bg-white/10' : 'bg-slate-200'}`} />
            <div className="flex items-center gap-2">
              <Layers size={14} className="text-blue-500" />
              <span className={`text-xs font-medium ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                Nodes:{' '}
                <span className={`${isDark ? 'text-slate-200' : 'text-slate-800'} font-mono`}>
                  {graphData.nodes.length}
                </span>
              </span>
            </div>
            <div className={`h-3 w-px ${isDark ? 'bg-white/10' : 'bg-slate-200'}`} />
            <div className="flex items-center gap-2">
              <Globe size={14} className="text-purple-500" />
              <span className={`text-xs font-medium ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>
                Federation: <span className={`${isDark ? 'text-slate-200' : 'text-slate-800'}`}>Active</span>
              </span>
            </div>
          </div>
        </div>

        <div className="absolute right-6 top-1/2 -translate-y-1/2 z-50 hidden lg:block pointer-events-auto">
          {isSentinelCollapsed ? (
            <div
              className={`w-16 ${panelHeightClassMap.limited} rounded-2xl shadow-2xl flex flex-col py-3 overflow-hidden ${
                isDark ? 'bg-slate-900/70 border border-white/10' : 'bg-white/95 border border-slate-200'
              }`}
            >
              <div className="flex flex-col items-center gap-3 px-2">
                <Tooltip content="AI Sentinel" side="left" className="w-full flex justify-center">
                  <button
                    type="button"
                    aria-label="AI Sentinel"
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
                  {TYPE_PALETTE_MAP.map(({ keys, palette }) => {
                    const key = keys[0]
                    const active = filterType === key
                    return (
                      <Tooltip
                        key={key}
                        content={key.charAt(0).toUpperCase() + key.slice(1)}
                        side="left"
                        className="w-full flex justify-center"
                      >
                        <button
                          type="button"
                          aria-label={key.charAt(0).toUpperCase() + key.slice(1)}
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
                  aria-label="更多操作"
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
                    <h3 className={`text-sm font-bold tracking-wide ${isDark ? 'text-white' : 'text-slate-900'}`}>AI Sentinel</h3>
                  </div>
                  <div className="flex items-center gap-2">
                    {filterType && (
                      <span
                        className={`px-2 py-0.5 rounded text-micro font-bold uppercase tracking-wider ${
                          isDark ? 'bg-white/10 text-white border border-white/15' : 'bg-slate-100 text-slate-700 border border-slate-200'
                        }`}
                      >
                        Filter: {filterType}
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
                <p className={`text-micro ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>Autonomous governance & healing.</p>
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
                    <h4 className={`text-sm font-medium ${isDark ? 'text-slate-300' : 'text-slate-600'}`}>Sector Secure</h4>
                    <p className={`text-xs ${isDark ? 'text-slate-500' : 'text-slate-500'}`}>No anomalies detected.</p>
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
                            <span className="text-micro font-mono text-emerald-300">HEALING_PROTOCOL...</span>
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
                      <h4 className={`text-sm font-bold mb-1 ${isDark ? 'text-slate-200' : 'text-slate-800'}`}>{issue.title}</h4>
                      <p className={`text-xs mb-4 leading-relaxed ${isDark ? 'text-slate-400' : 'text-slate-600'}`}>{issue.desc}</p>
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
                        Execute Auto-Fix
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>

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
                      {activeNode.type}
                    </span>
                    {isCardPinned && (
                      <span
                        className={`text-micro px-2 py-0.5 rounded-full font-bold ${
                          isDark ? 'bg-emerald-500/10 text-emerald-300 border border-emerald-500/30' : 'bg-emerald-50 text-emerald-600 border-emerald-200'
                        }`}
                      >
                        Pinned
                      </span>
                    )}
                  </div>
                  <h2 className={`text-base font-semibold tracking-tight ${isDark ? 'text-white' : 'text-slate-900'}`}>
                    {activeNode.name}
                  </h2>
                  <p className={`text-xs font-mono ${isDark ? 'text-slate-500' : 'text-slate-500'}`}>
                    ID: {activeNode.id}
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
                  <h4 className={`text-xs font-bold uppercase tracking-wider mb-2 ${isDark ? 'text-slate-500' : 'text-slate-500'}`}>Display Name</h4>
                  <p
                    className={`text-xs leading-relaxed p-3 rounded-lg border ${
                      isDark ? 'text-slate-300 bg-slate-900/50 border-white/5' : 'text-slate-700 bg-slate-50 border-slate-200'
                    }`}
                  >
                    {activeNode.displayName || 'No display name provided.'}
                  </p>
                </div>

                <div>
                  <h4 className={`text-xs font-bold uppercase tracking-wider mb-2 ${isDark ? 'text-slate-500' : 'text-slate-500'}`}>Description</h4>
                  <p
                    className={`text-xs leading-relaxed p-3 rounded-lg border ${
                      isDark ? 'text-slate-300 bg-slate-900/50 border-white/5' : 'text-slate-700 bg-slate-50 border-slate-200'
                    }`}
                  >
                    {activeNode.description ||
                      'No description available for this entity. Consider running an AI Metadata scan to auto-populate.'}
                  </p>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h4
                      className={`text-xs font-bold uppercase tracking-wider mb-2 flex items-center gap-1 ${
                        isDark ? 'text-slate-500' : 'text-slate-500'
                      }`}
                    >
                      <User size={12} /> Owner
                    </h4>
                    <span className={`text-sm font-medium ${isDark ? 'text-slate-200' : 'text-slate-800'}`}>
                      {activeNode.owner || 'Unassigned'}
                    </span>
                  </div>
                  <div>
                    <h4
                      className={`text-xs font-bold uppercase tracking-wider mb-2 flex items-center gap-1 ${
                        isDark ? 'text-slate-500' : 'text-slate-500'
                      }`}
                    >
                      <Clock size={12} /> Updated
                    </h4>
                    <span className={`text-sm font-medium ${isDark ? 'text-slate-200' : 'text-slate-800'}`}>
                      {activeNode.lastUpdated || 'Unknown'}
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
                      <Tag size={12} /> Tags
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
                        <Database size={12} /> Schema
                      </h4>
                      <button
                        type="button"
                        className={`text-micro flex items-center gap-1 ${
                          isDark ? 'text-indigo-400 hover:text-indigo-300' : 'text-indigo-600 hover:text-indigo-500'
                        }`}
                      >
                        View Full DDL <ArrowUp size={10} className="rotate-45" />
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
                            <th className="px-3 py-2 font-medium">Column</th>
                            <th className="px-3 py-2 font-medium">Type</th>
                            <th className="px-3 py-2 text-right">Attr</th>
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
                  Lineage
                </button>
                <button
                  type="button"
                  className={`flex-1 py-2 rounded-lg text-xs font-bold transition-colors border ${
                    isDark
                      ? 'bg-slate-800 hover:bg-slate-700 text-slate-200 border-white/5'
                      : 'bg-white hover:bg-slate-100 text-slate-700 border-slate-200'
                  }`}
                >
                  Profile
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
          <div className="relative mx-auto w-full max-w-[640px] px-4">
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
              selectedModelId={selectedModelId}
              defaultModelId={DEFAULT_WORKFLOW_MODEL_ID}
              modelOptions={WORKFLOW_MODEL_OPTIONS}
              onModelChange={setSelectedModelId}
              onManageModels={handleManageModels}
              modelDropdownHeader="选择向量模型"
              commandOptions={KNOWLEDGE_GRAPH_COMMAND_OPTIONS}
              onCommand={handleCommandAction}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
