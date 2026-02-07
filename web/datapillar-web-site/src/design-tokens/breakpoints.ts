export type BreakpointKey = 'md' | 'lg' | 'xl' | '2xl' | '3xl'

type BreakpointMap = Record<BreakpointKey, number>

const BREAKPOINT_VAR_MAP: Record<BreakpointKey, string> = {
  md: '--breakpoint-md',
  lg: '--breakpoint-lg',
  xl: '--breakpoint-xl',
  '2xl': '--breakpoint-2xl',
  '3xl': '--breakpoint-3xl'
}

// 仅用于 SSR/测试兜底，权威来源仍是 @theme 中的 --breakpoint-*
const FALLBACK_BREAKPOINTS: BreakpointMap = {
  md: 1080,
  lg: 1440,
  xl: 1920,
  '2xl': 2560,
  '3xl': 3840
}

export const BREAKPOINT_ORDER: BreakpointKey[] = ['3xl', '2xl', 'xl', 'lg', 'md']

let cachedBreakpoints: BreakpointMap | null = null

const canReadCssVars = () =>
  typeof window !== 'undefined'
  && typeof document !== 'undefined'
  && typeof getComputedStyle === 'function'

const parsePixelValue = (value: string): number | null => {
  const trimmed = value.trim()
  if (!trimmed) return null
  const parsed = Number.parseFloat(trimmed)
  return Number.isFinite(parsed) ? parsed : null
}

const readCssVar = (name: string): number | null => {
  if (!canReadCssVars()) return null
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name)
  return parsePixelValue(raw)
}

export function getBreakpoints(): BreakpointMap {
  if (cachedBreakpoints) return cachedBreakpoints

  const result = {} as BreakpointMap
  (Object.keys(BREAKPOINT_VAR_MAP) as BreakpointKey[]).forEach((key) => {
    const value = readCssVar(BREAKPOINT_VAR_MAP[key])
    result[key] = value ?? FALLBACK_BREAKPOINTS[key]
  })

  cachedBreakpoints = result
  return result
}

export function getBreakpointValue(key: BreakpointKey): number {
  return getBreakpoints()[key]
}

export function resetBreakpointCache(): void {
  cachedBreakpoints = null
}
