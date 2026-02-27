import type { Menu } from '@/services/types/auth'

export type PermissionCode = 'ADMIN' | 'READ' | 'DISABLE'

export function normalizePath(pathname: string): string {
  const trimmed = pathname.trim()
  if (!trimmed) {
    return '/'
  }
  if (trimmed === '/') {
    return trimmed
  }
  return trimmed.replace(/\/+$/, '')
}

export function normalizePermissionCode(permissionCode?: string | null): PermissionCode {
  const normalized = permissionCode?.trim().toUpperCase()
  if (normalized === 'ADMIN' || normalized === 'READ' || normalized === 'DISABLE') {
    return normalized
  }
  return 'DISABLE'
}

export function isMenuVisible(menu: Menu): boolean {
  return normalizePermissionCode(menu.permissionCode) !== 'DISABLE'
}

export function collectEnabledMenuPaths(menus: Menu[]): Set<string> {
  const paths = new Set<string>()

  const walk = (items: Menu[]) => {
    for (const item of items) {
      if (!item) {
        continue
      }
      if (item.path && isMenuVisible(item)) {
        paths.add(normalizePath(item.path))
      }
      if (Array.isArray(item.children) && item.children.length > 0) {
        walk(item.children)
      }
    }
  }

  walk(menus)
  return paths
}

export function hasMenuPathAccess(menus: Menu[], requiredMenuPath: string): boolean {
  const enabledPaths = collectEnabledMenuPaths(menus)
  return enabledPaths.has(normalizePath(requiredMenuPath))
}
