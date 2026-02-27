import { useEffect } from 'react'
import { useThemeStore } from '@/state'

/**
 * 主题初始化：应用启动时同步主题到 DOM。
 */
export function useThemeBootstrap() {
  useEffect(() => {
    useThemeStore.getState().initialize()
  }, [])
}
