import { useEffect, useRef, type JSX } from 'react'
import {
  Background,
  BackgroundVariant,
  Handle,
  MarkerType,
  MiniMap,
  Position,
  ReactFlow,
  type Edge,
  type FitViewOptions,
  type Node,
  type NodeProps,
  useEdgesState,
  useNodesInitialized,
  useNodesState,
  useReactFlow
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { Database, MoreHorizontal, Play, Share2, Shield, Sparkles } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { WorkflowLayoutResult } from '@/layouts/workflow/utils/formatter'
import type { WorkflowNodeType } from '@/services/workflowStudioService'

type StudioNodeData = {
  label: string
  type: WorkflowNodeType
  description: string
}

const CustomNode = ({ data }: NodeProps<Node<StudioNodeData>>) => {
  const palette: Record<WorkflowNodeType, { border: string; glow: string; accent: string; icon: JSX.Element }> = {
    source: {
      border: 'border-blue-500/30',
      glow: 'shadow-[0_0_30px_-5px_rgba(59,130,246,0.25)]',
      accent: 'bg-blue-500/15',
      icon: <Database size={14} className="text-blue-300" />
    },
    transform: {
      border: 'border-purple-500/30',
      glow: 'shadow-[0_0_30px_-5px_rgba(168,85,247,0.25)]',
      accent: 'bg-purple-500/15',
      icon: <Sparkles size={14} className="text-purple-200" />
    },
    quality: {
      border: 'border-emerald-500/40',
      glow: 'shadow-[0_0_30px_-5px_rgba(16,185,129,0.35)]',
      accent: 'bg-emerald-500/15',
      icon: <Shield size={14} className="text-emerald-200" />
    },
    sink: {
      border: 'border-amber-500/40',
      glow: 'shadow-[0_0_30px_-5px_rgba(245,158,11,0.35)]',
      accent: 'bg-amber-500/15',
      icon: <Share2 size={14} className="text-amber-200" />
    }
  }

  const styles = palette[data.type]

  return (
    <div
      className={cn(
        'w-44 rounded-xl border bg-white/95 dark:bg-slate-900/85 backdrop-blur-md transition-all duration-300 group relative overflow-hidden',
        styles.border,
      )}
    >
      <div className={cn('absolute top-0 left-0 right-0 h-px opacity-70', styles.accent)} />

      {data.type !== 'source' && (
        <Handle
          type="target"
          position={Position.Left}
          className="!w-2.5 !h-2.5 !bg-white !dark:!bg-slate-900 !border !border-slate-300 dark:!border-slate-600 !-left-1"
        />
      )}

      <div className="p-2.5 pt-3">
        <div className="flex items-center gap-1.5 mb-1.5">
          <div className={cn('p-1.5 rounded-lg border border-white/10 shadow-inner', styles.accent)}>{styles.icon}</div>
          <div className="flex-1 min-w-0">
            <p className="font-medium text-slate-900 dark:text-slate-100 text-legal truncate tracking-tight">{data.label}</p>
            <span className="text-tiny text-slate-500 dark:text-slate-400 uppercase tracking-[0.25em]">{data.type}</span>
          </div>
        </div>
        <p className="text-micro text-slate-500 dark:text-slate-400 leading-relaxed line-clamp-2 border-l border-slate-200 dark:border-slate-700 pl-1.5">
          {data.description}
        </p>
      </div>

      {data.type !== 'sink' && (
        <Handle
          type="source"
          position={Position.Right}
          className="!w-2.5 !h-2.5 !bg-white !dark:!bg-slate-900 !border !border-slate-300 dark:!border-slate-600 !-right-1"
        />
      )}
    </div>
  )
}

const nodeTypes = { studio: CustomNode }

const FIT_VIEW_OPTIONS: FitViewOptions = {
  padding: 0,
  minZoom: 0.2,
  maxZoom: 1.2
}
const DEFAULT_ZOOM_FACTOR = 0.7

type WorkflowCanvasRendererProps = {
  formattedLayout: WorkflowLayoutResult
  isDark: boolean
  viewportVersion: number
  resizeVersion: number
  workflowTimestamp: number
  nodesLength: number
}

type FlowWrapperProps = {
  fitViewOptions: FitViewOptions
  viewportVersion: number
  resizeVersion: number
  viewportPreferences: {
    preferredZoom: number
    offsetX: number
    offsetY: number
  }
  nodesLength: number
  workflowTimestamp: number
}

// 内部组件，处理 fitView
function FlowWrapper({
  fitViewOptions,
  viewportVersion,
  resizeVersion,
  viewportPreferences,
  nodesLength,
  workflowTimestamp
}: FlowWrapperProps) {
  const reactFlow = useReactFlow()
  const nodesInitialized = useNodesInitialized()
  const { preferredZoom, offsetX, offsetY } = viewportPreferences
  const fitViewRef = useRef(reactFlow.fitView)
  const setCenterRef = useRef(reactFlow.setCenter)
  const getViewportRef = useRef(reactFlow.getViewport)
  const lastAlignmentSignatureRef = useRef('')

  useEffect(() => {
    fitViewRef.current = reactFlow.fitView
    setCenterRef.current = reactFlow.setCenter
    getViewportRef.current = reactFlow.getViewport
  }, [reactFlow.fitView, reactFlow.setCenter, reactFlow.getViewport])

  useEffect(() => {
    if (nodesLength === 0 || !nodesInitialized) {
      lastAlignmentSignatureRef.current = ''
      return
    }

    const optionsSignature = `${fitViewOptions.padding ?? 'auto'}-${fitViewOptions.minZoom ?? 'auto'}-${fitViewOptions.maxZoom ?? 'auto'}`
    const effectSignature = [
      nodesLength,
      workflowTimestamp,
      viewportVersion,
      resizeVersion,
      Number.isFinite(preferredZoom) ? preferredZoom : 'auto',
      offsetX,
      offsetY,
      optionsSignature
    ].join('|')

    if (lastAlignmentSignatureRef.current === effectSignature) {
      return
    }
    lastAlignmentSignatureRef.current = effectSignature

    const frame = requestAnimationFrame(() => {
      const alignViewport = async () => {
        try {
          const fitView = fitViewRef.current
          const setCenter = setCenterRef.current
          const getViewport = getViewportRef.current
          if (!fitView || !setCenter || !getViewport) {
            return
          }
          await fitView(fitViewOptions)
          const { zoom } = getViewport()
          const zoomTarget = Number.isFinite(preferredZoom) ? preferredZoom : zoom
          const targetZoom = Math.max(
            fitViewOptions.minZoom ?? 0,
            Math.min(zoom * DEFAULT_ZOOM_FACTOR, zoomTarget)
          )
          await setCenter(offsetX, offsetY, { zoom: targetZoom, duration: 0 })
        } catch (error) {
          if (import.meta.env.DEV) {
            console.error('[WorkflowStudio] align viewport failed', error)
          }
        }
      }
      void alignViewport()
    })

    return () => cancelAnimationFrame(frame)
  }, [
    nodesLength,
    nodesInitialized,
    workflowTimestamp,
    fitViewOptions,
    viewportVersion,
    resizeVersion,
    preferredZoom,
    offsetX,
    offsetY
  ])

  return null
}

export default function WorkflowCanvasRenderer({
  formattedLayout,
  isDark,
  viewportVersion,
  resizeVersion,
  workflowTimestamp,
  nodesLength
}: WorkflowCanvasRendererProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<StudioNodeData>>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const hasWorkflow = formattedLayout.nodes.length > 0

  useEffect(() => {
    if (!hasWorkflow) {
      setNodes([])
      setEdges([])
      return
    }

    setNodes((previous) => {
      const previousMap = new Map(previous.map((node) => [node.id, node]))
      return formattedLayout.nodes.map((node) => {
        const existing = previousMap.get(node.id)
        const samePosition = existing?.position?.x === node.position.x && existing?.position?.y === node.position.y
        const sameData =
          existing?.data?.label === node.label &&
          existing?.data?.type === node.type &&
          existing?.data?.description === node.description

        if (existing && samePosition && sameData) {
          return existing
        }

        return {
          ...existing,
          id: node.id,
          type: 'studio',
          position: node.position,
          data: {
            label: node.label,
            type: node.type,
            description: node.description
          }
        }
      })
    })

    setEdges((previous) => {
      const previousMap = new Map(previous.map((edge) => [edge.id, edge]))
      return formattedLayout.edges.map((edge) => {
        const existing = previousMap.get(edge.id)
        const sameConnection = existing?.source === edge.source && existing?.target === edge.target
        const style = { stroke: '#818cf8', strokeWidth: 2, opacity: 0.65 }
        const sameStyle =
          existing?.animated === true &&
          existing?.markerEnd?.type === MarkerType.ArrowClosed &&
          existing?.markerEnd?.color === '#818cf8' &&
          existing?.style?.stroke === style.stroke &&
          existing?.style?.strokeWidth === style.strokeWidth &&
          existing?.style?.opacity === style.opacity

        if (existing && sameConnection && sameStyle) {
          return existing
        }

        return {
          ...existing,
          id: edge.id,
          source: edge.source,
          target: edge.target,
          animated: true,
          style,
          markerEnd: { type: MarkerType.ArrowClosed, color: '#818cf8' }
        }
      })
    })
  }, [formattedLayout, hasWorkflow, setEdges, setNodes])

  if (!hasWorkflow) {
    return null
  }

  return (
    <>
      <div className="absolute top-4 left-1/2 -translate-x-1/2 z-20">
        <div className="flex items-center gap-1.5 rounded-full border border-white/30 bg-white/80 dark:bg-slate-900/85 dark:border-white/10 backdrop-blur-lg px-2 py-0.5 shadow-xl text-legal">
          <button type="button" className="p-1.5 rounded-full hover:bg-white/70 dark:hover:bg-white/10 transition-colors text-slate-500 dark:text-slate-100">
            <Share2 size={14} />
          </button>
          <span className="font-semibold text-slate-600 dark:text-slate-100 px-2">Pipeline v1.0</span>
          <button
            type="button"
            className="flex items-center gap-1.5 px-3 py-1 rounded-full bg-indigo-600 text-white font-semibold hover:bg-indigo-500 transition-all shadow-[0_6px_18px_rgba(79,70,229,0.35)]"
          >
            <Play size={10} fill="currentColor" />
            Run
          </button>
          <button type="button" className="p-1.5 rounded-full hover:bg-white/70 dark:hover:bg-white/10 transition-colors text-slate-500 dark:text-slate-100">
            <MoreHorizontal size={14} />
          </button>
        </div>
      </div>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        nodeTypes={nodeTypes}
        colorMode={isDark ? 'dark' : 'light'}
        minZoom={0.2}
        maxZoom={1.2}
        className={cn('transition-colors duration-300', isDark ? 'bg-transparent' : 'bg-transparent')}
      >
        <FlowWrapper
          fitViewOptions={FIT_VIEW_OPTIONS}
          viewportVersion={viewportVersion}
          resizeVersion={resizeVersion}
          viewportPreferences={formattedLayout.viewport}
          nodesLength={nodesLength}
          workflowTimestamp={workflowTimestamp}
        />
        <Background
          color={isDark ? '#475569' : '#94a3b8'}
          gap={16}
          size={1}
          variant={BackgroundVariant.Lines}
          className={cn(isDark ? 'opacity-20' : 'opacity-40')}
        />
        <MiniMap
          className={cn(
            '!m-6 backdrop-blur-xl rounded-xl border',
            isDark ? '!bg-slate-900/70 !border-white/10' : '!bg-white/80 !border-white/40'
          )}
        />
      </ReactFlow>
    </>
  )
}
