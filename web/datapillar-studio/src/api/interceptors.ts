import { AxiosHeaders } from 'axios'

interface CsrfHeaderOptions {
  cookieName: string
  headerName: string
}

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

export function attachCsrfHeader(
  headers: AxiosHeaders,
  options: CsrfHeaderOptions
): void {
  const token = getCookieValue(options.cookieName)
  if (!token) {
    return
  }

  if (!headers.has(options.headerName)) {
    headers.set(options.headerName, token)
  }
}
