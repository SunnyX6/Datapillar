/**
 * 认证状态管理
 *
 * 使用 Zustand 管理认证状态
 * - localStorage 持久化（7天/30天）
 * - token 刷新由请求拦截器处理
 * - 提供登录、登出、初始化认证等方法
 */

import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import {
  login as apiLogin,
  loginSso as apiLoginSso,
  loginTenant as apiLoginTenant,
  logout as apiLogout
} from '@/services/authService'
import { setAuthLifecycleCallbacks } from '@/lib/api'
import {
  isTenantSelectResult,
  type LoginResult,
  type LoginSuccess,
  type SsoLoginRequest,
  type TenantOption,
  type User
} from '@/types/auth'
import { isApiResponse, type ApiError } from '@/types/api'

type SsoLoginPayload = SsoLoginRequest
type AuthStatus = 'ANONYMOUS' | 'AUTHENTICATED' | 'REFRESHING' | 'EXPIRED'
type LogoutOutcome = 'SUCCESS' | 'ALREADY_EXPIRED' | 'SERVER_ERROR' | 'NETWORK_ERROR'

interface LogoutResult {
  outcome: LogoutOutcome
  status?: number
  message?: string
}

/**
 * 认证状态接口
 */
interface AuthStore {
  authStatus: AuthStatus
  user: User | null
  loading: boolean
  error: string | null
  isAuthenticated: boolean
  sessionExpiresAt: number | null
  pendingRememberMe: boolean | null
  lastUsername: string | null
  lastRememberMe: boolean

  login: (
    username: string,
    password: string,
    rememberMe?: boolean,
    options?: {
      tenantCode?: string
    }
  ) => Promise<LoginResult>
  loginWithSso: (request: SsoLoginPayload) => Promise<LoginResult>
  loginTenant: (tenantId: number) => Promise<LoginResult>
  logout: () => Promise<LogoutResult>
  expireSession: (message?: string) => void
  setRefreshing: (active: boolean) => void
  initializeAuth: () => Promise<void>
  clearError: () => void
}

const DAY_MS = 24 * 60 * 60 * 1000
const SESSION_EXPIRES_MS = {
  default: 7 * DAY_MS,
  remember: 30 * DAY_MS
}
let logoutPromise: Promise<LogoutResult> | null = null

function resolveLogoutError(error: unknown): LogoutResult {
  const apiError = error as ApiError
  const status = typeof apiError?.status === 'number' ? apiError.status : undefined
  const code = apiError?.code
  const message = apiError instanceof Error ? apiError.message : '退出登录失败'
  const codeAsNumber = typeof code === 'number' ? code : undefined
  if (status === 401 || codeAsNumber === 401) {
    return { outcome: 'ALREADY_EXPIRED', status: 401, message }
  }
  if ((status !== undefined && status >= 500) || (codeAsNumber !== undefined && codeAsNumber >= 500)) {
    return { outcome: 'SERVER_ERROR', status: status ?? codeAsNumber, message }
  }
  return { outcome: 'NETWORK_ERROR', status, message }
}

function resolveLogoutEnvelopeFailure(code: unknown, message: string): LogoutResult {
  const status = typeof code === 'number' ? code : undefined
  if (status === 401) {
    return { outcome: 'ALREADY_EXPIRED', status, message }
  }
  if (status !== undefined && status >= 500) {
    return { outcome: 'SERVER_ERROR', status, message }
  }
  return { outcome: 'NETWORK_ERROR', status, message }
}

const buildSessionExpiresAt = (rememberMe?: boolean) => {
  const ttl = rememberMe ? SESSION_EXPIRES_MS.remember : SESSION_EXPIRES_MS.default
  return Date.now() + ttl
}

function resolveCurrentTenant(tenants: TenantOption[]): TenantOption | undefined {
  if (!Array.isArray(tenants) || tenants.length === 0) {
    return undefined
  }

  const defaultTenant = tenants.find((tenant) => tenant.isDefault === 1)
  return defaultTenant ?? tenants[0]
}

function buildUser(response: LoginSuccess): User {
  const currentTenant = resolveCurrentTenant(response.tenants)

  return {
    userId: response.userId,
    tenantId: currentTenant?.tenantId,
    tenantCode: currentTenant?.tenantCode,
    tenantName: currentTenant?.tenantName,
    tenants: response.tenants,
    username: response.username,
    email: response.email,
    roles: response.roles,
    menus: response.menus
  }
}

/**
 * 创建认证状态 Store
 */
