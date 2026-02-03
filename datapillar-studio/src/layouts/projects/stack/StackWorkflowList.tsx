import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Activity, ChevronRight, Clock, Workflow } from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui'
import { contentMaxWidthClassMap, iconSizeToken } from '@/design-tokens/dimensions'
import { StatusBadge } from './StackUi'
import type { WorkflowDefinition } from './types'

const MIN_PAGE_SIZE = 10
const MAX_PAGE_SIZE = 50
const PAGE_BUFFER = 3

const HealthStrip = ({ status }: { status: WorkflowDefinition['status'] }) => {
  const bars = Array.from({ length: 32 })
  const isHealthy = status === 'healthy'
  const isRunning = status === 'running'
  const isWarning = status === 'warning'
  const isError = status === 'error'
  const baseColor = isHealthy
    ? 'bg-emerald-500'
    : isRunning
      ? 'bg-blue-500'
      : isWarning
        ? 'bg-amber-500'
        : isError
          ? 'bg-red-500'
          : 'bg-slate-300 dark:bg-slate-600'

  return (
    <div className="flex w-full items-center justify-between">
      {bars.map((_, idx) => {
        const isNow = idx === bars.length - 1
        return (
          <div
            key={idx}
            className={`h-3 w-1 rounded-full ${isNow ? 'bg-blue-500' : baseColor}`}
            style={{ opacity: isNow ? 1 : 0.85 }}
          />
        )
      })}
    </div>
  )
}

type StackWorkflowListProps = {
  workflows: WorkflowDefinition[]
  onSelect: (workflow: WorkflowDefinition) => void
}

