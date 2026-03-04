// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/state'
import { logout as apiLogout } from '@/services/authService'

vi.mock('@/services/authService', () => ({
  login: vi.fn().mockResolvedValue({
    userId: 10001,
    username: 'mock-user',
    email: 'mock-user@datapillar.ai',
    tenants: [
      {
        tenantId: 0,
        tenantCode: 'tenant-default',
        tenantName: 'Default tenant',
        status: 1,
        isDefault: 1
      }
    ]
  }),
  getMyMenus: vi.fn().mockResolvedValue([]),
  loginSso: vi.fn(),
  loginTenant: vi.fn(),
  logout: vi.fn()
}))

const DAY_MS = 24 * 60 * 60 * 1000

const resetAuthStore = () => {
  useAuthStore.setState({
    authStatus: 'ANONYMOUS',
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
  authStatus: 'AUTHENTICATED' as const,
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

  it('Set when logging in and check Remember me30Expiration time in days', async () => {
    const before = Date.now()
    await useAuthStore.getState().login('mock-user', 'password', true)

    const { sessionExpiresAt, isAuthenticated, lastUsername, lastRememberMe, authStatus } = useAuthStore.getState()
    expect(isAuthenticated).toBe(true)
    expect(authStatus).toBe('AUTHENTICATED')
    expect(lastUsername).toBe('mock-user')
    expect(lastRememberMe).toBe(true)
    expect(sessionExpiresAt).toBeGreaterThanOrEqual(before + 30 * DAY_MS)
    expect(sessionExpiresAt).toBeLessThanOrEqual(before + 30 * DAY_MS + 2000)
  })

  it('Set when logging in with Remember Me unchecked7Expiration time in days', async () => {
    const before = Date.now()
    await useAuthStore.getState().login('mock-user', 'password', false)

    const { sessionExpiresAt, isAuthenticated, lastUsername, lastRememberMe, authStatus } = useAuthStore.getState()
    expect(isAuthenticated).toBe(true)
    expect(authStatus).toBe('AUTHENTICATED')
    expect(lastUsername).toBe('mock-user')
    expect(lastRememberMe).toBe(false)
    expect(sessionExpiresAt).toBeGreaterThanOrEqual(before + 7 * DAY_MS)
    expect(sessionExpiresAt).toBeLessThanOrEqual(before + 7 * DAY_MS + 2000)
  })

  it('initializeAuth When encountering an expired session, the status should be cleared', async () => {
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
    expect(state.authStatus).toBe('ANONYMOUS')
    expect(state.lastUsername).toBeNull()
    expect(state.lastRememberMe).toBe(false)
  })

  it('initializeAuth Directly restore the authentication status when encountering a valid session', async () => {
    const activeSessionState = createActiveSessionState()
    useAuthStore.setState(activeSessionState)

    await useAuthStore.getState().initializeAuth()

    const state = useAuthStore.getState()
    expect(state.user).toEqual(activeSessionState.user)
    expect(state.isAuthenticated).toBe(true)
    expect(state.authStatus).toBe('AUTHENTICATED')
    expect(state.loading).toBe(false)
    expect(state.error).toBeNull()
  })

  it('logout Maintain current login status during request，Clear the local session after successful response', async () => {
    const activeSessionState = createActiveSessionState()
    useAuthStore.setState(activeSessionState)

    let resolveLogoutRequest: (() => void) | null = null
    vi.mocked(apiLogout).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveLogoutRequest = () => resolve({ code: 0, data: 'ok' })
      }),
    )

    const logoutPromise = useAuthStore.getState().logout()

    const stateAfterTrigger = useAuthStore.getState()
    expect(stateAfterTrigger.authStatus).toBe('AUTHENTICATED')
    expect(stateAfterTrigger.user).toEqual(activeSessionState.user)
    expect(stateAfterTrigger.isAuthenticated).toBe(true)

    resolveLogoutRequest?.()
    await expect(logoutPromise).resolves.toMatchObject({ outcome: 'SUCCESS' })

    const state = useAuthStore.getState()
    expect(state.authStatus).toBe('ANONYMOUS')
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
    expect(state.sessionExpiresAt).toBeNull()
  })

  it('logout Should not throw an error when the backend fails and retain the current login status', async () => {
    const activeSessionState = createActiveSessionState()
    useAuthStore.setState(activeSessionState)
    vi.mocked(apiLogout).mockRejectedValueOnce(new Error('logout failed'))

    await expect(useAuthStore.getState().logout()).resolves.toMatchObject({ outcome: 'NETWORK_ERROR' })

    const state = useAuthStore.getState()
    expect(state.authStatus).toBe('AUTHENTICATED')
    expect(state.user).toEqual(activeSessionState.user)
    expect(state.isAuthenticated).toBe(true)
    expect(state.sessionExpiresAt).toBe(activeSessionState.sessionExpiresAt)
    expect(state.error).toBe('logout failed')
  })

  it('logout Return 401 When the session expires, it should be processed and the local session should be cleared.', async () => {
    const activeSessionState = createActiveSessionState()
    useAuthStore.setState(activeSessionState)
    const unauthorizedError = Object.assign(new Error('unauthorized'), {
      status: 401,
      code: 401
    })
    vi.mocked(apiLogout).mockRejectedValueOnce(unauthorizedError)

    await expect(useAuthStore.getState().logout()).resolves.toMatchObject({ outcome: 'ALREADY_EXPIRED' })

    const state = useAuthStore.getState()
    expect(state.authStatus).toBe('ANONYMOUS')
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
    expect(state.sessionExpiresAt).toBeNull()
  })

  it('logout The current login status should be retained when returning the business failure code.', async () => {
    const activeSessionState = createActiveSessionState()
    useAuthStore.setState(activeSessionState)
    vi.mocked(apiLogout).mockResolvedValueOnce({ code: 500, data: 'failed' })

    await expect(useAuthStore.getState().logout()).resolves.toMatchObject({ outcome: 'SERVER_ERROR' })

    const state = useAuthStore.getState()
    expect(state.authStatus).toBe('AUTHENTICATED')
    expect(state.user).toEqual(activeSessionState.user)
    expect(state.isAuthenticated).toBe(true)
    expect(state.sessionExpiresAt).toBe(activeSessionState.sessionExpiresAt)
  })

  it('concurrent trigger logout The backend exit should only be requested once', async () => {
    const activeSessionState = createActiveSessionState()
    useAuthStore.setState(activeSessionState)

    let resolveLogoutRequest: (() => void) | null = null
    vi.mocked(apiLogout).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveLogoutRequest = () => resolve({ code: 0, data: 'ok' })
      }),
    )

    const firstLogout = useAuthStore.getState().logout()
    const secondLogout = useAuthStore.getState().logout()

    expect(apiLogout).toHaveBeenCalledTimes(1)
    resolveLogoutRequest?.()
    await Promise.all([firstLogout, secondLogout])

    const state = useAuthStore.getState()
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
  })
})
