import { useEffect, useRef, useState } from 'react'
import { formatWorkflowGraph, type WorkflowLayoutOptions, type WorkflowLayoutResult } from '@/layouts/workflow/utils/formatter'
import type { WorkflowGraph } from '@/services/workflowStudioService'

type IdleTaskHandle = number | null

const hasIdleCallback = typeof window !== 'undefined' && 'requestIdleCallback' in window

const scheduleIdle = (callback: () => void): IdleTaskHandle => {
  if (hasIdleCallback && typeof window.requestIdleCallback === 'function') {
    return window.requestIdleCallback(() => callback())
  }
  return window.setTimeout(callback, 32)
}

const cancelIdle = (handle: IdleTaskHandle) => {
  if (handle === null) return
  if (hasIdleCallback && typeof window.cancelIdleCallback === 'function') {
    window.cancelIdleCallback(handle)
    return
  }
  clearTimeout(handle)
}

export const useDeferredWorkflowLayout = (
  workflow: WorkflowGraph,
  options: WorkflowLayoutOptions
): WorkflowLayoutResult => {
  const [layout, setLayout] = useState(() => formatWorkflowGraph(workflow, options))
  const workflowRef = useRef(workflow)
  const optionsRef = useRef(options)
  const lastSignatureRef = useRef<string | null>(null)

  useEffect(() => {
    workflowRef.current = workflow
  }, [workflow])

  useEffect(() => {
    optionsRef.current = options
  }, [options])

  useEffect(() => {
    let cancelled = false
    const signature = [
      workflow.lastUpdated,
      workflow.nodes.length,
      workflow.edges.length,
      options.nodeWidth,
      options.nodeHeight,
      options.columnGap,
      options.rowGap
    ].join('|')
    if (signature === lastSignatureRef.current) {
      return undefined
    }
    const handle = scheduleIdle(() => {
      if (cancelled) return
      const nextLayout = formatWorkflowGraph(workflowRef.current, optionsRef.current)
      lastSignatureRef.current = signature
      setLayout(nextLayout)
    })
    return () => {
      cancelled = true
      cancelIdle(handle)
    }
  }, [workflow, options])

  return layout
}