export function StackWorkflowList({ workflows, onSelect }: StackWorkflowListProps) {
  // 模拟真实分页：动态 pageSize（根据当前数据行高度 + 容器可视高度计算），并按 offset/limit 逐批追加。
  const [items, setItems] = useState<WorkflowDefinition[]>([])
  const [pageSize, setPageSize] = useState(MIN_PAGE_SIZE)
  const [loadingMore, setLoadingMore] = useState(false)
  const [offset, setOffset] = useState(0)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setItems([])
    setOffset(0)
    setLoadingMore(false)
  }, [workflows])

  const hasMore = offset < workflows.length

  const recomputePageSize = useCallback(() => {
    const container = containerRef.current
    if (!container) return

    const firstRow = container.querySelector('tbody tr') as HTMLElement | null
    if (!firstRow) return

    const rowHeight = firstRow.getBoundingClientRect().height
    if (!rowHeight || rowHeight <= 0) return

    const visibleRows = Math.max(1, Math.floor(container.clientHeight / rowHeight))
    const nextSize = Math.min(MAX_PAGE_SIZE, Math.max(MIN_PAGE_SIZE, visibleRows + PAGE_BUFFER))
    setPageSize((prev) => (prev === nextSize ? prev : nextSize))
  }, [])

  useLayoutEffect(() => {
    recomputePageSize()

    const container = containerRef.current
    if (!container || typeof ResizeObserver === 'undefined') return

    const observer = new ResizeObserver(() => recomputePageSize())
    observer.observe(container)
    return () => observer.disconnect()
  }, [recomputePageSize])

  useEffect(() => {
    recomputePageSize()
  }, [items.length, recomputePageSize])

  const loadMore = useCallback(async () => {
    if (loadingMore || !hasMore) return
    setLoadingMore(true)
    try {
      if (import.meta.env.DEV) {
        await new Promise((resolve) => setTimeout(resolve, 250))
      }
      const next = workflows.slice(offset, offset + pageSize)
      setItems((prev) => [...prev, ...next])
      setOffset((prev) => prev + next.length)
    } finally {
      setLoadingMore(false)
    }
  }, [hasMore, loadingMore, offset, pageSize, workflows])

  return (
    <div ref={containerRef} className="flex-1 overflow-auto custom-scrollbar p-6">
      <div className={`w-full ${contentMaxWidthClassMap.full} mx-auto pb-10`}>
        <Table
          layout="auto"
          minWidth="none"
          infiniteScroll={{ hasMore, loading: loadingMore, onLoadMore: loadMore }}
        >
          <TableHeader>
            <TableRow>
              <TableHead className="w-[35%]">Workflow Definition</TableHead>
              <TableHead className="w-[25%]">
                <div className="flex items-center">
                  <Activity size={12} className="mr-1.5" />
                  健康趋势
                </div>
              </TableHead>
              <TableHead className="w-[15%]">Schedule & Owner</TableHead>
              <TableHead className="w-[15%]">Performance</TableHead>
              <TableHead className="w-[10%] text-right">State</TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {items.map((workflow) => (
              <TableRow
                key={workflow.id}
                onClick={() => onSelect(workflow)}
                className="group hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors cursor-pointer border-b border-slate-100 dark:border-slate-800 last:border-0"
              >
                <TableCell>
                  <div className="flex items-center space-x-4">
                    <div className="p-1.5 rounded-lg bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400">
                      <Workflow size={iconSizeToken.small} />
                    </div>
                    <div>
                      <div className="flex items-center">
                        <span className="text-body-sm font-medium text-slate-800 dark:text-slate-100 group-hover:text-indigo-600 dark:group-hover:text-indigo-300 transition-colors">
                          {workflow.name}
                        </span>
                        {workflow.tags.includes('P0') && (
                          <span className="ml-2 px-1.5 py-0.5 bg-red-50 text-red-600 text-nano font-bold rounded border border-red-100 dark:bg-red-500/10 dark:text-red-200 dark:border-red-500/20">
                            P0
                          </span>
                        )}
                      </div>
                      <div className="flex items-center space-x-2 mt-1">
                        <code className="text-micro bg-slate-100 dark:bg-slate-800 px-1.5 py-0.5 rounded text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700 font-mono">
                          {workflow.id}
                        </code>
                        <span className="text-slate-300 dark:text-slate-700">|</span>
                        <p className="text-caption text-slate-500 dark:text-slate-400 line-clamp-1 max-w-[12.5rem]">{workflow.description}</p>
                      </div>
                    </div>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="w-full max-w-[15rem]">
                    <div className="px-0.5">
                      <HealthStrip status={workflow.status} />
                      <div className="flex justify-between text-nano text-slate-400 dark:text-slate-500 font-mono mt-1">
                        <span>-24h</span>
                        <span>Now</span>
                      </div>
                    </div>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex flex-col space-y-2">
                    <div className="flex items-center text-caption text-slate-600 dark:text-slate-300">
                      <Clock size={12} className="mr-2 text-slate-400" />
                      <span className="font-mono">{workflow.schedule}</span>
                    </div>
                    <div className="flex items-center text-caption text-slate-600 dark:text-slate-300">
                      <div className="w-4 h-4 rounded-full bg-gradient-to-r from-indigo-500 to-purple-500 flex items-center justify-center text-tiny text-white font-bold mr-2 shadow-sm">
                        {workflow.owner.charAt(0)}
                      </div>
                      {workflow.owner}
                    </div>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex flex-col space-y-1">
                    <div className="text-body-sm font-semibold text-slate-800 dark:text-slate-100 font-mono">{workflow.avgDuration}</div>
                    <div className="text-micro text-slate-400 dark:text-slate-500">Avg. Duration</div>
                  </div>
                  <div className="text-micro text-slate-400 dark:text-slate-500 mt-1">
                    Last: <span className="font-medium text-slate-600 dark:text-slate-300">{workflow.lastRun}</span>
                  </div>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex items-center justify-end space-x-4">
                    <StatusBadge status={workflow.status} />
                    <div className="w-8 flex justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                      <div className="p-1.5 rounded-md hover:bg-slate-100 dark:hover:bg-slate-700 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200">
                        <ChevronRight size={16} />
                      </div>
                    </div>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
