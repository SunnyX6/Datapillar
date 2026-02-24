import type { Menu } from '@/types/auth'

type PermissionCode = 'ADMIN' | 'READ' | 'DISABLE'

interface RouteAccessRule {
  match: (pathname: string) => boolean
  requiredMenuPaths: string[]
}

const ENTRY_ROUTE_PRIORITY: readonly string[] = [
  '/home',
  '/governance/metadata',
  '/governance/semantic',
  '/governance/knowledge',
  '/projects',
  '/collaboration',
  '/workflow',
  '/wiki',
  '/data-tracking',
  '/ide',
  '/profile',
  '/profile/permission',
  '/profile/llm/models',
]

const ROUTE_ACCESS_RULES: readonly RouteAccessRule[] = [
  {
    match: (pathname) =>
      pathname === '/governance/metadata' ||
      pathname.startsWith('/governance/metadata/'),
    requiredMenuPaths: ['/governance/metadata'],
  },
  {
    match: (pathname) =>
      pathname === '/governance/semantic' ||
      pathname.startsWith('/governance/semantic/'),
    requiredMenuPaths: ['/governance/semantic'],
  },
  {
    match: (pathname) =>
      pathname === '/governance/knowledge' ||
      pathname.startsWith('/governance/knowledge/'),
    requiredMenuPaths: ['/governance/knowledge'],
  },
  {
    match: (pathname) => pathname === '/profile/permission',
    requiredMenuPaths: ['/profile/permission'],
  },
  {
    match: (pathname) => pathname === '/profile/llm/models',
    requiredMenuPaths: ['/profile/llm/models'],
  },
  {
    match: (pathname) => pathname === '/profile',
    requiredMenuPaths: ['/profile'],
  },
  {
    match: (pathname) => pathname === '/ide/sql',
    requiredMenuPaths: ['/ide/sql', '/ide'],
  },
  {
    match: (pathname) => pathname === '/ide' || pathname.startsWith('/ide/'),
    requiredMenuPaths: ['/ide'],
  },
  {
    match: (pathname) => pathname === '/home' || pathname.startsWith('/home/'),
    requiredMenuPaths: ['/home'],
  },
  {
    match: (pathname) =>
      pathname === '/projects' || pathname.startsWith('/projects/'),
    requiredMenuPaths: ['/projects'],
  },
  {
    match: (pathname) =>
      pathname === '/collaboration' || pathname.startsWith('/collaboration/'),
    requiredMenuPaths: ['/collaboration'],
  },
  {
    match: (pathname) =>
      pathname === '/workflow' || pathname.startsWith('/workflow/'),
    requiredMenuPaths: ['/workflow'],
  },
  {
    match: (pathname) => pathname === '/wiki' || pathname.startsWith('/wiki/'),
    requiredMenuPaths: ['/wiki'],
  },
  {
    match: (pathname) =>
      pathname === '/data-tracking' || pathname.startsWith('/data-tracking/'),
    requiredMenuPaths: ['/data-tracking'],
  },
]

function normalizePath(pathname: string): string {
  const trimmed = pathname.trim()
  if (!trimmed) {
    return '/'
  }
  if (trimmed === '/') {
    return trimmed
  }
  return trimmed.replace(/\/+$/, '')
}

function normalizePermissionCode(permissionCode?: string | null): PermissionCode {
  const normalized = permissionCode?.trim().toUpperCase()
  if (normalized === 'ADMIN' || normalized === 'READ' || normalized === 'DISABLE') {
    return normalized
  }
  return 'DISABLE'
}

function collectEnabledMenuPaths(menus: Menu[]): Set<string> {
  const paths = new Set<string>()

  const walk = (items: Menu[]) => {
    for (const item of items) {
      if (!item) {
        continue
      }
      if (item.path && normalizePermissionCode(item.permissionCode) !== 'DISABLE') {
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

export function isMenuVisible(menu: Menu): boolean {
  return normalizePermissionCode(menu.permissionCode) !== 'DISABLE'
}

export function canAccessRouteByMenus(menus: Menu[], pathname: string): boolean {
  const normalizedPath = normalizePath(pathname)
  const matchedRule = ROUTE_ACCESS_RULES.find((rule) => rule.match(normalizedPath))
  if (!matchedRule) {
    return true
  }
  const enabledPaths = collectEnabledMenuPaths(menus)
  return matchedRule.requiredMenuPaths.some((requiredPath) =>
    enabledPaths.has(requiredPath),
  )
}

export function resolveFirstAccessibleRoute(menus: Menu[]): string | null {
  const enabledPaths = collectEnabledMenuPaths(menus)
  for (const path of ENTRY_ROUTE_PRIORITY) {
    if (enabledPaths.has(path)) {
      return path
    }
  }
  return null
}
