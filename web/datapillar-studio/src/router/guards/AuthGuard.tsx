import { Navigate, Outlet } from 'react-router-dom'
import { useAuthStore } from '@/stores'

const LOGIN_PATH = '/login'

/**
 * 认证守卫：仅负责登录态判断。
 */
export function AuthGuard() {
  const loading = useAuthStore((state) => state.loading)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)

  if (loading) {
    return null
  }

  if (!isAuthenticated) {
    return <Navigate to={LOGIN_PATH} replace />
  }

  return <Outlet />
}
