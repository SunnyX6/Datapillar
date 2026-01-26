import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { isApiSuccess, type ApiResponse, type ApiError } from '@/types/api'

interface ApiClientOptions {
  baseURL: string
  timeout?: number
  validateResponse?: boolean
}

type RetryConfig = InternalAxiosRequestConfig & { _retry?: boolean }

const AUTH_BASE_URL = '/api/auth'
const AUTH_ENDPOINTS = {
  login: `${AUTH_BASE_URL}/login`,
  logout: `${AUTH_BASE_URL}/logout`,
  refresh: `${AUTH_BASE_URL}/refresh`,
  tokenInfo: `${AUTH_BASE_URL}/token-info`
}

const refreshClient = axios.create({
  baseURL: AUTH_BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

let refreshPromise: Promise<void> | null = null
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

function isApiResponse(data: unknown): data is ApiResponse<unknown> {
  if (!data || typeof data !== 'object') {
    return false
  }
  const candidate = data as Record<string, unknown>
  return typeof candidate.status === 'number' &&
    typeof candidate.code === 'string' &&
    typeof candidate.message === 'string' &&
    'data' in candidate &&
    typeof candidate.timestamp === 'string'
}

function buildApiError(data: ApiResponse<unknown>): ApiError {
  const error = new Error(data.message || 'Request failed') as ApiError
  error.code = data.code
  error.status = data.status
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
  return requestUrl.startsWith(AUTH_ENDPOINTS.login) ||
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
  return path.startsWith(AUTH_ENDPOINTS.login) ||
    path.startsWith(AUTH_ENDPOINTS.logout) ||
    path.startsWith(AUTH_ENDPOINTS.refresh)
}

async function refreshAccessToken(): Promise<void> {
  if (!refreshPromise) {
    refreshPromise = refreshClient
      .post<ApiResponse<void>>('/refresh')
      .then((response) => {
        const data = response.data
        if (!isApiResponse(data)) {
          throw new Error('Invalid API response')
        }
        if (!isApiSuccess(data)) {
          throw buildApiError(data)
        }
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

  client.interceptors.response.use(
    (response) => {
      if (!validateResponse) {
        return response
      }
      const data = response.data
      if (!isApiResponse(data)) {
        return Promise.reject(new Error('Invalid API response'))
      }
      if (!isApiSuccess(data)) {
        return Promise.reject(buildApiError(data))
      }
      return response
    },
    async (error: AxiosError) => {
      if (shouldAttemptRefresh(error)) {
        const config = error.config as RetryConfig
        config._retry = true
        try {
          await refreshAccessToken()
          return client(config)
        } catch (refreshError) {
          unauthorizedHandler?.()
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
        const responseData = error.response?.data
        if (isApiResponse(responseData)) {
          return Promise.reject(buildApiError(responseData))
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
  const firstResponse = await fetch(input, requestInit)

  if (firstResponse.status !== 401 || shouldSkipAuthHandlingForUrl(requestUrl)) {
    return firstResponse
  }

  try {
    await refreshAccessToken()
  } catch {
    unauthorizedHandler?.()
    return firstResponse
  }

  const retryInput = input instanceof Request ? input.clone() : input
  return fetch(retryInput, requestInit)
}
