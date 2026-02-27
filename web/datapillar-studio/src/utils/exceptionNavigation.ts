export type ForbiddenReason = 'permission-denied' | 'no-accessible-entry'

interface PathLike {
  pathname: string
  search?: string
  hash?: string
}

interface BuildForbiddenPathOptions {
  reason: ForbiddenReason
  from?: string | null
  deniedPath?: string | null
}

interface ResolveForbiddenQueryResult {
  reason: ForbiddenReason
  from: string
  deniedPath?: string
}

const LOGIN_PATH = '/login'
const EXCEPTION_PATHNAME_SET = new Set(['/403', '/404', '/500'])

function splitPathname(path: string): string {
  const queryIndex = path.indexOf('?')
  const hashIndex = path.indexOf('#')
  let endIndex = path.length

  if (queryIndex >= 0) {
    endIndex = Math.min(endIndex, queryIndex)
  }

  if (hashIndex >= 0) {
    endIndex = Math.min(endIndex, hashIndex)
  }

  return path.slice(0, endIndex)
}

function normalizeInternalPath(path: string | null | undefined): string | null {
  if (typeof path !== 'string') {
    return null
  }

  const normalized = path.trim()
  if (!normalized || !normalized.startsWith('/') || normalized.startsWith('//')) {
    return null
  }

  return normalized
}

export function buildLocationPath(location: PathLike): string {
  return `${location.pathname}${location.search ?? ''}${location.hash ?? ''}`
}

export function normalizeReturnPath(path: string | null | undefined): string | null {
  const normalized = normalizeInternalPath(path)
  if (!normalized) {
    return null
  }

  const pathname = splitPathname(normalized)
  if (EXCEPTION_PATHNAME_SET.has(pathname)) {
    return null
  }

  return normalized
}

export function buildEntryPathWithFrom(from: string | null | undefined): string {
  const normalizedFrom = normalizeReturnPath(from)
  if (!normalizedFrom) {
    return '/'
  }

  const searchParams = new URLSearchParams()
  searchParams.set('from', normalizedFrom)
  return `/?${searchParams.toString()}`
}

export function buildForbiddenPath(options: BuildForbiddenPathOptions): string {
  const searchParams = new URLSearchParams()
  searchParams.set('reason', options.reason)

  const from = normalizeReturnPath(options.from) ?? LOGIN_PATH
  searchParams.set('from', from)

  const deniedPath = normalizeInternalPath(options.deniedPath)
  if (deniedPath) {
    searchParams.set('denied', deniedPath)
  }

  return `/403?${searchParams.toString()}`
}

export function resolveForbiddenQuery(search: string): ResolveForbiddenQueryResult {
  const searchParams = new URLSearchParams(search)
  const reasonRaw = searchParams.get('reason')
  const reason: ForbiddenReason = reasonRaw === 'no-accessible-entry' ? 'no-accessible-entry' : 'permission-denied'
  const from = normalizeReturnPath(searchParams.get('from')) ?? LOGIN_PATH
  const deniedPath = normalizeInternalPath(searchParams.get('denied'))

  if (!deniedPath) {
    return { reason, from }
  }

  return {
    reason,
    from,
    deniedPath,
  }
}
