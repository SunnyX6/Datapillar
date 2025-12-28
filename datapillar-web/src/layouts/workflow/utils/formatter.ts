import type { WorkflowEdgeDefinition, WorkflowGraph, WorkflowNodeDefinition, WorkflowNodeType } from '@/services/workflowStudioService'

const DEFAULT_NODE_WIDTH = 172
const DEFAULT_NODE_HEIGHT = 96
const DEFAULT_COLUMN_GAP = 300
const DEFAULT_ROW_GAP = 160
const TYPE_PRIORITY: Record<WorkflowNodeType, number> = {
  source: 0,
  transform: 1,
  quality: 2,
  sink: 3
}

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

const sortQueue = (queue: WorkflowNodeDefinition[]) => {
  return queue.sort((a, b) => {
    const priority = TYPE_PRIORITY[a.type] - TYPE_PRIORITY[b.type]
    if (priority !== 0) {
      return priority
    }
    return a.label.localeCompare(b.label)
  })
}

const computeColumns = (graph: WorkflowGraph) => {
  const adjacency = buildAdjacency(graph.edges)
  const indegree = new Map<string, number>()
  const nodeMap = new Map<string, WorkflowNodeDefinition>()

  graph.nodes.forEach((node) => {
    indegree.set(node.id, 0)
    nodeMap.set(node.id, node)
  })

  graph.edges.forEach(({ target }) => {
    indegree.set(target, (indegree.get(target) ?? 0) + 1)
  })

  const columns = new Map<string, number>()
  const queue = sortQueue(
    graph.nodes.filter((node) => (indegree.get(node.id) ?? 0) === 0)
  )

  let columnIndex = 0

  while (queue.length > 0) {
    const levelSize = queue.length
    for (let index = 0; index < levelSize; index += 1) {
      const node = queue.shift()
      if (!node) continue
      columns.set(node.id, columnIndex)

      const targets = adjacency.get(node.id)
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
    sortQueue(queue)
    columnIndex += 1
  }

  // Fallback for nodes not processed (e.g. cycles)
  graph.nodes.forEach((node) => {
    if (!columns.has(node.id)) {
      columns.set(node.id, columnIndex)
      columnIndex += 1
    }
  })

  return columns
}

const gatherRowAssignments = (nodes: WorkflowNodeDefinition[], columns: Map<string, number>) => {
  const byColumn = new Map<number, WorkflowNodeDefinition[]>()
  nodes.forEach((node) => {
    const column = columns.get(node.id) ?? 0
    if (!byColumn.has(column)) {
      byColumn.set(column, [])
    }
    byColumn.get(column)?.push(node)
  })

  byColumn.forEach((list) => {
    list.sort((a, b) => {
      const priority = TYPE_PRIORITY[a.type] - TYPE_PRIORITY[b.type]
      if (priority !== 0) {
        return priority
      }
      return a.label.localeCompare(b.label)
    })
  })

  const rows = new Map<string, number>()
  byColumn.forEach((list, _column) => {
    list.forEach((node, index) => {
      rows.set(node.id, index)
    })
  })

  const maxColumn = Math.max(...Array.from(byColumn.keys()))
  const maxRows = Math.max(0, ...Array.from(byColumn.values()).map((list) => list.length))

  return { rows, maxColumn, maxRows }
}

const computeBounds = (nodes: FormattedWorkflowNode[], spacing: WorkflowLayoutOptions) => {
  if (nodes.length === 0) {
    return {
      width: 0,
      height: 0,
      columnCount: 0,
      rowCount: 0
    }
  }

  const columns = nodes.map((node) => node.column)
  const rows = nodes.map((node) => node.row)

  const minColumn = Math.min(...columns)
  const maxColumn = Math.max(...columns)
  const minRow = Math.min(...rows)
  const maxRow = Math.max(...rows)

  const columnCount = maxColumn - minColumn + 1
  const rowCount = maxRow - minRow + 1

  const width = Math.max(
    spacing.nodeWidth,
    (columnCount - 1) * spacing.columnGap + spacing.nodeWidth
  )
  const height = Math.max(
    spacing.nodeHeight,
    (rowCount - 1) * spacing.rowGap + spacing.nodeHeight
  )

  return {
    width,
    height,
    columnCount,
    rowCount
  }
}

const buildViewportPreferences = (columnCount: number, maxRows: number) => {
  const zoomFromColumns = columnCount <= 3 ? 0.95 : Math.max(0.4, 1 - (columnCount - 3) * 0.1)
  const zoomFromRows = maxRows <= 4 ? 0.95 : Math.max(0.45, 1 - (maxRows - 4) * 0.05)
  const preferredZoom = Number(Math.min(zoomFromColumns, zoomFromRows).toFixed(2))
  const offsetX = columnCount > 3 ? Math.min(180, (columnCount - 3) * 40) : 0
  return {
    preferredZoom,
    offsetX,
    offsetY: 0
  }
}

export const formatWorkflowGraph = (
  graph: WorkflowGraph,
  options?: Partial<WorkflowLayoutOptions>
): WorkflowLayoutResult => {
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
    const columnIndex = columns.get(node.id) ?? 0
    const rowIndex = rows.get(node.id) ?? 0
    const position = {
      x: (columnIndex - centerColumn) * spacing.columnGap,
      y: (rowIndex - centerRow) * spacing.rowGap
    }

    return {
      ...node,
      column: columnIndex,
      row: rowIndex,
      position
    }
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
