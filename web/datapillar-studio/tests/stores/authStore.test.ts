// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores'

vi.mock('@/services/authService', () => ({
  login: vi.fn().mockResolvedValue({
    userId: 10001,
    username: 'mock-user',
    email: 'mock-user@datapillar.ai',
    tenants: [
      {
        tenantId: 0,
        tenantCode: 'tenant-default',
        tenantName: '默认租户',
        status: 1,
        isDefault: 1
      }
    ],
    roles: [],
    menus: []
  }),
  loginSso: vi.fn(),
  loginTenant: vi.fn(),
  logout: vi.fn()
}))

const DAY_MS = 24 * 60 * 60 * 1000

const resetAuthStore = () => {
  useAuthStore.setState({
    user: null,
    loading: false,
    error: null,
    isAuthenticated: false,
    sessionExpiresAt: null,
    pendingRememberMe: null,
    lastUsername: null,
    lastRememberMe: false
  })
}

const createActiveSessionState = () => ({
  user: {
    userId: 10001,
    tenantId: 0,
    username: 'mock-user',
    roles: [],
    menus: []
  },
  isAuthenticated: true,
  sessionExpiresAt: Date.now() + 60 * 1000
})

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
    const activeSessionState = createActiveSessionState()
    useAuthStore.setState({
      user: activeSessionState.user,
      isAuthenticated: true,
      sessionExpiresAt: Date.now() - 1000
    })

    await useAuthStore.getState().initializeAuth()

    const state = useAuthStore.getState()
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
    expect(state.lastUsername).toBeNull()
    expect(state.lastRememberMe).toBe(false)
  })

  it('initializeAuth 遇到有效会话时直接恢复认证状态', async () => {
    const activeSessionState = createActiveSessionState()
    useAuthStore.setState(activeSessionState)

    await useAuthStore.getState().initializeAuth()

    const state = useAuthStore.getState()
    expect(state.user).toEqual(activeSessionState.user)
    expect(state.isAuthenticated).toBe(true)
    expect(state.loading).toBe(false)
    expect(state.error).toBeNull()
  })
})
