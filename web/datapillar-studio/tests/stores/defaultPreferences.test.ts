// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'

describe('默认偏好', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    vi.resetModules()
  })

  it('主题默认应为浅色', async () => {
    const { useThemeStore } = await import('@/stores/themeStore')

    expect(useThemeStore.getState().mode).toBe('light')
    useThemeStore.getState().initialize()
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('语言默认应为中文', async () => {
    const { useI18nStore } = await import('@/stores/i18nStore')
    const i18n = (await import('@/lib/i18n')).default

    expect(useI18nStore.getState().language).toBe('zh-CN')
    expect(i18n.options.lng).toBe('zh-CN')
  })
})
