import type { Page } from '@playwright/test'

export type WebVitalsMetrics = {
  lcp: number
  cls: number
  inp: number
  clsEntries: LayoutShiftEntry[]
}

type LayoutShiftEntry = {
  value: number
  startTime: number
  sources: LayoutShiftSource[]
  ignored?: boolean
  ignoredReason?: string
}

type LayoutShiftSource = {
  tagName?: string
  id?: string
  className?: string
  previousRect?: LayoutShiftRect | null
  currentRect?: LayoutShiftRect | null
}

type LayoutShiftRect = {
  x: number
  y: number
  width: number
  height: number
}

export const initWebVitals = async (page: Page) => {
  await page.addInitScript(() => {
    type LayoutShiftPerformanceEntry = PerformanceEntry & {
      value?: number
      hadRecentInput?: boolean
      sources?: Array<{
        node?: Element
        previousRect?: DOMRectReadOnly
        currentRect?: DOMRectReadOnly
      }>
    }

    const globalScope = window as typeof window & {
      __webVitals?: { lcp: number; cls: number; inp: number; clsEntries: LayoutShiftEntry[] }
    }

    const MAX_SHIFT_ENTRIES = 20
    const MAX_SHIFT_SOURCES = 5
    const INPUT_RECENT_THRESHOLD = 500
    const MONACO_ROOT_SELECTOR = '.monaco-editor'

    globalScope.__webVitals = { lcp: 0, cls: 0, inp: 0, clsEntries: [] }

    let clsValue = 0
    let lastInputTime = 0

    const markRecentInput = () => {
      lastInputTime = performance.now()
    }

    ;[
      'pointerdown',
      'pointerup',
      'mousedown',
      'mouseup',
      'keydown',
      'keyup',
      'touchstart',
      'touchend'
    ].forEach((eventType) => {
      window.addEventListener(eventType, markRecentInput, { capture: true, passive: true })
    })

    const isMonacoNode = (node?: Element) => {
      if (!node) return false
      if ('closest' in node && typeof node.closest === 'function') {
        return Boolean(node.closest(MONACO_ROOT_SELECTOR))
      }
      return false
    }

    const isMonacoShift = (sources?: LayoutShiftPerformanceEntry['sources']) => {
      if (!sources || sources.length === 0) return false
      return sources.every(source => isMonacoNode(source.node))
    }

    if (PerformanceObserver.supportedEntryTypes?.includes('layout-shift')) {
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          const shift = entry as LayoutShiftPerformanceEntry
          const shiftStartTime = shift.startTime ?? 0
          const monacoOnly = isMonacoShift(shift.sources)
          const recentInput =
            shift.hadRecentInput ||
            (lastInputTime > 0 &&
              shiftStartTime >= lastInputTime &&
              shiftStartTime - lastInputTime <= INPUT_RECENT_THRESHOLD)
          if (recentInput || monacoOnly) continue
          clsValue += shift.value ?? 0
        }
        if (globalScope.__webVitals) {
          globalScope.__webVitals.cls = clsValue
        }

        if (!globalScope.__webVitals?.clsEntries) return
        if (globalScope.__webVitals.clsEntries.length >= MAX_SHIFT_ENTRIES) return

        const latest = list.getEntries().slice(-1)[0] as LayoutShiftPerformanceEntry | undefined
        if (!latest) return
        const latestStartTime = latest.startTime ?? 0
        const latestMonacoOnly = isMonacoShift(latest.sources)
        const latestRecentInput =
          latest.hadRecentInput ||
          (lastInputTime > 0 &&
            latestStartTime >= lastInputTime &&
            latestStartTime - lastInputTime <= INPUT_RECENT_THRESHOLD)
        if (latestRecentInput) return

        const sources = latest.sources ?? []
        const sourceSummaries = sources.slice(0, MAX_SHIFT_SOURCES).map((source) => {
          const node = source.node
          const rawClassName = node && 'className' in node ? node.className : ''
          const className = typeof rawClassName === 'string' ? rawClassName : ''
          const previousRect = source.previousRect
          const currentRect = source.currentRect
          return {
            tagName: node?.tagName,
            id: node && 'id' in node ? (node as HTMLElement).id : undefined,
            className: className || undefined,
            previousRect: previousRect
              ? { x: previousRect.x, y: previousRect.y, width: previousRect.width, height: previousRect.height }
              : null,
            currentRect: currentRect
              ? { x: currentRect.x, y: currentRect.y, width: currentRect.width, height: currentRect.height }
              : null
          }
        })

        globalScope.__webVitals.clsEntries.push({
          value: latest.value ?? 0,
          startTime: latest.startTime ?? 0,
          sources: sourceSummaries,
          ignored: latestMonacoOnly || undefined,
          ignoredReason: latestMonacoOnly ? 'monaco-internal' : undefined
        })
      }).observe({ type: 'layout-shift', buffered: true })
    }

    if (PerformanceObserver.supportedEntryTypes?.includes('largest-contentful-paint')) {
      new PerformanceObserver((list) => {
        const entries = list.getEntries()
        const lastEntry = entries[entries.length - 1]
        if (!lastEntry) return
        if (globalScope.__webVitals) {
          globalScope.__webVitals.lcp = lastEntry.startTime
        }
      }).observe({ type: 'largest-contentful-paint', buffered: true })
    }

    if (PerformanceObserver.supportedEntryTypes?.includes('event')) {
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          const eventEntry = entry as PerformanceEntry & { interactionId?: number; duration?: number }
          if (!eventEntry.interactionId) continue
          const duration = eventEntry.duration ?? 0
          if (globalScope.__webVitals && duration > globalScope.__webVitals.inp) {
            globalScope.__webVitals.inp = duration
          }
        }
      }).observe({ type: 'event', buffered: true, durationThreshold: 40 })
    }
  })
}

export const collectWebVitals = async (page: Page): Promise<WebVitalsMetrics> => {
  return page.evaluate(() => {
    const globalScope = window as typeof window & {
      __webVitals?: { lcp: number; cls: number; inp: number; clsEntries?: LayoutShiftEntry[] }
    }
    const metrics = globalScope.__webVitals ?? { lcp: 0, cls: 0, inp: 0, clsEntries: [] }
    return {
      lcp: metrics.lcp,
      cls: metrics.cls,
      inp: metrics.inp,
      clsEntries: metrics.clsEntries ?? []
    }
  })
}
