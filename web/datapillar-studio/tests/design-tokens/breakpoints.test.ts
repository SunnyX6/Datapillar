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

  it('from CSS Variable reading breakpoint value', () => {
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

  it('CSS Use the bottom value when the variable is invalid', () => {
    mockCssVariables({
      '--breakpoint-md': 'not-a-number'
    })

    const breakpoints = getBreakpoints()
    expect(breakpoints.md).toBe(1080)
    expect(breakpoints.xl).toBe(1920)
  })

  it('None DOM Use the lowest value for the environment', () => {
    globalScope.document = undefined
    globalScope.getComputedStyle = undefined

    const breakpoints = getBreakpoints()
    expect(breakpoints.lg).toBe(1440)
    expect(breakpoints['3xl']).toBe(3840)
  })
})
