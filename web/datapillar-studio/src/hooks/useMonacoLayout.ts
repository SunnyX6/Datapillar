import { useCallback, useEffect, useRef } from 'react'
import type { RefObject } from 'react'
import type * as Monaco from 'monaco-editor'

type LayoutSize = {
  width: number
  height: number
}

export const useMonacoLayout = (
  editor: Monaco.editor.IStandaloneCodeEditor | null,
  containerRef: RefObject<HTMLElement>,
) => {
  const layoutFrameRef = useRef<number | null>(null)
  const layoutSizeRef = useRef<LayoutSize | null>(null)

  const layoutNow = useCallback((overrideEditor?: Monaco.editor.IStandaloneCodeEditor | null) => {
    const targetEditor = overrideEditor ?? editor
    const container = containerRef.current
    if (!targetEditor || !container) return
    const rect = container.getBoundingClientRect()
    const width = Math.floor(rect.width)
    const height = Math.floor(rect.height)
    if (width <= 0 || height <= 0) return
    const lastSize = layoutSizeRef.current
    if (lastSize && lastSize.width === width && lastSize.height === height) {
      return
    }
    layoutSizeRef.current = { width, height }
    targetEditor.layout({ width, height })
  }, [editor, containerRef])

  const scheduleLayout = useCallback(() => {
    if (!editor || !containerRef.current) return
    if (layoutFrameRef.current !== null) {
      cancelAnimationFrame(layoutFrameRef.current)
    }
    layoutFrameRef.current = requestAnimationFrame(() => {
      layoutFrameRef.current = null
      layoutNow()
    })
  }, [editor, containerRef, layoutNow])

  useEffect(() => {
    if (!editor || !containerRef.current) return
    layoutSizeRef.current = null
    scheduleLayout()

    if (typeof ResizeObserver === 'undefined') {
      const handleResize = () => scheduleLayout()
      window.addEventListener('resize', handleResize)
      return () => window.removeEventListener('resize', handleResize)
    }

    const observer = new ResizeObserver(() => scheduleLayout())
    observer.observe(containerRef.current)
    return () => observer.disconnect()
  }, [editor, scheduleLayout, containerRef])

  useEffect(() => {
    return () => {
      if (layoutFrameRef.current !== null) {
        cancelAnimationFrame(layoutFrameRef.current)
        layoutFrameRef.current = null
      }
    }
  }, [])

  return { layoutNow, scheduleLayout }
}
