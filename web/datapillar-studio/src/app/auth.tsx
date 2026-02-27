import { useEffect } from 'react'
import { useAuthStore } from '@/state'

/**
 * 认证初始化：应用启动时恢复会话状态。
 */
export function useAuthBootstrap() {
  useEffect(() => {
    void useAuthStore.getState().initializeAuth()
  }, [])
}
