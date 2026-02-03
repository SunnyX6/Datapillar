import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores'

vi.mock('@/lib/api/auth', () => ({
  login: vi.fn().mockResolvedValue({
    loginStage: 'SUCCESS',
    userId: 10001,
    tenantId: 0,
    username: 'mock-user',
    email: 'mock-user@datapillar.ai',
    roles: [],
    menus: []
  }),
  loginTenant: vi.fn(),
  logout: vi.fn(),
  ssoLogin: vi.fn()
}))

vi.mock('@/lib/api/token', () => ({
  getTokenInfo: vi.fn()
}))

const DAY_MS = 24 * 60 * 60 * 1000

const resetAuthStore = () => {
  useAuthStore.setState({
    user: null,
    loading: false,
    error: null,
    isAuthenticated: false,
    sessionExpiresAt: null,
    pendingRememberMe: null
  })
}

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    resetAuthStore()
    vi.clearAllMocks()
  })

  it('登录勾选记住我时设置30天过期时间', async () => {
    const before = Date.now()
    await useAuthStore.getState().login('mock-user', 'password', true)

    const { sessionExpiresAt, isAuthenticated, lastUsername, lastRememberMe } = useAuthStore.getState()
    expect(isAuthenticated).toBe(true)
    expect(lastUsername).toBe('mock-user')
    expect(lastRememberMe).toBe(true)
    expect(sessionExpiresAt).toBeGreaterThanOrEqual(before + 30 * DAY_MS)
    expect(sessionExpiresAt).toBeLessThanOrEqual(before + 30 * DAY_MS + 2000)
  })

  it('登录未勾选记住我时设置7天过期时间', async () => {
    const before = Date.now()
    await useAuthStore.getState().login('mock-user', 'password', false)

    const { sessionExpiresAt, isAuthenticated, lastUsername, lastRememberMe } = useAuthStore.getState()
    expect(isAuthenticated).toBe(true)
    expect(lastUsername).toBe('mock-user')
    expect(lastRememberMe).toBe(false)
    expect(sessionExpiresAt).toBeGreaterThanOrEqual(before + 7 * DAY_MS)
    expect(sessionExpiresAt).toBeLessThanOrEqual(before + 7 * DAY_MS + 2000)
  })

  it('initializeAuth 遇到过期会话应清理状态', async () => {
    useAuthStore.setState({
      user: {
        userId: 10001,
        tenantId: 0,
        username: 'mock-user',
        roles: [],
        menus: []
      },
      isAuthenticated: true,
      sessionExpiresAt: Date.now() - 1000
    })

    const { getTokenInfo } = await import('@/lib/api/token')
    await useAuthStore.getState().initializeAuth()

    const state = useAuthStore.getState()
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
    expect(state.lastUsername).toBeNull()
    expect(state.lastRememberMe).toBe(false)
    expect(getTokenInfo).not.toHaveBeenCalled()
  })
})
