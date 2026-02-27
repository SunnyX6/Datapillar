import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { useSetupStore } from '@/state'

const SETUP_PATH = '/setup'
const SERVER_ERROR_PATH = '/500'

/**
 * 初始化守卫：仅负责 setup 状态检查。
 */
export function SetupGate() {
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
    return <Navigate to={SERVER_ERROR_PATH} replace />
  }

  if (!initialized) {
    return <Navigate to={SETUP_PATH} replace />
  }

  return <Outlet />
}
