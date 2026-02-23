import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { handleAppError, normalizeAxiosError } from '@/lib/error-center'
import { useAuthStore } from '@/stores'

const HOME_PATH = '/home'
const LOGIN_PATH = '/login'

type EntryRouteTarget = 'home' | 'login' | 'halt'

let entryRouteTargetPromise: Promise<EntryRouteTarget> | null = null

async function resolveEntryRouteTarget(
  initializeAuth: () => Promise<void>,
): Promise<EntryRouteTarget> {
  try {
    await initializeAuth()
  } catch (error) {
    handleAppError(
      normalizeAxiosError(error, {
        module: 'router/entry-route',
        isCoreRequest: true,
      }),
    )
    return 'halt'
  }

  return useAuthStore.getState().isAuthenticated ? 'home' : 'login'
}

/**
 * 应用入口路由：仅负责登录态分流。
 */
export function EntryRoute() {
  const navigate = useNavigate()
  const loading = useAuthStore((state) => state.loading)
  const initializeAuth = useAuthStore((state) => state.initializeAuth)

  useEffect(() => {
    let cancelled = false

    if (!entryRouteTargetPromise) {
      entryRouteTargetPromise = resolveEntryRouteTarget(initializeAuth).finally(
        () => {
          entryRouteTargetPromise = null
        },
      )
    }

    void entryRouteTargetPromise.then((target) => {
      if (cancelled) {
        return
      }
      if (target === 'home') {
        navigate(HOME_PATH, { replace: true })
        return
      }
      if (target === 'login') {
        navigate(LOGIN_PATH, { replace: true })
      }
    })

    return () => {
      cancelled = true
    }
  }, [initializeAuth, navigate])

  if (loading) {
    return null
  }
  return null
}
