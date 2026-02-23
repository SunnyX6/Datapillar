import { useEffect } from 'react'
import { useRouteError } from 'react-router-dom'
import { handleAppError, normalizeRouteError } from '@/lib/error-center'

const loadingFallback = (
  <div className="flex min-h-dvh items-center justify-center bg-slate-50 text-slate-500 dark:bg-[#020617]">
    <span className="text-xs tracking-[0.3em] uppercase">Loading...</span>
  </div>
)

/**
 * 路由级错误边界：统一接入全局错误中心，避免路由渲染异常导致白屏。
 */
export function RouteErrorBoundary() {
  const routeError = useRouteError()

  useEffect(() => {
    handleAppError(
      normalizeRouteError(routeError, {
        module: 'router/error-boundary',
        isCoreRequest: true
      })
    )
  }, [routeError])

  return loadingFallback
}