export const useAuthStore = create<AuthStore>()(
  persist(
    (set, get) => ({
      authStatus: 'ANONYMOUS',
      user: null,
      loading: true,
      error: null,
      isAuthenticated: false,
      sessionExpiresAt: null,
      pendingRememberMe: null,
      lastUsername: null,
      lastRememberMe: false,

      /**
       * 登录
       */
      login: async (username: string, password: string, rememberMe = false, options) => {
        const normalizedUsername = username.trim()
        set({
          loading: true,
          error: null,
          lastUsername: normalizedUsername.length > 0 ? normalizedUsername : null,
          lastRememberMe: rememberMe
        })

        try {
          const response = await apiLogin({
            loginAlias: normalizedUsername,
            password,
            rememberMe,
            tenantCode: options?.tenantCode?.trim() || undefined
          })

          if (!isTenantSelectResult(response)) {
            const sessionExpiresAt = buildSessionExpiresAt(rememberMe)
            const user = buildUser(response)

            set({
              authStatus: 'AUTHENTICATED',
              user,
              isAuthenticated: true,
              loading: false,
              error: null,
              sessionExpiresAt,
              pendingRememberMe: null,
              lastUsername: normalizedUsername.length > 0 ? normalizedUsername : null,
              lastRememberMe: rememberMe
            })
          } else {
            set({
              authStatus: 'ANONYMOUS',
              user: null,
              isAuthenticated: false,
              loading: false,
              error: null,
              sessionExpiresAt: null,
              pendingRememberMe: rememberMe,
              lastUsername: normalizedUsername.length > 0 ? normalizedUsername : null,
              lastRememberMe: rememberMe
            })
          }

          return response
        } catch (error) {
          set({
            authStatus: 'ANONYMOUS',
            user: null,
            isAuthenticated: false,
            loading: false,
            error: error instanceof Error ? error.message : '登录失败',
            sessionExpiresAt: null,
            pendingRememberMe: null,
            lastUsername: normalizedUsername.length > 0 ? normalizedUsername : null,
            lastRememberMe: rememberMe
          })
          throw error
        }
      },

      /**
       * SSO 登录
       */
      loginWithSso: async (request: SsoLoginPayload) => {
        set({ loading: true, error: null })

        try {
          const response = await apiLoginSso(request)

          if (!isTenantSelectResult(response)) {
            const sessionExpiresAt = buildSessionExpiresAt(false)
            const user = buildUser(response)

            set({
              authStatus: 'AUTHENTICATED',
              user,
              isAuthenticated: true,
              loading: false,
              error: null,
              sessionExpiresAt,
              pendingRememberMe: null
            })
          } else {
            set({
              authStatus: 'ANONYMOUS',
              user: null,
              isAuthenticated: false,
              loading: false,
              error: null,
              sessionExpiresAt: null,
              pendingRememberMe: null
            })
          }

          return response
        } catch (error) {
          set({
            authStatus: 'ANONYMOUS',
            user: null,
            isAuthenticated: false,
            loading: false,
            error: error instanceof Error ? error.message : '登录失败',
            sessionExpiresAt: null,
            pendingRememberMe: null
          })
          throw error
        }
      },

      /**
       * 选择租户完成登录
       */
      loginTenant: async (tenantId: number) => {
        set({ loading: true, error: null })

        try {
          const rememberMe = get().pendingRememberMe ?? false
          const response = await apiLoginTenant({ tenantId })
          if (isTenantSelectResult(response)) {
            throw new Error('租户选择未完成')
          }

          const sessionExpiresAt = buildSessionExpiresAt(rememberMe)
          const user = buildUser(response)

          set({
            authStatus: 'AUTHENTICATED',
            user,
            isAuthenticated: true,
            loading: false,
            error: null,
            sessionExpiresAt,
            pendingRememberMe: null,
            lastRememberMe: rememberMe
          })

          return response
        } catch (error) {
          set({
            authStatus: 'ANONYMOUS',
            user: null,
            isAuthenticated: false,
            loading: false,
            error: error instanceof Error ? error.message : '登录失败',
            sessionExpiresAt: null,
            pendingRememberMe: null
          })
          throw error
        }
      },

      /**
       * 登出
       */
      logout: async () => {
        if (logoutPromise) {
          return logoutPromise
        }

        const currentState = get()
        if (!currentState.isAuthenticated) {
          set({
            authStatus: 'ANONYMOUS',
            user: null,
            isAuthenticated: false,
            loading: false,
            error: null,
            sessionExpiresAt: null,
            pendingRememberMe: null
          })
          return { outcome: 'ALREADY_EXPIRED', status: 401 } as LogoutResult
        }

        const sessionSnapshot = {
          user: currentState.user,
          isAuthenticated: currentState.isAuthenticated,
          sessionExpiresAt: currentState.sessionExpiresAt,
          pendingRememberMe: currentState.pendingRememberMe
        }

        set({
          loading: false,
          error: null,
        })

        logoutPromise = (async () => {
          let result: LogoutResult = { outcome: 'SUCCESS' }
          try {
            const payload = await apiLogout()
            if (!isApiResponse(payload) || payload.code !== 0) {
              result = resolveLogoutEnvelopeFailure(payload.code, '退出登录失败')
            }
          } catch (error) {
            result = resolveLogoutError(error)
          } finally {
            if (result.outcome === 'SUCCESS' || result.outcome === 'ALREADY_EXPIRED') {
              set({
                authStatus: 'ANONYMOUS',
                user: null,
                isAuthenticated: false,
                loading: false,
                error: null,
                sessionExpiresAt: null,
                pendingRememberMe: null
              })
            } else {
              set({
                authStatus: sessionSnapshot.isAuthenticated ? 'AUTHENTICATED' : 'ANONYMOUS',
                user: sessionSnapshot.user,
                isAuthenticated: sessionSnapshot.isAuthenticated,
                loading: false,
                error: result.message ?? '退出登录失败',
                sessionExpiresAt: sessionSnapshot.sessionExpiresAt,
                pendingRememberMe: sessionSnapshot.pendingRememberMe
              })
            }
            logoutPromise = null
          }

          return result
        })()

        return logoutPromise
      },

      expireSession: (message = '登录状态已失效，请重新登录') => {
        const currentState = get()
        if (!currentState.isAuthenticated) {
          return
        }
        set({
          authStatus: 'EXPIRED',
          user: null,
          isAuthenticated: false,
          loading: false,
          error: message,
          sessionExpiresAt: null,
          pendingRememberMe: null
        })
      },

      setRefreshing: (active: boolean) => {
        const currentState = get()
        if (active) {
          if (currentState.authStatus === 'AUTHENTICATED') {
            set({ authStatus: 'REFRESHING' })
          }
          return
        }
        if (currentState.authStatus === 'REFRESHING') {
          set({ authStatus: currentState.isAuthenticated ? 'AUTHENTICATED' : 'ANONYMOUS' })
        }
      },

      /**
       * 初始化认证状态
       * 页面加载时调用，仅根据本地会话状态恢复
       */
      initializeAuth: async () => {
        const { user: currentUser, sessionExpiresAt } = get()
        const now = Date.now()

        // 如果本地持久化中没有用户信息或已过期，直接返回未登录状态
        if (!currentUser || !sessionExpiresAt || sessionExpiresAt <= now) {
          set({
            authStatus: 'ANONYMOUS',
            user: null,
            isAuthenticated: false,
            loading: false,
            sessionExpiresAt: null,
            pendingRememberMe: null
          })
          return
        }
        set({
          authStatus: 'AUTHENTICATED',
          isAuthenticated: true,
          loading: false,
          error: null
        })
      },

      /**
       * 清除错误信息
       */
      clearError: () => {
        set({ error: null })
      }
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        authStatus: state.authStatus,
        user: state.user,
        sessionExpiresAt: state.sessionExpiresAt,
        lastUsername: state.lastUsername,
        lastRememberMe: state.lastRememberMe
      }),
      onRehydrateStorage: () => {
        return (state) => {
          if (!state) {
            return
          }
          const hasSession = !!state.user && !!state.sessionExpiresAt
          if (!hasSession) {
            state.authStatus = 'ANONYMOUS'
            return
          }
          state.authStatus = 'AUTHENTICATED'
        }
      }
    }
  )
)

setAuthLifecycleCallbacks({
  canRefresh: () => {
    const { authStatus, isAuthenticated } = useAuthStore.getState()
    if (!isAuthenticated) {
      return false
    }
    return authStatus === 'AUTHENTICATED' || authStatus === 'REFRESHING'
  },
  onRefreshStart: () => {
    useAuthStore.getState().setRefreshing(true)
  },
  onRefreshEnd: () => {
    useAuthStore.getState().setRefreshing(false)
  },
  onSessionExpired: () => {
    useAuthStore.getState().expireSession()
  }
})
