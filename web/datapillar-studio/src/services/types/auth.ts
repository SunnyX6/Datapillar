/**
 * Certification related TypeScript Interface definition
 */
import type { ApiResponse } from '@/api/types/api'

/**
 * menu item interface
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
 * User information interface
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
 * Password login request interface
 */
export interface PasswordLoginRequest {
  tenantCode?: string
  loginAlias: string
  password: string
  rememberMe?: boolean
}

/**
 * Login successful response interface
 */
export interface LoginSuccess {
  /** Successful login scenarios usually do not return loginStage */
  loginStage?: string
  userId: number
  username: string
  email?: string
  tenants: TenantOption[]
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
 * Token Information interface
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
 * Registration request interface
 */
export interface RegisterRequest {
  username: string
  password: string
  email?: string
  confirmPassword: string
}

/**
 * Password change request interface
 */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

/**
 * Reset password request interface
 */
export interface ResetPasswordRequest {
  email: string
}

/**
 * Authentication status interface
 */
export interface AuthState {
  isAuthenticated: boolean
  user: User | null
  loading: boolean
  error: string | null
}

/**
 * Authentication error type
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
 * Authentication error interface
 */
export interface AuthError {
  type: AuthErrorType
  message: string
  details?: unknown
}

/**
 * Unified authentication response type
 */
export type LoginApiResponse = ApiResponse<LoginResult>
export type TokenInfoApiResponse = ApiResponse<TokenInfo>
