/**
 * 认证相关的 TypeScript 接口定义
 */
import type { ApiResponse } from './api'

/**
 * 菜单项接口
 */
export interface Menu {
  id: number
  name: string
  path: string
  icon?: string
  permissionCode?: string
  children?: Menu[]
}

/**
 * 用户信息接口
 */
export interface User {
  userId: number
  username: string
  email?: string
  avatar?: string
  roles: string[]
  permissions: string[]
  menus: Menu[]
}

/**
 * 登录请求接口
 */
export interface LoginRequest {
  username: string
  password: string
  rememberMe?: boolean
}

/**
 * 登录响应接口（后端实际返回的数据结构）
 */
export interface LoginResponse {
  userId: number
  username: string
  email?: string
  roles: string[]
  permissions: string[]
  menus: Menu[]
}

/**
 * Token 信息接口
 */
export interface TokenInfo {
  valid: boolean
  remainingSeconds: number
  expirationTime?: number
  issuedAt?: number
  userId?: number
  username?: string
}

/**
 * 注册请求接口
 */
export interface RegisterRequest {
  username: string
  password: string
  email?: string
  confirmPassword: string
}

/**
 * 修改密码请求接口
 */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

/**
 * 重置密码请求接口
 */
export interface ResetPasswordRequest {
  email: string
}

/**
 * 认证状态接口
 */
export interface AuthState {
  isAuthenticated: boolean
  user: User | null
  loading: boolean
  error: string | null
}

/**
 * 认证错误类型
 */
export type AuthErrorType =
  | 'INVALID_CREDENTIALS'
  | 'USER_NOT_FOUND'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_INVALID'
  | 'NETWORK_ERROR'
  | 'SERVER_ERROR'
  | 'VALIDATION_ERROR'
  | 'UNKNOWN_ERROR'

/**
 * 认证错误接口
 */
export interface AuthError {
  type: AuthErrorType
  message: string
  details?: unknown
}

/**
 * 统一的认证响应类型
 */
export type LoginApiResponse = ApiResponse<LoginResponse>
export type TokenInfoApiResponse = ApiResponse<TokenInfo>
