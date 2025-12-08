/**
 * ¤Á API
 *
 * Ð›{U{úI¤Áøs„ API (
 */

import { post } from './api'
import type { LoginRequest, LoginResponse } from '@/types/auth'

/**
 * (7{U
 *
 * @param request {U÷BÂp
 * @returns {UÍ”pn
 */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await post<LoginResponse>('/auth/login', request)

  if (response.code !== 'OK') {
    throw new Error(response.message || '{U1%')
  }

  return response.data
}

/**
 * (7{ú
 */
export async function logout(): Promise<void> {
  try {
    await post<string>('/auth/logout')
  } catch (error) {
    console.error('{ú1%:', error)
  }
}
