import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores'

/**
 * 受保护路由：只有登录后才能访问子路由
 *
 * 使用方式：
 * - 在路由配置中将需要保护的路由作为此组件的子路由
 * - 未登录用户会被重定向到登录页
 * - 认证状态验证中时显示空白，避免闪烁
 */
export function PrivateRoute() {
  const location = useLocation()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const loading = useAuthStore((state) => state.loading)

  // 认证状态验证中，返回 null 避免误判重定向
  if (loading) {
    return null
  }

  // 未登录，重定向到登录页
  if (!isAuthenticated) {
    return <Navigate to="/" state={{ from: location }} replace />
  }

  return <Outlet />
}
