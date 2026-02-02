/**
 * 认证状态管理
 *
 * 使用 Zustand 管理认证状态
 * - sessionStorage 持久化（会话级）
 * - token 刷新由请求拦截器处理
 * - 提供登录、登出、初始化认证等方法
 */

import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
import { login as apiLogin, logout as apiLogout, ssoLogin as apiSsoLogin } from '@/lib/api/auth'
import { getTokenInfo } from '@/lib/api/token'
import { setUnauthorizedHandler } from '@/lib/api/client'
import type { SsoLoginRequest, User } from '@/types/auth'

/**
 * 认证状态接口
 */
interface AuthStore {
  user: User | null
  loading: boolean
  error: string | null
  isAuthenticated: boolean

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
  ) => Promise<void>
  loginWithSso: (request: SsoLoginRequest) => Promise<void>
  logout: () => Promise<void>
  initializeAuth: () => Promise<void>
  clearError: () => void
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

      /**
       * 登录
       */
      login: async (username: string, password: string, rememberMe = false, options) => {
        set({ loading: true, error: null })

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
            error: null
          })

        } catch (error) {
          set({
            user: null,
            isAuthenticated: false,
            loading: false,
            error: error instanceof Error ? error.message : '登录失败'
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
            error: null
          })
        } catch (error) {
          set({
            user: null,
            isAuthenticated: false,
            loading: false,
            error: error instanceof Error ? error.message : '登录失败'
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
            error: null
          })
        }
      },

      /**
       * 初始化认证状态
       * 页面加载时调用，验证当前登录状态
       */
      initializeAuth: async () => {
        const currentUser = get().user

        // 如果 sessionStorage 中没有用户信息，直接返回未登录状态
        if (!currentUser) {
          set({
            user: null,
            isAuthenticated: false,
            loading: false
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
              loading: false
            })
          }
        } catch {
          // 验证失败，清除用户信息
          set({
            user: null,
            isAuthenticated: false,
            loading: false
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
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        user: state.user
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
