// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

describe('Default preference', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    vi.resetModules()
  })

  it('Theme should be light by default', async () => {
    const { useThemeStore } = await import('@/state/themeStore')

    expect(useThemeStore.getState().mode).toBe('light')
    useThemeStore.getState().initialize()
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('The language default should be Chinese', async () => {
    const { useI18nStore } = await import('@/state/i18nStore')
    const i18n = (await import('@/app/i18n')).default

    expect(useI18nStore.getState().language).toBe('zh-CN')
    expect(i18n.options.lng).toBe('zh-CN')
  })
})
