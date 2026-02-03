import type { WorkflowEdgeDefinition, WorkflowGraph, WorkflowNodeDefinition } from '@/services/workflowStudioService'

const DEFAULT_NODE_WIDTH = 172
const DEFAULT_NODE_HEIGHT = 96
const DEFAULT_COLUMN_GAP = 300
const DEFAULT_ROW_GAP = 160

export interface WorkflowLayoutOptions {
  nodeWidth: number
  nodeHeight: number
  columnGap: number
  rowGap: number
}

export interface FormattedWorkflowNode extends WorkflowNodeDefinition {
  column: number
  row: number
  position: { x: number; y: number }
}

export interface WorkflowLayoutResult {
  nodes: FormattedWorkflowNode[]
  edges: WorkflowEdgeDefinition[]
  bounds: { width: number; height: number }
  spacing: WorkflowLayoutOptions
  viewport: { preferredZoom: number; offsetX: number; offsetY: number }
  diagnostics: { columnCount: number; maxRows: number }
}

const defaultOptions: WorkflowLayoutOptions = {
  nodeWidth: DEFAULT_NODE_WIDTH,
  nodeHeight: DEFAULT_NODE_HEIGHT,
  columnGap: DEFAULT_COLUMN_GAP,
  rowGap: DEFAULT_ROW_GAP
}

const buildAdjacency = (edges: WorkflowEdgeDefinition[]) => {
  const adjacency = new Map<string, Set<string>>()
  edges.forEach(({ source, target }) => {
    if (!adjacency.has(source)) {
      adjacency.set(source, new Set())
    }
    adjacency.get(source)?.add(target)
  })
  return adjacency
}

const computeColumns = (graph: WorkflowGraph) => {
  const adjacency = buildAdjacency(graph.edges)
  const indegree = new Map<string, number>()
  const nodeMap = new Map<string, WorkflowNodeDefinition>()

  graph.nodes.forEach((node) => {
    indegree.set(String(node.id), 0)
    nodeMap.set(String(node.id), node)
  })

  graph.edges.forEach(({ target }) => {
    indegree.set(target, (indegree.get(target) ?? 0) + 1)
  })

  const columns = new Map<string, number>()
  const queue = graph.nodes
    .filter((node) => (indegree.get(String(node.id)) ?? 0) === 0)
    .sort((a, b) => a.label.localeCompare(b.label))

  let columnIndex = 0

  while (queue.length > 0) {
    const levelSize = queue.length
    for (let index = 0; index < levelSize; index += 1) {
      const node = queue.shift()
      if (!node) continue
      columns.set(String(node.id), columnIndex)

      const targets = adjacency.get(String(node.id))
      targets?.forEach((targetId) => {
        const nextDegree = (indegree.get(targetId) ?? 0) - 1
        indegree.set(targetId, nextDegree)
        if (nextDegree === 0) {
          const targetNode = nodeMap.get(targetId)
          if (targetNode) {
            queue.push(targetNode)
          }
        }
      })
    }
    queue.sort((a, b) => a.label.localeCompare(b.label))
    columnIndex += 1
  }

  graph.nodes.forEach((node) => {
    if (!columns.has(String(node.id))) {
      columns.set(String(node.id), columnIndex)
      columnIndex += 1
    }
  })

  return columns
}

const gatherRowAssignments = (nodes: WorkflowNodeDefinition[], columns: Map<string, number>) => {
  const byColumn = new Map<number, WorkflowNodeDefinition[]>()
  nodes.forEach((node) => {
    const column = columns.get(String(node.id)) ?? 0
    if (!byColumn.has(column)) {
      byColumn.set(column, [])
    }
    byColumn.get(column)?.push(node)
  })

  byColumn.forEach((list) => {
    list.sort((a, b) => a.label.localeCompare(b.label))
  })

  const rows = new Map<string, number>()
  byColumn.forEach((list) => {
    list.forEach((node, index) => {
      rows.set(String(node.id), index)
    })
  })

  const maxColumn = Math.max(0, ...Array.from(byColumn.keys()))
  const maxRows = Math.max(0, ...Array.from(byColumn.values()).map((list) => list.length))

  return { rows, maxColumn, maxRows }
}

