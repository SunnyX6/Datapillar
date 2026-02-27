interface CacheBucketLike {
  loading?: boolean
  fetchedAt?: number | null
}

export const PERMISSION_CACHE_TTL_MS = 5 * 60 * 1000

export interface ShouldRequestCacheOptions {
  force?: boolean
  ttlMs?: number
  now?: number
}

export function isCacheExpired(
  fetchedAt: number | null | undefined,
  ttlMs = PERMISSION_CACHE_TTL_MS,
  now = Date.now(),
): boolean {
  if (!fetchedAt) {
    return true
  }

  return now - fetchedAt > ttlMs
}

export function shouldRequestCache(
  bucket: CacheBucketLike | undefined,
  options: ShouldRequestCacheOptions = {},
): boolean {
  if (options.force) {
    return true
  }

  if (!bucket) {
    return true
  }

  if (bucket.loading) {
    return false
  }

  return isCacheExpired(bucket.fetchedAt, options.ttlMs, options.now)
}
