import { useCallback, useEffect } from 'react'
import { toast } from 'sonner'
import { getTenantRolePermissions } from '@/services/studioTenantRoleService'
import { usePermissionCacheStore } from '../state'
import { shouldRequestCache } from '../utils/permissionCachePolicy'
import {
  mapRolePermissionToResource,
  type PermissionResource,
  type PermissionTab,
} from '../utils/permissionTypes'
import { sortPermissionResources } from '../utils/permissionSelectors'

interface UsePermissionFunctionalParams {
  tenantId?: number
  roleId: string
  activeTab: PermissionTab
}

interface UsePermissionFunctionalResult {
  permissions: PermissionResource[]
  functionalLoading: boolean
  functionalError: string | null
  refreshFunctional: () => Promise<void>
}

const permissionsRequestMap = new Map<string, Promise<void>>()

function resolveErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message
  }

  return '未知错误'
}

function parseRoleId(roleId: string): number | null {
  const parsedRoleId = Number(roleId)
  if (!Number.isFinite(parsedRoleId)) {
    return null
  }

  return parsedRoleId
}

export function usePermissionFunctional(
  params: UsePermissionFunctionalParams,
): UsePermissionFunctionalResult {
  const permissionsBucket = usePermissionCacheStore((state) => {
    if (!params.roleId) {
      return undefined
    }
    return state.permissionsByRoleId[params.roleId]
  })

  const setPermissionsLoading = usePermissionCacheStore(
    (state) => state.setPermissionsLoading,
  )
  const setPermissionsError = usePermissionCacheStore(
    (state) => state.setPermissionsError,
  )
  const setPermissionsData = usePermissionCacheStore(
    (state) => state.setPermissionsData,
  )

  const ensurePermissions = useCallback(
    async (force = false) => {
      if (!params.tenantId || !params.roleId) {
        return
      }

      const parsedRoleId = parseRoleId(params.roleId)
      if (parsedRoleId === null) {
        return
      }

      const cacheState = usePermissionCacheStore.getState()
      const currentBucket = cacheState.permissionsByRoleId[params.roleId]
      if (!shouldRequestCache(currentBucket, { force })) {
        return
      }

      const requestKey = `${params.tenantId}:${params.roleId}`
      const existingRequest = permissionsRequestMap.get(requestKey)
      if (existingRequest) {
        await existingRequest
        return
      }

      const request = (async () => {
        setPermissionsLoading(params.roleId, true)

        try {
          const permissions = await getTenantRolePermissions(
            params.tenantId,
            parsedRoleId,
            'ALL',
          )
          setPermissionsData(
            params.roleId,
            sortPermissionResources(permissions.map(mapRolePermissionToResource)),
          )
        } catch (error) {
          const message = resolveErrorMessage(error)
          setPermissionsError(params.roleId, message)
          toast.error(`加载角色功能权限失败：${message}`)
        }
      })()

      permissionsRequestMap.set(requestKey, request)

      try {
        await request
      } finally {
        permissionsRequestMap.delete(requestKey)
      }
    },
    [
      params.roleId,
      params.tenantId,
      setPermissionsData,
      setPermissionsError,
      setPermissionsLoading,
    ],
  )

  useEffect(() => {
    if (params.activeTab !== 'functional') {
      return
    }

    void ensurePermissions(false)
  }, [ensurePermissions, params.activeTab])

  return {
    permissions: permissionsBucket?.permissions ?? [],
    functionalLoading: permissionsBucket?.loading ?? false,
    functionalError: permissionsBucket?.error ?? null,
    refreshFunctional: async () => {
      await ensurePermissions(true)
    },
  }
}