const computeBounds = (nodes: FormattedWorkflowNode[], spacing: WorkflowLayoutOptions) => {
  if (nodes.length === 0) {
    return { width: 0, height: 0, columnCount: 0, rowCount: 0 }
  }

  const columns = nodes.map((node) => node.column)
  const rows = nodes.map((node) => node.row)

  const minColumn = Math.min(...columns)
  const maxColumn = Math.max(...columns)
  const minRow = Math.min(...rows)
  const maxRow = Math.max(...rows)

  const columnCount = maxColumn - minColumn + 1
  const rowCount = maxRow - minRow + 1

  const width = Math.max(spacing.nodeWidth, (columnCount - 1) * spacing.columnGap + spacing.nodeWidth)
  const height = Math.max(spacing.nodeHeight, (rowCount - 1) * spacing.rowGap + spacing.nodeHeight)

  return { width, height, columnCount, rowCount }
}

const buildViewportPreferences = (columnCount: number, maxRows: number) => {
  const zoomFromColumns = columnCount <= 3 ? 0.95 : Math.max(0.4, 1 - (columnCount - 3) * 0.1)
  const zoomFromRows = maxRows <= 4 ? 0.95 : Math.max(0.45, 1 - (maxRows - 4) * 0.05)
  const preferredZoom = Number(Math.min(zoomFromColumns, zoomFromRows).toFixed(2))
  const offsetX = columnCount > 3 ? Math.min(180, (columnCount - 3) * 40) : 0
  return { preferredZoom, offsetX, offsetY: 0 }
}

/**
 * 检查节点是否包含 AI 返回的位置信息
 */
const hasAIPositions = (nodes: WorkflowNodeDefinition[]): boolean => {
  return nodes.length > 0 && nodes.every((node) => node.positionX !== undefined && node.positionY !== undefined)
}

export const formatWorkflowGraph = (graph: WorkflowGraph, options?: Partial<WorkflowLayoutOptions>): WorkflowLayoutResult => {
  const spacing = { ...defaultOptions, ...options }

  if (!graph.nodes.length) {
    return {
      nodes: [],
      edges: [],
      bounds: { width: 0, height: 0 },
      spacing,
      viewport: { preferredZoom: 1, offsetX: 0, offsetY: 0 },
      diagnostics: { columnCount: 0, maxRows: 0 }
    }
  }

  // 如果 AI 返回了位置信息，直接使用
  if (hasAIPositions(graph.nodes)) {
    const formattedNodes: FormattedWorkflowNode[] = graph.nodes.map((node, index) => ({
      ...node,
      column: index,
      row: 0,
      position: { x: node.positionX, y: node.positionY }
    }))

    const minX = Math.min(...formattedNodes.map((n) => n.position.x))
    const maxX = Math.max(...formattedNodes.map((n) => n.position.x))
    const minY = Math.min(...formattedNodes.map((n) => n.position.y))
    const maxY = Math.max(...formattedNodes.map((n) => n.position.y))

    const width = maxX - minX + spacing.nodeWidth
    const height = maxY - minY + spacing.nodeHeight
    const columnCount = Math.ceil(width / spacing.columnGap) || 1
    const rowCount = Math.ceil(height / spacing.rowGap) || 1

    return {
      nodes: formattedNodes,
      edges: graph.edges,
      bounds: { width, height },
      spacing,
      viewport: buildViewportPreferences(columnCount, rowCount),
      diagnostics: { columnCount, maxRows: rowCount }
    }
  }

  // 否则自动计算布局
  const columns = computeColumns(graph)
  const { rows, maxColumn, maxRows } = gatherRowAssignments(graph.nodes, columns)

  const columnValues = Array.from(columns.values())
  const minColumn = Math.min(...columnValues)
  const maxAssignedColumn = Math.max(maxColumn, ...columnValues)
  const centerColumn = (minColumn + maxAssignedColumn) / 2

  const rowValues = Array.from(rows.values())
  const minRow = Math.min(...rowValues, 0)
  const maxAssignedRow = Math.max(...rowValues, maxRows)
  const centerRow = (minRow + maxAssignedRow) / 2

  const formattedNodes: FormattedWorkflowNode[] = graph.nodes.map((node) => {
    const columnIndex = columns.get(String(node.id)) ?? 0
    const rowIndex = rows.get(String(node.id)) ?? 0
    const position = {
      x: (columnIndex - centerColumn) * spacing.columnGap,
      y: (rowIndex - centerRow) * spacing.rowGap
    }

    return { ...node, column: columnIndex, row: rowIndex, position }
  })

  const bounds = computeBounds(formattedNodes, spacing)
  const viewport = buildViewportPreferences(bounds.columnCount, bounds.rowCount)

  return {
    nodes: formattedNodes,
    edges: graph.edges,
    bounds: { width: bounds.width, height: bounds.height },
    spacing,
    viewport,
    diagnostics: { columnCount: bounds.columnCount, maxRows: bounds.rowCount }
  }
}
