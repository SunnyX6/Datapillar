/**
 * Token 管理工具
 *
 * 由于 token 存储在 HttpOnly Cookie 中，前端无法直接读取
 * 本模块提供以下功能：
 * - 查询 token 状态（通过 API）
 * - 自动刷新 token
 * - 判断登录状态
 */

import { get, post } from './api'
import type { TokenInfo } from '@/types/auth'

/**
 * Token 刷新阈值（秒）
 * 当 token 剩余时间小于此值时触发自动刷新
 */
const TOKEN_REFRESH_THRESHOLD = 5 * 60

/**
 * Token 刷新定时器 ID
 */
let refreshTimerId: number | null = null

/**
 * 获取当前 token 信息
 */
export async function getTokenInfo(): Promise<TokenInfo> {
  try {
    const response = await get<TokenInfo>('/auth/token-info')
    if (response.code === 'OK') {
      return response.data
    }
    return {
      valid: false,
      remainingSeconds: 0
    }
  } catch {
    return {
      valid: false,
      remainingSeconds: 0
    }
  }
}

/**
 * 刷新 token
 */
export async function refreshToken(): Promise<boolean> {
  try {
    const response = await post<null, void>('/auth/refresh')
    return response.code === 'OK'
  } catch {
    return false
  }
}

/**
 * 判断是否已登录
 */
export async function isAuthenticated(): Promise<boolean> {
  const tokenInfo = await getTokenInfo()
  return tokenInfo.valid
}

/**
 * 启动 token 自动刷新机制
 *
 * 定期检查 token 剩余时间，当剩余时间小于阈值时自动刷新
 */
export function startTokenRefresh(onTokenExpired?: () => void): void {
  stopTokenRefresh()

  const checkAndRefresh = async () => {
    try {
      const tokenInfo = await getTokenInfo()

      if (!tokenInfo.valid) {
        stopTokenRefresh()
        onTokenExpired?.()
        return
      }

      if (tokenInfo.remainingSeconds <= TOKEN_REFRESH_THRESHOLD) {
        const success = await refreshToken()
        if (!success) {
          stopTokenRefresh()
          onTokenExpired?.()
        }
      }
    } catch {
      stopTokenRefresh()
      onTokenExpired?.()
    }
  }

  checkAndRefresh()

  refreshTimerId = window.setInterval(checkAndRefresh, 60 * 1000)
}

/**
 * 停止 token 自动刷新
 */
export function stopTokenRefresh(): void {
  if (refreshTimerId !== null) {
    clearInterval(refreshTimerId)
    refreshTimerId = null
  }
}
