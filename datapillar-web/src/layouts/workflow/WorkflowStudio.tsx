import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { cn } from '@/lib/utils'
import { useWorkflowStudioStore, useIsDark } from '@/stores'
import { useDeferredWorkflowLayout } from '@/hooks/useDeferredWorkflowLayout'
import { WorkflowHero } from './WorkflowHero'

const WorkflowCanvasRenderer = lazy(async () => {
  const module = await import('./WorkflowCanvasRenderer')
  return { default: module.default }
})

export function WorkflowCanvasPanel({ viewportVersion = 0 }: { viewportVersion?: number }) {
  const workflow = useWorkflowStudioStore((state) => state.workflow)
  const isDark = useIsDark()
  const [resizeVersion, setResizeVersion] = useState(0)
  const canvasRef = useRef<HTMLDivElement | null>(null)
  const layoutOptions = useMemo(
    () => ({
      columnGap: 300,
      rowGap: 150,
      nodeWidth: 172,
      nodeHeight: 96
    }),
    []
  )
  const formattedLayout = useDeferredWorkflowLayout(workflow, layoutOptions)

  useEffect(() => {
    if (typeof ResizeObserver === 'undefined') {
      const handleResize = () => setResizeVersion((version) => version + 1)
      window.addEventListener('resize', handleResize)
      return () => window.removeEventListener('resize', handleResize)
    }

    if (!canvasRef.current) {
      return
    }

    const observer = new ResizeObserver(() => {
      setResizeVersion((version) => version + 1)
    })
    observer.observe(canvasRef.current)
    return () => observer.disconnect()
  }, [])

  const hasWorkflow = formattedLayout.nodes.length > 0

  return (
    <div
      ref={canvasRef}
      className={cn('flex-1 relative overflow-hidden transition-colors duration-300', isDark ? 'bg-[#050713]' : 'bg-[#f1f5f9]')}
    >
      {hasWorkflow ? (
        <Suspense
          fallback={
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="rounded-full border-2 border-indigo-200 border-t-indigo-500 size-10 animate-spin" />
            </div>
          }
        >
          <WorkflowCanvasRenderer
            formattedLayout={formattedLayout}
            isDark={isDark}
            viewportVersion={viewportVersion}
            resizeVersion={resizeVersion}
            workflowTimestamp={workflow.lastUpdated}
            nodesLength={workflow.nodes.length}
          />
        </Suspense>
      ) : (
        <WorkflowHero />
      )}
    </div>
  )
}

export function StudioStatsStrip() {
  const workflow = useWorkflowStudioStore((state) => state.workflow)

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-2 p-4 border-t border-slate-200/80 dark:border-white/5 bg-white/75 dark:bg-black/30">
      <StatBadge label="Nodes" value={workflow.stats.nodes} tone="indigo" />
      <StatBadge label="Edges" value={workflow.stats.edges} tone="purple" />
      <StatBadge label="Runtime" value={`${workflow.stats.runtimeMinutes}m`} tone="emerald" />
    </div>
  )
}

function StatBadge({ label, value, tone }: { label: string; value: string | number; tone: 'indigo' | 'purple' | 'emerald' }) {
  const toneClasses: Record<'indigo' | 'purple' | 'emerald', string> = {
    indigo: 'from-indigo-500/15 to-indigo-500/5 text-indigo-700 dark:text-indigo-200',
    purple: 'from-purple-500/15 to-purple-500/5 text-purple-700 dark:text-purple-200',
    emerald: 'from-emerald-500/15 to-emerald-500/5 text-emerald-700 dark:text-emerald-200'
  }
  return (
    <div className={cn('rounded-2xl border border-slate-200/80 dark:border-white/5 px-3 py-2.5 bg-gradient-to-br text-sm font-medium', toneClasses[tone])}>
      <p className="text-micro uppercase tracking-[0.35em]">{label}</p>
      <p className="text-base font-semibold mt-1">{value}</p>
    </div>
  )
}
