import { useEffect, useId, useRef, useState, type CSSProperties } from 'react'
import {
  Activity,
  ArrowDownCircle,
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  Clock,
  Copy,
  Cpu,
  Database,
  Download,
  FileCode,
  Filter,
  Gauge,
  GitCommit,
  History,
  Layers,
  MoreHorizontal,
  Play,
  RefreshCw,
  Search,
  Settings,
  Table as TableIcon,
  Terminal as TerminalIcon,
  User,
  X,
  Zap
} from 'lucide-react'
import { Button, Card, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui'
import { StatusBadge } from './StackUi'
import type { PipelineRun, StackEdge, TaskNode, TaskRunHistory, WorkflowDefinition } from './types'

const mockNodes: TaskNode[] = [
  { id: 'n1', x: 60, y: 60, name: 'ODS_Orders_Sync', type: 'source', status: 'success', duration: '45s', startTime: '10:00:01', owner: 'sys_etl', description: 'Sync orders from MySQL shard_01.' },
  { id: 'n2', x: 60, y: 160, name: 'ODS_Users_Sync', type: 'source', status: 'success', duration: '32s', startTime: '10:00:01', owner: 'sys_etl', description: 'Sync user profiles from CRM.' },
  { id: 'n3', x: 320, y: 110, name: 'DWD_Fact_Order', type: 'transform', status: 'success', duration: '2m 15s', startTime: '10:00:48', owner: 'data_eng_01', description: 'Cleanse and join orders with user dimension.' },
  { id: 'n4', x: 580, y: 110, name: 'DWS_User_Behavior', type: 'join', status: 'running', duration: 'Running', startTime: '10:03:05', owner: 'analyst_02', description: 'Aggregate daily user behaviors.' },
  { id: 'n5', x: 840, y: 110, name: 'ADS_Sales_Metrics', type: 'sink', status: 'waiting', duration: '-', startTime: '-', owner: 'bi_team', description: 'Push final metrics to ClickHouse.' }
]

const mockEdges: StackEdge[] = [
  { id: 'e1', source: 'n1', target: 'n3', animated: false },
  { id: 'e2', source: 'n2', target: 'n3', animated: false },
  { id: 'e3', source: 'n3', target: 'n4', animated: true },
  { id: 'e4', source: 'n4', target: 'n5', animated: false }
]

const mockRuns: PipelineRun[] = [
  { id: 'run_8921', status: 'running', startTime: 'Today, 10:00 AM', duration: '3m 12s...', trigger: 'Schedule' },
  { id: 'run_8920', status: 'success', startTime: 'Yesterday, 10:00 AM', duration: '14m 20s', trigger: 'Schedule' },
  { id: 'run_8919', status: 'failed', startTime: 'Yesterday, 09:15 AM', duration: '2m 10s', trigger: 'Manual' }
]

const mockTaskHistory: TaskRunHistory[] = [
  { runId: '9821', pipelineRunId: 'run_8921', status: 'running', startTime: 'Today, 10:00 AM', duration: '3m 12s...', recordsProcessed: '4.5M', memoryUsage: '6.2GB' },
  { runId: '9815', pipelineRunId: 'run_8920', status: 'success', startTime: 'Yesterday, 10:00 AM', duration: '14m 20s', recordsProcessed: '8.1M', memoryUsage: '5.1GB' },
  { runId: '9802', pipelineRunId: 'run_8919', status: 'failed', startTime: 'Yesterday, 09:15 AM', duration: '2m 10s', recordsProcessed: '1.2M', memoryUsage: '4.9GB' }
]

const NodeIcon = ({ type, className, size = 14 }: { type: TaskNode['type']; className?: string; size?: number }) => {
  switch (type) {
    case 'source':
      return <Database size={size} className={className} />
    case 'transform':
      return <Zap size={size} className={className} />
    case 'join':
      return <GitCommit size={size} className={className} />
    case 'sink':
      return <TableIcon size={size} className={className} />
    default:
      return null
  }
}

const generateMockLogs = (run: TaskRunHistory, nodeType: string) => {
  const logs = [
    { time: '10:03:01', level: 'INFO', msg: `Initializing task runner for node ${nodeType}...` },
    { time: '10:03:02', level: 'INFO', msg: 'Environment: PROD-US-EAST-1 (v2.4.5)' },
    { time: '10:03:03', level: 'INFO', msg: 'Allocating resources... [OK]' },
    { time: '10:03:05', level: 'DEBUG', msg: 'Connection pool created: 10/50 connections active' }
  ]

  if (run.status === 'success') {
    logs.push(
      { time: '10:03:08', level: 'INFO', msg: 'Processing partition p_20231026' },
      { time: '10:03:15', level: 'WARN', msg: 'High latency detected in shard_04 (142ms) - auto-scaling triggered' },
      { time: '10:03:45', level: 'INFO', msg: 'Batch 1/50 committed successfully' },
      { time: '10:04:20', level: 'INFO', msg: 'Batch 25/50 committed successfully' },
      { time: '10:05:10', level: 'INFO', msg: 'Batch 50/50 committed successfully' },
      { time: '10:06:00', level: 'INFO', msg: 'Finalizing transaction...' },
      { time: '10:06:02', level: 'SUCCESS', msg: `Task completed in ${run.duration}. Records: ${run.recordsProcessed}` }
    )
  } else if (run.status === 'failed') {
    logs.push(
      { time: '10:03:08', level: 'INFO', msg: 'Processing partition p_20231026' },
      { time: '10:03:12', level: 'ERROR', msg: 'Connection timeout while writing to sink' },
      { time: '10:03:13', level: 'INFO', msg: 'Retrying (attempt 1/3)...' },
      { time: '10:03:25', level: 'ERROR', msg: 'Connection refused: 192.168.1.55:9092' },
      { time: '10:03:26', level: 'FATAL', msg: 'Task execution terminated with exit code 1' }
    )
  } else {
    logs.push(
      { time: '10:03:08', level: 'INFO', msg: 'Processing partition p_20231026' },
      { time: '10:03:12', level: 'DEBUG', msg: 'Stream offset: 482910' },
      { time: 'NOW', level: 'INFO', msg: 'Processing batch 452/1000...' }
    )
  }
  return logs
}

type StackTaskManagerProps = {
  workflow: WorkflowDefinition
  onBack: () => void
}

export function StackTaskManager({ workflow, onBack }: StackTaskManagerProps) {
  const svgId = useId()
  const svgIdSafe = `dp-${svgId.replace(/:/g, '')}`
  const edgeGradientId = `${svgIdSafe}-edge-gradient`
  const edgeGradientActiveId = `${svgIdSafe}-edge-gradient-active`
  const arrowheadId = `${svgIdSafe}-arrowhead`
  const arrowheadActiveId = `${svgIdSafe}-arrowhead-active`

  const [selectedNode, setSelectedNode] = useState<TaskNode | null>(null)
  const [activeTab, setActiveTab] = useState<'logs' | 'metrics' | 'code' | 'config'>('logs')
  const [selectedTaskRun, setSelectedTaskRun] = useState<TaskRunHistory | null>(null)
  const [isHistoryOpen, setIsHistoryOpen] = useState(false)
  const mainPaneRef = useRef<HTMLDivElement>(null)
  const [bottomPanelHeight, setBottomPanelHeight] = useState(224)
  const [isBottomPanelResizing, setIsBottomPanelResizing] = useState(false)

  const handleNodeSelect = (node: TaskNode) => {
    const isTogglingOff = selectedNode?.id === node.id
    setSelectedNode(isTogglingOff ? null : node)
    if (isTogglingOff) {
      return
    }
    const latestRun = mockTaskHistory[0]
    setSelectedTaskRun(latestRun)
    setActiveTab('logs')
    setIsHistoryOpen(false)
  }

  const handleRunChange = (run: TaskRunHistory) => {
    setSelectedTaskRun(run)
    setIsHistoryOpen(false)
  }

  const getLogs = () => {
    if (selectedTaskRun && selectedNode) {
      return generateMockLogs(selectedTaskRun, selectedNode.type)
    }
    return []
  }

  const NODE_WIDTH = 170
  const _NODE_HEIGHT = 60
  const PORT_INSET_Y = 30

  const getEdgePath = (sourceId: string, targetId: string) => {
    const sourceNode = mockNodes.find((node) => node.id === sourceId)
    const targetNode = mockNodes.find((node) => node.id === targetId)

    if (!sourceNode || !targetNode) return ''

    const startX = sourceNode.x + NODE_WIDTH + 6
    const startY = sourceNode.y + PORT_INSET_Y
    const endX = targetNode.x - 6
    const endY = targetNode.y + PORT_INSET_Y

    const dist = Math.abs(endX - startX)
    const controlX1 = startX + dist * 0.4
    const controlY1 = startY
    const controlX2 = endX - dist * 0.4
    const controlY2 = endY

    return `M ${startX} ${startY} C ${controlX1} ${controlY1}, ${controlX2} ${controlY2}, ${endX} ${endY}`
  }

  const BOTTOM_PANEL_MIN_HEIGHT = 180
  const BOTTOM_PANEL_MAX_HEIGHT_RATIO = 0.7

  const handleBottomPanelResizeStart = (e: React.MouseEvent) => {
    e.preventDefault()
    setIsBottomPanelResizing(true)
    document.body.style.userSelect = 'none'
    document.body.style.cursor = 'row-resize'
  }

  useEffect(() => {
    if (!isBottomPanelResizing) {
      return
    }

    const handleMouseMove = (e: MouseEvent) => {
      const pane = mainPaneRef.current
      if (!pane) return

      const rect = pane.getBoundingClientRect()
      const maxHeight = rect.height * BOTTOM_PANEL_MAX_HEIGHT_RATIO
      const nextHeight = rect.bottom - e.clientY
      const clamped = Math.max(BOTTOM_PANEL_MIN_HEIGHT, Math.min(maxHeight, nextHeight))
      setBottomPanelHeight(clamped)
    }

    const handleMouseUp = () => {
      setIsBottomPanelResizing(false)
      document.body.style.userSelect = ''
      document.body.style.cursor = ''
    }

    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)

    return () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
  }, [isBottomPanelResizing])

  return (
    <div className="flex-1 flex flex-col h-full bg-slate-50/40 dark:bg-slate-900 animate-in slide-in-from-right-4 duration-300 relative overflow-hidden">
      <div className="h-14 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between px-6 flex-shrink-0 z-20 shadow-sm relative">
        <div className="flex items-center">
          <Button
            onClick={onBack}
            variant="ghost"
            size="iconSm"
            className="mr-3 p-1.5 text-slate-400 hover:text-slate-900 hover:bg-slate-100 dark:hover:text-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors group"
          >
            <ArrowLeft size={18} className="group-hover:-translate-x-1 transition-transform" />
          </Button>
          <div>
            <h2 className="text-body-sm font-semibold text-slate-900 dark:text-slate-100 flex items-center">
              {workflow.name}
              <span
                className={`ml-3 px-2 py-0.5 rounded text-micro font-bold border uppercase tracking-wide ${
                  workflow.status === 'running'
                    ? 'bg-blue-50 text-blue-700 border-blue-100 dark:bg-blue-500/10 dark:text-blue-200 dark:border-blue-500/20'
                    : workflow.status === 'healthy'
                      ? 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20'
                      : 'bg-slate-100 text-slate-600 border-slate-200 dark:bg-slate-800/60 dark:text-slate-200 dark:border-slate-700'
                }`}
              >
                {workflow.status}
              </span>
            </h2>
            <div className="text-micro text-slate-400 dark:text-slate-500 font-mono mt-0.5">
              ID: {workflow.id} • Schedule: {workflow.schedule}
            </div>
          </div>
        </div>

        <div className="flex items-center space-x-3">
          <div className="flex items-center text-caption text-slate-500 dark:text-slate-400 mr-4">
            <Clock size={14} className="mr-1.5 text-slate-400 dark:text-slate-500" />
            Run: <span className="font-mono text-slate-900 dark:text-slate-100 ml-1 font-medium">{workflow.lastRun}</span>
          </div>
          <div className="h-4 w-px bg-slate-200 dark:bg-slate-800 mr-1"></div>
          <Button variant="primary" size="small" className="text-caption font-bold shadow-sm">
            <Play size={12} /> Run Now
          </Button>
          <Button
            variant="ghost"
            size="iconSm"
            className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 dark:hover:text-slate-200 dark:hover:bg-slate-800 rounded"
          >
            <MoreHorizontal size={16} />
          </Button>
        </div>
      </div>

      {isBottomPanelResizing && <div className="fixed inset-0 z-[9999] cursor-row-resize" />}

      <div className="flex-1 flex overflow-hidden">
        <div ref={mainPaneRef} className="flex-1 relative overflow-hidden flex flex-col">
          <div className="absolute top-4 left-4 right-4 flex justify-between items-start z-10 pointer-events-none">
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-1 flex space-x-1 pointer-events-auto shadow-sm">
              <Button
                variant="ghost"
                size="iconSm"
                className="p-1.5 text-slate-700 dark:text-slate-200 bg-slate-100 dark:bg-slate-800 rounded hover:bg-slate-200 dark:hover:bg-slate-700"
              >
                <Layers size={14} />
              </Button>
              <Button
                variant="ghost"
                size="iconSm"
                className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 dark:hover:text-slate-200 dark:hover:bg-slate-800 rounded"
              >
                <Filter size={14} />
              </Button>
            </div>
          </div>

          <div
            className="flex-1 relative overflow-auto custom-scrollbar cursor-grab active:cursor-grabbing"
            onClick={(event) => {
              if (event.target === event.currentTarget) setSelectedNode(null)
            }}
          >
              <div
                className="absolute inset-0 min-w-[75rem] min-h-[37.5rem] pointer-events-none opacity-40 bg-[radial-gradient(#e2e8f0_1px,transparent_1px)] dark:bg-[radial-gradient(#1e293b_1px,transparent_1px)] [background-size:20px_20px]"
              />

              <div className="absolute inset-0 min-w-[75rem] min-h-[37.5rem] pointer-events-none">
                <svg className="absolute inset-0 w-full h-full overflow-visible pointer-events-none">
                  <defs>
                    <linearGradient id={edgeGradientId} x1="0%" y1="0%" x2="100%" y2="0%">
                      <stop offset="0%" stopColor="#94a3b8" />
                      <stop offset="100%" stopColor="#cbd5e1" />
                    </linearGradient>
                    <linearGradient id={edgeGradientActiveId} x1="0%" y1="0%" x2="100%" y2="0%">
                      <stop offset="0%" stopColor="#3b82f6" />
                      <stop offset="100%" stopColor="#22d3ee" />
                    </linearGradient>

                    <marker id={arrowheadId} viewBox="0 0 10 10" refX="10" refY="5" markerWidth="5" markerHeight="5" orient="auto">
                      <path d="M0,0 L10,5 L0,10 Z" fill="currentColor" className="text-slate-400 dark:text-slate-500" />
                    </marker>
                    <marker id={arrowheadActiveId} viewBox="0 0 10 10" refX="10" refY="5" markerWidth="5" markerHeight="5" orient="auto">
                      <path d="M0,0 L10,5 L0,10 Z" fill="currentColor" className="text-blue-500 dark:text-cyan-300" />
                    </marker>
                  </defs>

                  {mockEdges.map((edge) => {
                    const path = getEdgePath(edge.source, edge.target)
                    return (
                      <g key={edge.id}>
                        <path
                          d={path}
                          fill="none"
                          stroke="transparent"
                          strokeWidth="20"
                          className="cursor-pointer pointer-events-auto hover:stroke-black/5 dark:hover:stroke-white/5 transition-colors"
                        />
                        <path
                          d={path}
                          fill="none"
                          stroke={edge.animated ? `url(#${edgeGradientActiveId})` : 'currentColor'}
                          strokeWidth={edge.animated ? '2' : '1.75'}
                          markerEnd={edge.animated ? `url(#${arrowheadActiveId})` : `url(#${arrowheadId})`}
                          strokeDasharray={edge.animated ? 'none' : 'none'}
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          className={edge.animated ? '' : 'text-slate-400 dark:text-slate-500'}
                          style={{ filter: edge.animated ? 'drop-shadow(0 0 2px rgba(59,130,246,0.6))' : 'none' }}
                        />

                        {edge.animated && (
                          <circle r="3" fill="#3b82f6">
                            <animateMotion dur="1.5s" repeatCount="indefinite" path={path} />
                          </circle>
                        )}
                      </g>
                    )
                  })}
                </svg>

                <div className="absolute inset-0 pointer-events-auto">
                  {mockNodes.map((node) => (
                    <div
                      key={node.id}
                      style={{ transform: `translate(${node.x}px, ${node.y}px)` }}
                      className="absolute"
                      onClick={(event) => {
                        event.stopPropagation()
                        handleNodeSelect(node)
                      }}
                    >
                      <GraphNode node={node} selected={selectedNode?.id === node.id} />
                    </div>
                  ))}
                </div>
              </div>
          </div>

          <div
            style={{ '--bottom-panel-height': `${bottomPanelHeight}px` } as CSSProperties}
            className={`h-[var(--bottom-panel-height)] bg-white dark:bg-slate-900 border-t border-slate-200 dark:border-slate-800 flex flex-col shrink-0 z-10 shadow-[0_-4px_12px_-6px_rgba(0,0,0,0.05)] relative ${
              isBottomPanelResizing ? '' : 'transition-[height] duration-150 ease-out'
            }`}
          >
            <div onMouseDown={handleBottomPanelResizeStart} className="absolute top-0 left-0 right-0 h-2 cursor-row-resize group">
              <div className="h-px w-full bg-slate-200 dark:bg-slate-800 group-hover:bg-brand-500 transition-colors" />
            </div>

            <div className="px-5 py-2.5 bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center">
              <div className="flex items-center space-x-3">
                <div className="p-1 rounded bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 text-slate-400 dark:text-slate-500">
                  <Activity size={16} />
                </div>
                <div className="flex flex-col">
                  <h3 className="text-legal font-bold text-slate-900 dark:text-slate-100 uppercase tracking-wide">Pipeline Execution History</h3>
                  <span className="text-micro text-slate-400 dark:text-slate-500 font-medium">Global workflow run logs</span>
                </div>
              </div>
              <div className="flex items-center space-x-2">
                <div className="px-2 py-1 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded text-micro text-slate-500 dark:text-slate-400 font-mono">
                  Last 30 runs
                </div>
                <Button
                  variant="ghost"
                  size="iconSm"
                  className="p-1 text-slate-400 hover:text-slate-600 hover:bg-slate-100 dark:hover:text-slate-200 dark:hover:bg-slate-800 rounded"
                >
                  <RefreshCw size={14} />
                </Button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto custom-scrollbar">
              <Table className="border-0 shadow-none rounded-none" layout="auto" minWidth="none">
                <TableHeader className="sticky top-0 z-10 shadow-sm">
                  <TableRow>
                    <TableHead className="px-5 py-2.5">Workflow Run ID</TableHead>
                    <TableHead className="px-5 py-2.5">Global Status</TableHead>
                    <TableHead className="px-5 py-2.5">Trigger</TableHead>
                    <TableHead className="px-5 py-2.5 text-right">Total Duration</TableHead>
                  </TableRow>
                </TableHeader>

                <TableBody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {mockRuns.map((run) => (
                    <TableRow key={run.id} className="hover:bg-slate-50 dark:hover:bg-slate-800/50 group cursor-pointer transition-colors">
                      <TableCell className="px-5 py-2.5">
                        <span className="font-mono text-caption text-brand-600 dark:text-brand-300 font-medium group-hover:underline">#{run.id}</span>
                        <div className="text-micro text-slate-400 dark:text-slate-500">{run.startTime}</div>
                      </TableCell>
                      <TableCell className="px-5 py-2.5">
                        <StatusBadge status={run.status} />
                      </TableCell>
                      <TableCell className="px-5 py-2.5 text-caption text-slate-600 dark:text-slate-300">
                        <div className="flex items-center">
                          {run.trigger === 'Schedule' ? (
                            <Clock size={12} className="mr-1.5 text-slate-400 dark:text-slate-500" />
                          ) : (
                            <User size={12} className="mr-1.5 text-slate-400 dark:text-slate-500" />
                          )}
                          {run.trigger}
                        </div>
                      </TableCell>
                      <TableCell className="px-5 py-2.5 text-caption text-slate-600 dark:text-slate-300 font-mono text-right">
                        {run.duration}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </div>
        </div>

        {selectedNode && selectedTaskRun && (
          <div className="w-[31.25rem] bg-white dark:bg-slate-900 border-l border-slate-200 dark:border-slate-800 shadow-2xl z-40 animate-in slide-in-from-right duration-300 flex flex-col">
            <div className="h-14 px-5 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between bg-white dark:bg-slate-900 flex-shrink-0 z-20 relative">
              <div className="flex items-center space-x-3">
                <div className="p-1.5 bg-brand-50 dark:bg-brand-500/10 rounded-lg text-brand-600 dark:text-brand-300">
                  <NodeIcon type={selectedNode.type} />
                </div>
                <div>
                  <span className="text-body-sm font-semibold text-slate-900 dark:text-slate-100 block leading-tight">{selectedNode.name}</span>
                  <span className="text-micro text-slate-400 dark:text-slate-500 font-mono">ID: {selectedNode.id}</span>
                </div>
              </div>
              <div className="flex items-center space-x-1">
                <Button
                  onClick={() => setSelectedNode(null)}
                  variant="ghost"
                  size="iconSm"
                  className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 dark:hover:text-slate-200 dark:hover:bg-slate-800 rounded-lg transition-colors"
                >
                  <X size={18} />
                </Button>
              </div>
            </div>

            <div className="px-5 py-3 bg-slate-50/80 dark:bg-slate-800/40 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between relative z-10 backdrop-blur-sm">
              <div className="flex items-center space-x-3">
                <StatusBadge status={selectedTaskRun.status} />
                <div className="flex flex-col">
                  <span className="text-caption font-bold text-slate-900 dark:text-slate-100 font-mono flex items-center">
                    TaskRun #{selectedTaskRun.runId}
                    {selectedTaskRun.runId === mockTaskHistory[0].runId && (
                      <span className="ml-2 px-1.5 py-0.5 bg-brand-100 dark:bg-brand-500/10 text-brand-700 dark:text-brand-200 text-nano rounded font-sans tracking-wide">
                        LATEST
                      </span>
                    )}
                  </span>
                  <span className="text-micro text-slate-400 dark:text-slate-500">{selectedTaskRun.startTime}</span>
                </div>
              </div>

              <div className="relative">
                <Button
                  onClick={() => setIsHistoryOpen(!isHistoryOpen)}
                  variant="outline"
                  size="small"
                  className={`flex items-center px-3 py-1.5 rounded-lg text-caption font-medium border transition-all shadow-sm hover:bg-white dark:hover:bg-slate-800/60 ${
                    isHistoryOpen
                      ? 'bg-white dark:bg-slate-900 border-brand-300 dark:border-brand-500/40 text-brand-600 dark:text-brand-200 ring-2 ring-brand-50 dark:ring-brand-500/20'
                      : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300 hover:border-slate-300 dark:hover:border-slate-600 hover:text-slate-900 dark:hover:text-slate-100'
                  }`}
                >
                  <History size={14} className="mr-2 text-slate-400 dark:text-slate-500" />
                  History
                  <ChevronDown
                    size={12}
                    className={`ml-2 text-slate-400 dark:text-slate-500 transition-transform duration-200 ${isHistoryOpen ? 'rotate-180' : ''}`}
                  />
                </Button>

                {isHistoryOpen && (
                  <div className="absolute right-0 top-full mt-2 w-72 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-200 z-50">
                    <div className="px-3 py-2 bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-800 text-micro font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider">
                      Select Past Execution
                    </div>
                    <div className="max-h-[300px] overflow-y-auto custom-scrollbar">
                      {mockTaskHistory.map((run) => (
                        <div
                          key={run.runId}
                          onClick={() => handleRunChange(run)}
                          className={`px-4 py-3 border-b border-slate-100 dark:border-slate-800 cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors flex items-center justify-between group ${
                            selectedTaskRun.runId === run.runId ? 'bg-brand-50/50 dark:bg-brand-500/10' : ''
                          }`}
                        >
                          <div>
                            <div className="flex items-center space-x-2 mb-1">
                              <span
                                className={`font-mono text-caption font-medium ${
                                  selectedTaskRun.runId === run.runId ? 'text-brand-700 dark:text-brand-200' : 'text-slate-700 dark:text-slate-200'
                                }`}
                              >
                                #{run.runId}
                              </span>
                              {run.status === 'failed' && <span className="w-1.5 h-1.5 rounded-full bg-red-500"></span>}
                            </div>
                            <div className="text-micro text-slate-400 dark:text-slate-500">{run.startTime}</div>
                          </div>
                          <div className="text-right">
                            <div className="text-micro font-mono text-slate-500 dark:text-slate-400 mb-1">{run.duration}</div>
                            {selectedTaskRun.runId === run.runId && <CheckCircle2 size={12} className="text-brand-600 dark:text-brand-300 ml-auto" />}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>

            <div className="px-5 border-b border-slate-200 dark:border-slate-800 flex space-x-1 bg-white dark:bg-slate-900">
              {[
                { id: 'logs', icon: TerminalIcon, label: 'Logs' },
                { id: 'metrics', icon: Gauge, label: 'Metrics' },
                { id: 'code', icon: FileCode, label: 'Code' },
                { id: 'config', icon: Settings, label: 'Config' }
              ].map((tab) => (
                <Button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id as typeof activeTab)}
                  variant="ghost"
                  size="small"
                  className={`flex-1 px-0 py-3 text-caption font-bold border-b-2 transition-colors rounded-none flex items-center justify-center ${
                    activeTab === tab.id
                      ? 'border-brand-600 dark:border-brand-400 text-brand-600 dark:text-brand-300 bg-brand-50/30 dark:bg-brand-500/10 hover:bg-brand-50/30 dark:hover:bg-brand-500/10'
                      : 'border-transparent text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-100 hover:bg-slate-50 dark:hover:bg-slate-800/50'
                  }`}
                >
                  <tab.icon size={14} className="mr-1.5" />
                  {tab.label}
                </Button>
              ))}
            </div>

            <div className="flex-1 overflow-hidden flex flex-col bg-white dark:bg-slate-900 relative">
              {activeTab === 'logs' && (
                <div className="flex-1 p-5 bg-transparent flex flex-col overflow-hidden">
                  <div className="flex-1 flex flex-col bg-white dark:bg-slate-950 rounded-xl overflow-hidden shadow-sm dark:shadow-none border border-slate-200 dark:border-slate-800 font-mono text-legal relative">
                    <div className="flex items-center justify-between px-4 py-2.5 bg-slate-50 dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 select-none flex-shrink-0">
                      <div className="flex items-center space-x-4">
                        <div className="flex space-x-1.5">
                          <div className="w-2.5 h-2.5 rounded-full bg-[#ff5f56]"></div>
                          <div className="w-2.5 h-2.5 rounded-full bg-[#ffbd2e]"></div>
                          <div className="w-2.5 h-2.5 rounded-full bg-[#27c93f]"></div>
                        </div>
                        <div className="h-3 w-px bg-slate-200 dark:bg-slate-800"></div>
                        <div className="flex items-center text-slate-500 dark:text-slate-300">
                          <TerminalIcon size={10} className="mr-1.5" />
                          <span className="opacity-80">task-runner-{selectedTaskRun.runId}.log</span>
                        </div>
                      </div>
                      <div className="flex items-center space-x-3 text-slate-500 dark:text-slate-400">
                        <Button
                          variant="ghost"
                          size="iconSm"
                          className="size-6! p-0 text-slate-500 hover:text-slate-900 hover:bg-slate-200/60 dark:text-slate-400 dark:hover:text-slate-100 dark:hover:bg-slate-800/60 transition-colors"
                          title="Search"
                        >
                          <Search size={12} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="iconSm"
                          className="size-6! p-0 text-slate-500 hover:text-slate-900 hover:bg-slate-200/60 dark:text-slate-400 dark:hover:text-slate-100 dark:hover:bg-slate-800/60 transition-colors"
                          title="Copy All"
                        >
                          <Copy size={12} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="iconSm"
                          className="size-6! p-0 text-slate-500 hover:text-slate-900 hover:bg-slate-200/60 dark:text-slate-400 dark:hover:text-slate-100 dark:hover:bg-slate-800/60 transition-colors"
                          title="Download"
                        >
                          <Download size={12} />
                        </Button>
                        <div className="h-3 w-px bg-slate-200 dark:bg-slate-800"></div>
                        <Button
                          variant="ghost"
                          size="iconSm"
                          className="size-6! p-0 text-slate-500 hover:text-slate-900 hover:bg-slate-200/60 dark:text-slate-400 dark:hover:text-slate-100 dark:hover:bg-slate-800/60 transition-colors"
                          title="Follow Tail"
                        >
                          <ArrowDownCircle size={12} />
                        </Button>
                      </div>
                    </div>

                    <div className="flex-1 overflow-y-auto custom-scrollbar p-4 space-y-0.5">
                      {getLogs().map((log, index) => (
                        <div
                          key={`${log.time}-${index}`}
                          className="flex hover:bg-slate-50 dark:hover:bg-slate-900/40 px-2 -mx-2 rounded transition-colors group"
                        >
                          <span className="text-slate-400 dark:text-slate-500 mr-3 select-none w-6 text-right opacity-40 group-hover:opacity-100 font-mono text-micro">
                            {index + 1}
                          </span>
                          <span className="text-slate-500 dark:text-sky-300/70 mr-3 font-mono text-micro select-none">{log.time}</span>
                          <div className="flex-1 flex items-start break-all">
                            <span
                              className={`mr-2 px-1 rounded-[2px] text-nano font-bold select-none h-4 flex items-center ${
                                log.level === 'INFO'
                                  ? 'bg-sky-50 text-sky-700 dark:bg-sky-400/10 dark:text-sky-200'
                                  : log.level === 'WARN'
                                    ? 'bg-amber-50 text-amber-700 dark:bg-amber-400/10 dark:text-amber-200'
                                    : log.level === 'ERROR' || log.level === 'FATAL'
                                      ? 'bg-rose-50 text-rose-700 dark:bg-rose-400/10 dark:text-rose-200'
                                      : log.level === 'SUCCESS'
                                        ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-400/10 dark:text-emerald-200'
                                        : 'bg-slate-100 text-slate-600 dark:bg-slate-400/10 dark:text-slate-200'
                              }`}
                            >
                              {log.level}
                            </span>
                            <span
                              className={`text-slate-700 dark:text-slate-200 ${
                                log.level === 'ERROR' || log.level === 'FATAL' ? 'text-rose-700 dark:text-rose-200' : ''
                              }`}
                            >
                              {log.msg}
                            </span>
                          </div>
                        </div>
                      ))}

                      {selectedTaskRun.status === 'running' && (
                        <div className="flex items-center text-slate-400 dark:text-slate-500 mt-2 px-2 pl-9 animate-pulse">
                          <span className="w-1.5 h-3 bg-slate-300 dark:bg-slate-600 mr-2"></span>
                          <span className="text-micro italic opacity-70">Streaming logs...</span>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'code' && (
                <div className="flex-1 p-0 relative flex flex-col">
                  <div className="px-4 py-2 bg-slate-50 dark:bg-slate-800/50 border-b border-slate-200 dark:border-slate-800 flex justify-between items-center text-caption text-slate-500 dark:text-slate-400">
                    <span className="font-mono">source_definition.sql</span>
                    <Button variant="outline" size="tiny" className="text-slate-500 dark:text-slate-300 shadow-sm hover:text-brand-600 dark:hover:text-brand-300">
                      Copy
                    </Button>
                  </div>
                  <div className="flex-1 bg-slate-50/30 dark:bg-slate-950/30 p-4 overflow-auto">
                    <pre className="text-caption font-mono text-slate-700 dark:text-slate-200 leading-relaxed bg-white dark:bg-slate-900 p-4 rounded-lg border border-slate-200 dark:border-slate-800 shadow-sm dark:shadow-none">{`-- Task Definition for ${selectedNode?.name ?? 'Node'}\n-- Optimized for ClickHouse\n\nSELECT \n    order_id,\n    user_id,\n    amount,\n    currency,\n    created_at\nFROM \n    source_db.orders \nWHERE \n    date = '\${today}'\n    AND status = 'paid'\n    AND amount > 0\nLIMIT 10000;`}</pre>
                  </div>
                </div>
              )}

              {activeTab === 'metrics' && (
                <div className="flex-1 p-6 overflow-y-auto">
                  <div className="space-y-6">
                    <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-sm dark:shadow-none">
                      <h4 className="text-caption font-bold text-slate-900 dark:text-slate-100 mb-4 flex items-center">
                        <Cpu size={14} className="mr-2 text-slate-400 dark:text-slate-500" />
                        Memory Usage
                      </h4>
                      <div className="h-32 bg-slate-50 dark:bg-slate-800/60 rounded-lg border border-slate-200 dark:border-slate-800 flex items-end justify-between px-3 pb-0 relative overflow-hidden">
                        {[40, 60, 45, 70, 80, 50, 60, 40, 30, 45, 60, 75, 50, 40, 60, 80, 90, 70, 50, 40].map((height, index) => (
                          <div
                            key={index}
                            className="w-2 h-[var(--bar-height)] bg-blue-400 rounded-t-sm opacity-80 hover:opacity-100 transition-opacity"
                            style={{ '--bar-height': `${height}%` } as CSSProperties}
                          />
                        ))}
                        <div className="absolute top-2 right-2 text-micro font-mono text-slate-400 dark:text-slate-500">Peak: {selectedTaskRun.memoryUsage}</div>
                      </div>
                    </div>

                    <div className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-4 shadow-sm dark:shadow-none">
                      <h4 className="text-caption font-bold text-slate-900 dark:text-slate-100 mb-4 flex items-center">
                        <Activity size={14} className="mr-2 text-slate-400 dark:text-slate-500" />
                        IO Throughput
                      </h4>
                      <div className="grid grid-cols-2 gap-4">
                        <div className="p-3 bg-slate-50 dark:bg-slate-800/60 rounded-lg text-center">
                          <div className="text-micro text-slate-400 dark:text-slate-500 uppercase font-bold mb-1">Read Records</div>
                          <div className="text-body font-mono font-semibold text-slate-800 dark:text-slate-100">{selectedTaskRun.recordsProcessed}</div>
                        </div>
                        <div className="p-3 bg-slate-50 dark:bg-slate-800/60 rounded-lg text-center">
                          <div className="text-micro text-slate-400 dark:text-slate-500 uppercase font-bold mb-1">Write Records</div>
                          <div className="text-body font-mono font-semibold text-slate-800 dark:text-slate-100">{selectedTaskRun.recordsProcessed}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'config' && (
                <div className="flex-1 p-5 overflow-y-auto">
                  <div className="space-y-4">
                    <div>
                      <label className="text-caption font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wide block mb-2">Node Type</label>
                      <div className="flex items-center px-3 py-2 bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-800 rounded-lg">
                        <NodeIcon type={selectedNode.type} />
                        <span className="ml-2 text-caption text-slate-700 dark:text-slate-200 font-mono capitalize">{selectedNode.type}</span>
                      </div>
                    </div>
                    <div>
                      <label className="text-caption font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wide block mb-2">Parameters</label>
                      <textarea
                        className="w-full h-32 px-3 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg text-caption text-slate-700 dark:text-slate-200 font-mono focus:ring-2 focus:ring-brand-500 focus:border-transparent outline-none resize-none"
                        defaultValue={`{\n  "mode": "incremental",\n  "batch_size": 1000,\n  "timeout": "300s"\n}`}
                      />
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

const GraphNode = ({ node, selected }: { node: TaskNode; selected: boolean }) => {
  const styles =
    {
      source: {
        borderColor: 'border-blue-200 dark:border-blue-800/40',
        hoverBorder: 'hover:border-blue-300 dark:hover:border-blue-600/60',
        bg: 'bg-blue-50 dark:bg-blue-900/20',
        hoverBg: 'bg-blue-50/50 dark:bg-blue-900/20',
        accent: 'bg-blue-500',
        icon: 'text-blue-600 dark:text-blue-400'
      },
      transform: {
        borderColor: 'border-amber-200 dark:border-amber-800/40',
        hoverBorder: 'hover:border-amber-300 dark:hover:border-amber-600/60',
        bg: 'bg-amber-50 dark:bg-amber-900/20',
        hoverBg: 'bg-amber-50/50 dark:bg-amber-900/20',
        accent: 'bg-amber-500',
        icon: 'text-amber-600 dark:text-amber-400'
      },
      join: {
        borderColor: 'border-purple-200 dark:border-purple-800/40',
        hoverBorder: 'hover:border-purple-300 dark:hover:border-purple-600/60',
        bg: 'bg-purple-50 dark:bg-purple-900/20',
        hoverBg: 'bg-purple-50/50 dark:bg-purple-900/20',
        accent: 'bg-purple-500',
        icon: 'text-purple-600 dark:text-purple-400'
      },
      sink: {
        borderColor: 'border-emerald-200 dark:border-emerald-800/40',
        hoverBorder: 'hover:border-emerald-300 dark:hover:border-emerald-600/60',
        bg: 'bg-emerald-50 dark:bg-emerald-900/20',
        hoverBg: 'bg-emerald-50/50 dark:bg-emerald-900/20',
        accent: 'bg-emerald-500',
        icon: 'text-emerald-600 dark:text-emerald-400'
      }
    }[node.type] ?? {
      borderColor: 'border-slate-200 dark:border-slate-800',
      hoverBorder: 'hover:border-slate-300 dark:hover:border-slate-600/60',
      bg: 'bg-slate-50 dark:bg-slate-800/50',
      hoverBg: 'bg-slate-50/60 dark:bg-slate-800/50',
      accent: 'bg-slate-400 dark:bg-slate-500',
      icon: 'text-slate-600 dark:text-slate-300'
    }

  return (
    <div className="group relative">
      {node.type !== 'source' && (
        <div className="absolute -left-[4px] top-[30px] -translate-y-1/2 flex items-center justify-center z-[-1]">
          <div className={`w-2 h-2 rounded-full border border-white dark:border-slate-900 ${styles.accent} shadow-sm`}></div>
        </div>
      )}
      {node.type !== 'sink' && (
        <div className="absolute -right-[4px] top-[30px] -translate-y-1/2 flex items-center justify-center z-[-1]">
          <div className={`w-2 h-2 rounded-full border border-white dark:border-slate-900 ${styles.accent} shadow-sm`}></div>
        </div>
      )}

      <Card
        variant="default"
        padding="none"
        className={`w-[10.625rem] h-[3.75rem] cursor-pointer relative overflow-hidden flex flex-col shadow-sm transition-all duration-200 ${styles.borderColor} ${styles.hoverBorder} ${
          selected ? 'ring-2 ring-brand-500/30 shadow-md z-20' : 'hover:shadow-md'
        }`}
      >
        <div className={`absolute inset-0 pointer-events-none transition-opacity duration-300 ${styles.hoverBg} ${selected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'}`} />

        <div className="flex-1 px-3 py-2 flex items-center space-x-3 relative z-10">
          <div className={`p-1.5 rounded-lg ${styles.bg} flex items-center justify-center flex-shrink-0`}>
            <NodeIcon type={node.type} className={styles.icon} size={16} />
          </div>

          <div className="flex flex-col min-w-0 justify-center">
            <div className="text-caption font-semibold text-slate-900 dark:text-slate-100 truncate leading-tight mb-0.5">{node.name}</div>
            <div className="flex items-center text-nano text-slate-400 dark:text-slate-500 font-mono leading-tight">
              <Clock size={9} className="mr-1 opacity-70" />
              {node.duration}
            </div>
          </div>
        </div>

        {node.status === 'running' && (
          <div className="absolute top-2 right-2">
            <span className="relative flex h-2 w-2">
              <span className={`animate-ping absolute inline-flex h-full w-full rounded-full ${styles.accent} opacity-75`}></span>
              <span className={`relative inline-flex rounded-full h-2 w-2 ${styles.accent}`}></span>
            </span>
          </div>
        )}
      </Card>
    </div>
  )
}
