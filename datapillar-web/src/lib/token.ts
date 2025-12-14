/**
 * Token 管理工具
 *
 * 由于 token 存储在 HttpOnly Cookie 中，前端无法直接读取
 * 本模块提供以下功能：
 * - 查询 token 状态（通过 API）
 * - 自动刷新 token
 * - 判断登录状态
 */

import axios from 'axios'
import type { WebAdminResponse } from '@/types/webAdmin'
import type { TokenInfo } from '@/types/auth'

/**
 * Token API 客户端
 */
const tokenClient = axios.create({
  baseURL: '/api/auth',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * 从错误中提取错误信息
 */
function extractErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as { response?: { data?: { message?: string } } }
    if (axiosError.response?.data?.message) {
      return axiosError.response.data.message
    }
  }
  if (error instanceof Error) {
    return error.message
  }
  return '未知错误'
}

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
 * Token 过期回调
 */
let tokenExpiredCallback: (() => void) | null = null

/**
 * 获取当前 token 信息
 */
export async function getTokenInfo(): Promise<TokenInfo> {
  try {
    const response = await tokenClient.get<WebAdminResponse<TokenInfo>>('/token-info')
    if (response.data.code !== 'OK') {
      throw new Error(response.data.message || '获取 token 信息失败')
    }
    return response.data.data
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * 刷新 token
 */
export async function refreshToken(): Promise<void> {
  try {
    const response = await tokenClient.post<WebAdminResponse<void>>('/refresh')
    if (response.data.code !== 'OK') {
      throw new Error(response.data.message || 'Token 刷新失败')
    }
  } catch (error) {
    throw new Error(extractErrorMessage(error))
  }
}

/**
 * 判断是否已登录
 */
export async function isAuthenticated(): Promise<boolean> {
  try {
    const tokenInfo = await getTokenInfo()
    return tokenInfo.valid
  } catch {
    return false
  }
}

/**
 * 根据 token 剩余时间安排下次检查
 */
function scheduleNextCheck(remainingSeconds: number): void {
  stopTokenRefresh()

  // 计算下次检查时间：在 token 过期前 5 分钟检查
  // 如果剩余时间已经小于阈值，立即刷新
  const checkAfterMs = Math.max(
    (remainingSeconds - TOKEN_REFRESH_THRESHOLD) * 1000,
    10 * 1000 // 最少 10 秒后检查，避免过于频繁
  )

  refreshTimerId = window.setTimeout(checkAndRefreshToken, checkAfterMs)
}

/**
 * 检查并刷新 token
 */
async function checkAndRefreshToken(): Promise<void> {
  try {
    const tokenInfo = await getTokenInfo()

    if (!tokenInfo.valid) {
      stopTokenRefresh()
      tokenExpiredCallback?.()
      return
    }

    if (tokenInfo.remainingSeconds <= TOKEN_REFRESH_THRESHOLD) {
      await refreshToken()
      // 刷新后重新获取 token 信息，安排下次检查
      const newTokenInfo = await getTokenInfo()
      scheduleNextCheck(newTokenInfo.remainingSeconds)
    } else {
      // 安排下次检查
      scheduleNextCheck(tokenInfo.remainingSeconds)
    }
  } catch {
    stopTokenRefresh()
    tokenExpiredCallback?.()
  }
}

/**
 * 启动 token 自动刷新机制
 *
 * 根据 token 剩余有效期动态安排刷新，而非固定轮询
 * @param onTokenExpired token 过期时的回调
 * @param initialRemainingSeconds 初始剩余秒数（可选，避免重复请求）
 */
export async function startTokenRefresh(
  onTokenExpired?: () => void,
  initialRemainingSeconds?: number
): Promise<void> {
  stopTokenRefresh()
  tokenExpiredCallback = onTokenExpired || null

  try {
    let remainingSeconds = initialRemainingSeconds

    // 如果没有传入初始剩余时间，则查询一次
    if (remainingSeconds === undefined) {
      const tokenInfo = await getTokenInfo()
      if (!tokenInfo.valid) {
        onTokenExpired?.()
        return
      }
      remainingSeconds = tokenInfo.remainingSeconds
    }

    scheduleNextCheck(remainingSeconds)
  } catch {
    onTokenExpired?.()
  }
}

/**
 * 停止 token 自动刷新
 */
export function stopTokenRefresh(): void {
  if (refreshTimerId !== null) {
    clearTimeout(refreshTimerId)
    refreshTimerId = null
  }
  tokenExpiredCallback = null
}
