import { useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { getSetupStatus } from "@/lib/api/setup"
import { useAuthStore } from "@/stores"

const SETUP_PATH = "/setup"
const HOME_PATH = "/home"
const LOGIN_PATH = "/login"
const SERVER_ERROR_PATH = "/500"

type EntryRouteTarget = "setup" | "home" | "login" | "server-error"

let entryRouteTargetPromise: Promise<EntryRouteTarget> | null = null

function isSetupPath(): boolean {
  if (typeof window === "undefined") {
    return false
  }
  return window.location.pathname === SETUP_PATH
}

async function resolveEntryRouteTarget(initializeAuth: () => Promise<void>): Promise<EntryRouteTarget> {
  try {
    const setupStatus = await getSetupStatus()
    if (!setupStatus.initialized) {
      return "setup"
    }
  } catch {
    return "server-error"
  }

  try {
    await initializeAuth()
  } catch {
    return "server-error"
  }

  return useAuthStore.getState().isAuthenticated ? "home" : "login"
}

/**
 * 应用入口路由：先判定初始化状态，再做登录态分流。
 */
export function EntryRoute() {
  const navigate = useNavigate()
  const loading = useAuthStore((state) => state.loading)
  const initializeAuth = useAuthStore((state) => state.initializeAuth)

  useEffect(() => {
    let cancelled = false

    if (!entryRouteTargetPromise) {
      entryRouteTargetPromise = resolveEntryRouteTarget(initializeAuth).finally(() => {
        entryRouteTargetPromise = null
      })
    }

    void entryRouteTargetPromise.then((target) => {
      if (cancelled || isSetupPath()) {
        return
      }

      if (target === "setup") {
        navigate(SETUP_PATH, { replace: true })
        return
      }

      if (target === "home") {
        navigate(HOME_PATH, { replace: true })
        return
      }

      if (target === "server-error") {
        navigate(SERVER_ERROR_PATH, { replace: true })
        return
      }

      navigate(LOGIN_PATH, { replace: true })
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
