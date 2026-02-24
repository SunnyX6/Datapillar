import axios, { AxiosHeaders, type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import {
  handleAppError,
  normalizeApiPayloadError,
  normalizeAxiosError
} from '@/lib/error-center'
import {
  isApiResponse as isApiDataResponse,
  isApiSuccess,
  isErrorResponse as isApiErrorResponse,
  type ApiResponse,
  type ApiError,
  type ErrorResponse
} from '@/types/api'
import { API_ABSOLUTE_PATH, API_BASE, API_PATH } from './endpoints'

interface ApiClientOptions {
  baseURL: string
  timeout?: number
  validateResponse?: boolean
}

interface AuthLifecycleCallbacks {
  canRefresh: () => boolean
  onRefreshStart: () => void
  onRefreshEnd: () => void
  onSessionExpired: () => void
}

type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean }

const AUTH_BASE_URL = API_BASE.auth
const LOGIN_BASE_URL = API_BASE.login
const CSRF_COOKIE_NAME = 'csrf-token'
const CSRF_HEADER_NAME = 'X-CSRF-Token'
const REFRESH_CSRF_COOKIE_NAME = 'refresh-csrf-token'
const REFRESH_CSRF_HEADER_NAME = 'X-Refresh-CSRF-Token'
const AUTH_ENDPOINTS = {
  loginBase: LOGIN_BASE_URL,
  logout: `${LOGIN_BASE_URL}${API_PATH.login.logout}`,
  refresh: `${AUTH_BASE_URL}${API_PATH.auth.refresh}`
}
const CORE_FAILURE_PATHS = new Set<string>([API_ABSOLUTE_PATH.setupStatus])

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
let authLifecycleCallbacks: AuthLifecycleCallbacks | null = null

export function setAuthLifecycleCallbacks(callbacks: AuthLifecycleCallbacks | null): void {
  authLifecycleCallbacks = callbacks
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

function normalizePath(url: string): string {
  try {
    return new URL(url, 'http://localhost').pathname
  } catch {
    return url
  }
}

function isCoreFailureRequest(url: string): boolean {
  return CORE_FAILURE_PATHS.has(normalizePath(url))
}

function handleApiPayloadFailure(
  payload: ApiResponse<unknown> | ErrorResponse,
  status: number | undefined,
  requestUrl: string,
  method?: string,
  headers?: unknown
): void {
  handleAppError(
    normalizeApiPayloadError(payload, {
      module: 'api/client',
      status,
      requestUrl,
      method,
      isCoreRequest: isCoreFailureRequest(requestUrl),
      headers
    })
  )
}

function handleAxiosFailure(error: unknown): void {
  const axiosError = axios.isAxiosError(error) ? error : undefined
  const config = axiosError?.config as InternalAxiosRequestConfig | undefined
  const requestUrl = config ? getRequestUrl(config) : undefined
  const method = config?.method?.toUpperCase()
  const isCoreRequest = requestUrl ? isCoreFailureRequest(requestUrl) : false

  handleAppError(
    normalizeAxiosError(error, {
      module: 'api/client',
      requestUrl,
      method,
      isCoreRequest
    })
  )
}

async function refreshAccessToken(): Promise<void> {
  if (!refreshPromise) {
    authLifecycleCallbacks?.onRefreshStart()
    refreshPromise = refreshClient
      .post<ApiResponse<void> | ErrorResponse>(API_PATH.auth.refresh)
      .then((response) => {
        const data = response.data
        const requestUrl = AUTH_ENDPOINTS.refresh
        if (isStandardErrorResponse(data)) {
          handleApiPayloadFailure(data, response.status, requestUrl, 'POST', response.headers)
          throw buildApiError(data, response.status)
        }
        if (!isStandardApiResponse(data)) {
          throw new Error('Invalid API response')
        }
        if (!isApiSuccess(data)) {
          handleApiPayloadFailure(data, response.status, requestUrl, 'POST', response.headers)
          throw buildApiError(data, response.status)
        }
      })
      .finally(() => {
        refreshPromise = null
        authLifecycleCallbacks?.onRefreshEnd()
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

  if (!authLifecycleCallbacks || !authLifecycleCallbacks.canRefresh()) {
    return false
  }

  if (shouldSkipAuthHandling(config)) {
    return false
  }

  return true
}

function handleSessionExpired(): void {
  authLifecycleCallbacks?.onSessionExpired()
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
      const requestUrl = getRequestUrl(response.config as InternalAxiosRequestConfig)
      const method = response.config.method?.toUpperCase()

      if (isStandardErrorResponse(data)) {
        handleApiPayloadFailure(data, response.status, requestUrl, method, response.headers)
        return Promise.reject(buildApiError(data, response.status))
      }
      if (!isStandardApiResponse(data)) {
        return Promise.reject(new Error('Invalid API response'))
      }
      if (!isApiSuccess(data)) {
        handleApiPayloadFailure(data, response.status, requestUrl, method, response.headers)
        return Promise.reject(buildApiError(data, response.status))
      }
      return response
    },
    async (error: AxiosError) => {
      const responseData = error.response?.data

      if (shouldAttemptRefresh(error)) {
        const config = error.config as RetryConfig
        config._retry = true
        try {
          await refreshAccessToken()
          return client(config)
        } catch (refreshFailure) {
          handleSessionExpired()
          return Promise.reject(refreshFailure)
        }
      }

      if (error.response?.status === 401) {
        const config = error.config as RetryConfig | undefined
        const skipAuthHandling = config ? shouldSkipAuthHandling(config) : false
        if (!skipAuthHandling) {
          handleSessionExpired()
        }
        if (validateResponse && (isStandardApiResponse(responseData) || isStandardErrorResponse(responseData))) {
          return Promise.reject(buildApiError(responseData, error.response?.status))
        }
        return Promise.reject(error)
      }

      if (validateResponse && (isStandardApiResponse(responseData) || isStandardErrorResponse(responseData))) {
        const config = error.config as InternalAxiosRequestConfig | undefined
        const requestUrl = config ? getRequestUrl(config) : ''
        const method = config?.method?.toUpperCase()
        handleApiPayloadFailure(responseData, error.response?.status, requestUrl, method, error.response?.headers)
        return Promise.reject(buildApiError(responseData, error.response?.status))
      }

      handleAxiosFailure(error)
      return Promise.reject(error)
    }
  )

  return client
}
