import { useNavigate } from 'react-router-dom'
import { ReactNode, useCallback } from 'react'
import { useAuthStore } from '@/stores'
import { AuthContext, type AuthContextType } from './auth-context'

interface AuthProviderProps {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const navigate = useNavigate()
  const authUser = useAuthStore(state => state.user)
  const loading = useAuthStore(state => state.loading)
  const logoutAction = useAuthStore(state => state.logout)
  const initializeAuth = useAuthStore(state => state.initializeAuth)

  // 从 Zustand 状态派生本地状态
  const user = authUser ? {
    id: authUser.id,
    name: authUser.name,
    email: undefined
  } : null
  const token = authUser ? 'authenticated' : null
  const isLoading = loading

  // 登录函数（保持接口兼容性）
  const login = async () => {
    // 实际登录通过 LoginPage 中的 authStore.login() 处理
  }

  // 登出函数
  const logout = useCallback(async () => {
    await logoutAction()

    // 发送清理数据源状态的事件
    window.dispatchEvent(new CustomEvent('auth:logout'))

    navigate('/login')
  }, [logoutAction, navigate])

  // 刷新认证状态
  const refreshAuth = useCallback(async () => {
    try {
      await initializeAuth()
    } catch {
      // initializeAuth 失败，Zustand 状态已更新
    }
  }, [initializeAuth])

  const value: AuthContextType = {
    user,
    token,
    isLoading,
    isAuthenticated: !!user,
    login,
    logout,
    refreshAuth
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

// 自定义 hook 来使用认证上下文
