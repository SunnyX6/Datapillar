import { useCallback, useEffect } from 'react'
import { toast } from 'sonner'
import { getTenantRoleMembers } from '@/services/studioTenantRoleService'
import { usePermissionCacheStore } from '../state'
import { mapRoleMembersToUserItems } from '../utils/memberAdapter'
import { shouldRequestCache } from '../utils/permissionCachePolicy'
import type { PermissionTab, UserItem } from '../utils/permissionTypes'

interface UsePermissionMembersParams {
  tenantId?: number
  roleId: string
  activeTab: PermissionTab
}

interface UsePermissionMembersResult {
  users: UserItem[]
  memberCount: number
  membersLoading: boolean
  membersError: string | null
  refreshMembers: () => Promise<void>
}

const membersRequestMap = new Map<string, Promise<void>>()

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

export function usePermissionMembers(
  params: UsePermissionMembersParams,
): UsePermissionMembersResult {
  const membersBucket = usePermissionCacheStore((state) => {
    if (!params.roleId) {
      return undefined
    }
    return state.membersByRoleId[params.roleId]
  })

  const setMembersLoading = usePermissionCacheStore(
    (state) => state.setMembersLoading,
  )
  const setMembersError = usePermissionCacheStore((state) => state.setMembersError)
  const setMembersData = usePermissionCacheStore((state) => state.setMembersData)

  const ensureMembers = useCallback(
    async (force = false) => {
      if (!params.tenantId || !params.roleId) {
        return
      }

      const parsedRoleId = parseRoleId(params.roleId)
      if (parsedRoleId === null) {
        return
      }

      const cacheState = usePermissionCacheStore.getState()
      const currentBucket = cacheState.membersByRoleId[params.roleId]
      if (!shouldRequestCache(currentBucket, { force })) {
        return
      }

      const requestKey = `${params.tenantId}:${params.roleId}`
      const existingRequest = membersRequestMap.get(requestKey)
      if (existingRequest) {
        await existingRequest
        return
      }

      const request = (async () => {
        setMembersLoading(params.roleId, true)

        try {
          const response = await getTenantRoleMembers(params.tenantId, parsedRoleId)
          setMembersData(
            params.roleId,
            mapRoleMembersToUserItems(response.members, params.roleId),
            response.memberCount,
          )
        } catch (error) {
          const message = resolveErrorMessage(error)
          setMembersError(params.roleId, message)
          toast.error(`加载角色成员失败：${message}`)
        }
      })()

      membersRequestMap.set(requestKey, request)

      try {
        await request
      } finally {
        membersRequestMap.delete(requestKey)
      }
    },
    [
      params.roleId,
      params.tenantId,
      setMembersData,
      setMembersError,
      setMembersLoading,
    ],
  )

  useEffect(() => {
    if (params.activeTab !== 'members') {
      return
    }

    void ensureMembers(false)
  }, [ensureMembers, params.activeTab])

  return {
    users: membersBucket?.users ?? [],
    memberCount: membersBucket?.memberCount ?? 0,
    membersLoading: membersBucket?.loading ?? false,
    membersError: membersBucket?.error ?? null,
    refreshMembers: async () => {
      await ensureMembers(true)
    },
  }
}
