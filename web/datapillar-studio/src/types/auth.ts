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
  permissionCode?: string
  location?: 'TOP' | 'SIDEBAR' | 'PROFILE' | 'PAGE'
  categoryId?: number
  categoryName?: string
  children?: Menu[]
}

export interface RoleInfo {
  id: number
  name: string
  type: 'ADMIN' | 'USER'
}

/**
 * 用户信息接口
 */
export interface User {
  userId: number
  tenantId?: number
  tenantCode?: string
  tenantName?: string
  tenants?: TenantOption[]
  username: string
  email?: string
  avatar?: string
  roles: RoleInfo[]
  menus: Menu[]
}

/**
 * 密码登录请求接口
 */
export interface PasswordLoginRequest {
  tenantCode?: string
  loginAlias: string
  password: string
  rememberMe?: boolean
}

/**
 * 登录成功响应接口
 */
export interface LoginSuccess {
  /** 成功登录场景通常不回传 loginStage */
  loginStage?: string
  userId: number
  username: string
  email?: string
  tenants: TenantOption[]
  roles: RoleInfo[]
  menus: Menu[]
}

export interface TenantOption {
  tenantId: number
  tenantCode: string
  tenantName: string
  status: number
  isDefault?: number
}

export interface TenantSelectResult {
  loginStage: 'TENANT_SELECT'
  tenants: TenantOption[]
}

export type LoginResult = LoginSuccess | TenantSelectResult

export function isTenantSelectResult(result: LoginResult): result is TenantSelectResult {
  return result.loginStage === 'TENANT_SELECT'
}

export interface LoginTenantRequest {
  tenantId: number
}

export interface SsoLoginRequest {
  provider: string
  code: string
  state: string
  rememberMe?: boolean
  tenantCode?: string
}

export type LoginRequest = PasswordLoginRequest | SsoLoginRequest

/**
 * Token 信息接口
 */
export interface TokenInfo {
  remainingSeconds: number
  expirationTime?: number
  issuedAt?: number
  userId?: number
  tenantId?: number
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
  | 'INTERNAL_ERROR'
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
export type LoginApiResponse = ApiResponse<LoginResult>
export type TokenInfoApiResponse = ApiResponse<TokenInfo>
