import { normalizeReturnPath } from '@/utils/exceptionNavigation'

let lastAllowedRoute: string | null = null

export function rememberLastAllowedRoute(path: string) {
  const normalized = normalizeReturnPath(path)
  if (!normalized) {
    return
  }

  lastAllowedRoute = normalized
}

export function resolveLastAllowedRoute() {
  return lastAllowedRoute
}

export function resetLastAllowedRoute() {
  lastAllowedRoute = null
}
