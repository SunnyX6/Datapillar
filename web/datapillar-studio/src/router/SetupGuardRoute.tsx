import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useSetupStore } from '@/stores'

const SETUP_PATH = '/setup'

function isSetupPath(pathname: string): boolean {
  return pathname === SETUP_PATH
}

/**
 * 全局 Setup 守门路由：任何页面进入前先校验初始化状态。
 */
export function SetupGuardRoute() {
  const location = useLocation()
  const guardStatus = useSetupStore((state) => state.guardStatus)
  const initialized = useSetupStore((state) => state.initialized)
  const refreshSetupStatus = useSetupStore((state) => state.refreshSetupStatus)

  useEffect(() => {
    if (guardStatus !== 'idle') {
      return
    }
    void refreshSetupStatus()
  }, [guardStatus, refreshSetupStatus])

  if (guardStatus === 'idle' || guardStatus === 'checking') {
    return null
  }

  if (guardStatus === 'error') {
    return <Navigate to="/500" replace />
  }

  if (!initialized && !isSetupPath(location.pathname)) {
    return <Navigate to={SETUP_PATH} replace />
  }

  return <Outlet />
}
