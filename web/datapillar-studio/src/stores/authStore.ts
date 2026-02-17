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
} from '@/lib/api/auth'
import { setUnauthorizedHandler } from '@/lib/api/client'
import {
  isTenantSelectResult,
  type LoginResult,
  type LoginSuccess,
  type SsoLoginRequest,
  type TenantOption,
  type User
} from '@/types/auth'

type SsoLoginPayload = SsoLoginRequest

/**
 * 认证状态接口
 */
interface AuthStore {
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
  logout: () => Promise<void>
  initializeAuth: () => Promise<void>
  clearError: () => void
}

const DAY_MS = 24 * 60 * 60 * 1000
const SESSION_EXPIRES_MS = {
  default: 7 * DAY_MS,
  remember: 30 * DAY_MS
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
      user: null,
      loading: false,
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
              user,
              isAuthenticated: true,
              loading: false,
              error: null,
              sessionExpiresAt,
              pendingRememberMe: null
            })
          } else {
            set({
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
        try {
          await apiLogout()
        } finally {
          set({
            user: null,
            isAuthenticated: false,
            loading: false,
            error: null,
            sessionExpiresAt: null,
            pendingRememberMe: null
          })
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
            user: null,
            isAuthenticated: false,
            loading: false,
            sessionExpiresAt: null,
            pendingRememberMe: null
          })
          return
        }
        set({
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
        user: state.user,
        sessionExpiresAt: state.sessionExpiresAt,
        lastUsername: state.lastUsername,
        lastRememberMe: state.lastRememberMe
      }),
      onRehydrateStorage: () => {
        return () => {
          // 认证校验由入口路由统一触发，避免启动阶段并发请求打乱初始化分流。
        }
      }
    }
  )
)

setUnauthorizedHandler(() => {
  const { logout } = useAuthStore.getState()
  void logout()
})
