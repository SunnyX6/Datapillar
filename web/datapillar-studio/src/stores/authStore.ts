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
import { login as apiLogin, loginTenant as apiLoginTenant, logout as apiLogout, ssoLogin as apiSsoLogin } from '@/lib/api/auth'
import { getTokenInfo } from '@/lib/api/token'
import { setUnauthorizedHandler } from '@/lib/api/client'
import type { LoginResult, SsoLoginRequest, User } from '@/types/auth'

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
      inviteCode?: string
      email?: string
      phone?: string
    }
  ) => Promise<LoginResult>
  loginWithSso: (request: SsoLoginRequest) => Promise<LoginResult>
  loginTenant: (loginToken: string, tenantId: number) => Promise<LoginResult>
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
            username,
            password,
            rememberMe,
            tenantCode: options?.tenantCode,
            inviteCode: options?.inviteCode,
            email: options?.email,
            phone: options?.phone
          })

          if (response.loginStage === 'SUCCESS') {
            const sessionExpiresAt = buildSessionExpiresAt(rememberMe)
            const user: User = {
              userId: response.userId,
              tenantId: response.tenantId,
              username: response.username,
              email: response.email,
              roles: response.roles,
              menus: response.menus
            }

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
      loginWithSso: async (request: SsoLoginRequest) => {
        set({ loading: true, error: null })

        try {
          const response = await apiSsoLogin(request)

          if (response.loginStage === 'SUCCESS') {
            const sessionExpiresAt = buildSessionExpiresAt(false)
            const user: User = {
              userId: response.userId,
              tenantId: response.tenantId,
              username: response.username,
              email: response.email,
              roles: response.roles,
              menus: response.menus
            }

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
      loginTenant: async (loginToken: string, tenantId: number) => {
        set({ loading: true, error: null })

        try {
          const rememberMe = get().pendingRememberMe ?? false
          const response = await apiLoginTenant({ loginToken, tenantId })
          if (response.loginStage !== 'SUCCESS') {
            throw new Error('租户选择未完成')
          }

          const sessionExpiresAt = buildSessionExpiresAt(rememberMe)
          const user: User = {
            userId: response.userId,
            tenantId: response.tenantId,
            username: response.username,
            email: response.email,
            roles: response.roles,
            menus: response.menus
          }

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
       * 页面加载时调用，验证当前登录状态
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

        // 有用户信息，验证 token 是否有效
        set({ loading: true })

        try {
          const tokenInfo = await getTokenInfo()

          if (tokenInfo.valid) {
            set({ isAuthenticated: true, loading: false })

          } else {
            // Token 无效，清除用户信息
            set({
              user: null,
              isAuthenticated: false,
              loading: false,
              sessionExpiresAt: null,
              pendingRememberMe: null
            })
          }
        } catch {
          // 验证失败，清除用户信息
          set({
            user: null,
            isAuthenticated: false,
            loading: false,
            sessionExpiresAt: null,
            pendingRememberMe: null
          })
        }
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
        return (state) => {
          // 数据恢复完成后，验证认证状态
          if (state) {
            state.initializeAuth()
          }
        }
      }
    }
  )
)

setUnauthorizedHandler(() => {
  const { logout } = useAuthStore.getState()
  void logout()
})
