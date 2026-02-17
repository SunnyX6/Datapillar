import axios, { AxiosHeaders, type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import {
  isApiResponse as isApiDataResponse,
  isApiSuccess,
  isErrorResponse as isApiErrorResponse,
  type ApiResponse,
  type ApiError,
  type ErrorResponse
} from '@/types/api'

interface ApiClientOptions {
  baseURL: string
  timeout?: number
  validateResponse?: boolean
}

type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean }

const AUTH_BASE_URL = '/api/auth'
const LOGIN_BASE_URL = '/api/login'
const CSRF_COOKIE_NAME = 'csrf-token'
const CSRF_HEADER_NAME = 'X-CSRF-Token'
const REFRESH_CSRF_COOKIE_NAME = 'refresh-csrf-token'
const REFRESH_CSRF_HEADER_NAME = 'X-Refresh-CSRF-Token'
const AUTH_ENDPOINTS = {
  loginBase: LOGIN_BASE_URL,
  logout: `${LOGIN_BASE_URL}/logout`,
  refresh: `${AUTH_BASE_URL}/refresh`
}
const SETUP_REDIRECT_PATH = '/setup'
const SETUP_ERROR_MESSAGES = new Set(['系统尚未完成初始化', '系统初始化数据未就绪'])

const refreshClient = axios.create({
  baseURL: AUTH_BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

function getCookieValue(name: string): string | null {
  if (typeof document === 'undefined') {
    return null
  }
  const cookies = document.cookie ? document.cookie.split('; ') : []
  for (const cookie of cookies) {
    const [key, ...rest] = cookie.split('=')
    if (key === name) {
      return decodeURIComponent(rest.join('='))
    }
  }
  return null
}

function attachCsrfHeader(
  headers: AxiosHeaders,
  cookieName: string = CSRF_COOKIE_NAME,
  headerName: string = CSRF_HEADER_NAME
): void {
  const token = getCookieValue(cookieName)
  if (!token) {
    return
  }
  if (!headers.has(headerName)) {
    headers.set(headerName, token)
  }
}

refreshClient.interceptors.request.use((config) => {
  const headers = AxiosHeaders.from(config.headers)
  attachCsrfHeader(headers, REFRESH_CSRF_COOKIE_NAME, REFRESH_CSRF_HEADER_NAME)
  config.headers = headers
  return config
})

let refreshPromise: Promise<void> | null = null
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

function isStandardApiResponse(data: unknown): data is ApiResponse<unknown> {
  return isApiDataResponse(data)
}

function isStandardErrorResponse(data: unknown): data is ErrorResponse {
  return isApiErrorResponse(data)
}

function buildApiError(data: ApiResponse<unknown> | ErrorResponse, status?: number): ApiError {
  const message = isStandardErrorResponse(data) ? data.message : `Request failed (code: ${data.code})`
  const error = new Error(message) as ApiError
  error.code = data.code
  error.status = status
  error.response = data
  return error
}

function shouldRedirectToSetup(message: unknown): boolean {
  return typeof message === 'string' && SETUP_ERROR_MESSAGES.has(message)
}

function redirectToSetup(): void {
  if (typeof window === 'undefined') {
    return
  }
  if (window.location.pathname === SETUP_REDIRECT_PATH) {
    return
  }
  window.location.replace(SETUP_REDIRECT_PATH)
}

function extractErrorMessage(data: unknown): string | null {
  if (!data || typeof data !== 'object') {
    return null
  }
  const candidate = data as { message?: unknown }
  return typeof candidate.message === 'string' ? candidate.message : null
}

function handleSetupApiResponse(data: unknown): boolean {
  const message = extractErrorMessage(data)
  if (!shouldRedirectToSetup(message)) {
    return false
  }
  redirectToSetup()
  return true
}

function handleSetupError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false
  }

  const message = extractErrorMessage(error)
  if (shouldRedirectToSetup(message)) {
    redirectToSetup()
    return true
  }

  if (axios.isAxiosError(error)) {
    return handleSetupApiResponse(error.response?.data)
  }

  return false
}

function getRequestUrl(config: InternalAxiosRequestConfig): string {
  const baseURL = config.baseURL ?? ''
  const url = config.url ?? ''
  return `${baseURL}${url}`
}

function shouldSkipAuthHandling(config: InternalAxiosRequestConfig): boolean {
  const requestUrl = getRequestUrl(config)
  return requestUrl.startsWith(AUTH_ENDPOINTS.loginBase) ||
    requestUrl.startsWith(AUTH_ENDPOINTS.logout) ||
    requestUrl.startsWith(AUTH_ENDPOINTS.refresh)
}

function getFetchUrl(input: RequestInfo | URL): string {
  if (typeof input === 'string') {
    return input
  }
  if (input instanceof URL) {
    return input.toString()
  }
  return input.url
}

function normalizePath(url: string): string {
  try {
    return new URL(url, 'http://localhost').pathname
  } catch {
    return url
  }
}

function shouldSkipAuthHandlingForUrl(url: string): boolean {
  const path = normalizePath(url)
  return path.startsWith(AUTH_ENDPOINTS.loginBase) ||
    path.startsWith(AUTH_ENDPOINTS.logout) ||
    path.startsWith(AUTH_ENDPOINTS.refresh)
}

