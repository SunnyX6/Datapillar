import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { getBreakpoints, getBreakpointValue, resetBreakpointCache } from '@/design-tokens/breakpoints'

const globalScope = globalThis as typeof globalThis & {
  document?: Document
  getComputedStyle?: (elt: Element) => CSSStyleDeclaration
}

const originalDocument = globalScope.document
const originalGetComputedStyle = globalScope.getComputedStyle

const mockCssVariables = (values: Record<string, string>) => {
  globalScope.document = { documentElement: {} as Element } as Document
  globalScope.getComputedStyle = () =>
    ({
      getPropertyValue: (name: string) => values[name] ?? ''
    }) as CSSStyleDeclaration
}

describe('breakpoints', () => {
  beforeEach(() => {
    resetBreakpointCache()
  })

  afterEach(() => {
    resetBreakpointCache()
    globalScope.document = originalDocument
    globalScope.getComputedStyle = originalGetComputedStyle
  })

  it('从 CSS 变量读取断点值', () => {
    mockCssVariables({
      '--breakpoint-md': '1080px',
      '--breakpoint-lg': '1440px',
      '--breakpoint-xl': '1920px',
      '--breakpoint-2xl': '2560px',
      '--breakpoint-3xl': '3840px'
    })

    expect(getBreakpointValue('xl')).toBe(1920)
    expect(getBreakpointValue('2xl')).toBe(2560)
  })

  it('CSS 变量无效时使用兜底值', () => {
    mockCssVariables({
      '--breakpoint-md': 'not-a-number'
    })

    const breakpoints = getBreakpoints()
    expect(breakpoints.md).toBe(1080)
    expect(breakpoints.xl).toBe(1920)
  })

  it('无 DOM 环境时使用兜底值', () => {
    globalScope.document = undefined
    globalScope.getComputedStyle = undefined

    const breakpoints = getBreakpoints()
    expect(breakpoints.lg).toBe(1440)
    expect(breakpoints['3xl']).toBe(3840)
  })
})