async function refreshAccessToken(): Promise<void> {
  if (!refreshPromise) {
    refreshPromise = refreshClient
      .post<ApiResponse<void> | ErrorResponse>('/refresh')
      .then((response) => {
        const data = response.data
        if (isStandardErrorResponse(data)) {
          handleSetupApiResponse(data)
          throw buildApiError(data, response.status)
        }
        if (!isStandardApiResponse(data)) {
          throw new Error('Invalid API response')
        }
        if (!isApiSuccess(data)) {
          handleSetupApiResponse(data)
          throw buildApiError(data, response.status)
        }
      })
      .catch((error: unknown) => {
        const responseData = axios.isAxiosError(error) ? error.response?.data : undefined
        if (
          handleSetupApiResponse(responseData) &&
          (isStandardApiResponse(responseData) || isStandardErrorResponse(responseData))
        ) {
          throw buildApiError(responseData, axios.isAxiosError(error) ? error.response?.status : undefined)
        }
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

function shouldAttemptRefresh(error: AxiosError): boolean {
  const status = error.response?.status
  if (status !== 401) {
    return false
  }

  const config = error.config as RetryConfig | undefined
  if (!config || config._retry) {
    return false
  }

  if (shouldSkipAuthHandling(config)) {
    return false
  }

  return true
}

export function createApiClient(options: ApiClientOptions): AxiosInstance {
  const validateResponse = options.validateResponse !== false
  const client = axios.create({
    baseURL: options.baseURL,
    timeout: options.timeout ?? 30000,
    headers: { 'Content-Type': 'application/json' }
  })

  client.interceptors.request.use((config) => {
    const headers = AxiosHeaders.from(config.headers)
    attachCsrfHeader(headers)
    config.headers = headers
    return config
  })

  client.interceptors.response.use(
    (response) => {
      if (!validateResponse) {
        return response
      }
      const data = response.data
      if (isStandardErrorResponse(data)) {
        handleSetupApiResponse(data)
        return Promise.reject(buildApiError(data, response.status))
      }
      if (!isStandardApiResponse(data)) {
        return Promise.reject(new Error('Invalid API response'))
      }
      if (!isApiSuccess(data)) {
        handleSetupApiResponse(data)
        return Promise.reject(buildApiError(data, response.status))
      }
      return response
    },
    async (error: AxiosError) => {
      const responseData = error.response?.data
      if (handleSetupApiResponse(responseData)) {
        if (isStandardApiResponse(responseData) || isStandardErrorResponse(responseData)) {
          return Promise.reject(buildApiError(responseData, error.response?.status))
        }
        return Promise.reject(error)
      }

      if (shouldAttemptRefresh(error)) {
        const config = error.config as RetryConfig
        config._retry = true
        try {
          await refreshAccessToken()
          return client(config)
        } catch (refreshError) {
          if (!handleSetupError(refreshError)) {
            unauthorizedHandler?.()
          }
          return Promise.reject(refreshError)
        }
      }

      if (error.response?.status === 401) {
        const config = error.config as RetryConfig | undefined
        if (config && !shouldSkipAuthHandling(config)) {
          unauthorizedHandler?.()
        }
      }

      if (validateResponse) {
        if (isStandardApiResponse(responseData) || isStandardErrorResponse(responseData)) {
          return Promise.reject(buildApiError(responseData, error.response?.status))
        }
      }

      return Promise.reject(error)
    }
  )

  return client
}

export async function fetchWithAuthRetry(
  input: RequestInfo | URL,
  init: RequestInit = {}
): Promise<Response> {
  const requestUrl = getFetchUrl(input)
  const requestInit: RequestInit = {
    ...init,
    credentials: init.credentials ?? 'include'
  }
  const headers = new Headers(requestInit.headers ?? {})
  const csrfToken = getCookieValue(CSRF_COOKIE_NAME)
  if (csrfToken && !headers.has(CSRF_HEADER_NAME)) {
    headers.set(CSRF_HEADER_NAME, csrfToken)
  }
  requestInit.headers = headers
  const firstResponse = await fetch(input, requestInit)

  if (!shouldSkipAuthHandlingForUrl(requestUrl)) {
    try {
      const responseData = await firstResponse.clone().json()
      if (handleSetupApiResponse(responseData)) {
        return firstResponse
      }
    } catch {
      // ignore non-json response body
    }
  }

  if (firstResponse.status !== 401 || shouldSkipAuthHandlingForUrl(requestUrl)) {
    return firstResponse
  }

  try {
    await refreshAccessToken()
  } catch (error) {
    if (!handleSetupError(error)) {
      unauthorizedHandler?.()
    }
    return firstResponse
  }

  const retryInput = input instanceof Request ? input.clone() : input
  const retryResponse = await fetch(retryInput, requestInit)

  if (!shouldSkipAuthHandlingForUrl(requestUrl)) {
    try {
      const responseData = await retryResponse.clone().json()
      handleSetupApiResponse(responseData)
    } catch {
      // ignore non-json response body
    }
  }

  return retryResponse
}
