import { useCallback, useMemo } from 'react'
import { toast } from 'sonner'
import { removeTenantRoleMembers } from '@/services/studioTenantRoleService'
import { useAuthStore } from '@/state/authStore'
import { usePermissionCacheStore, usePermissionUiStore } from '../state'
import { buildRoleItems } from '../utils/permissionSelectors'
import type { RoleItem } from '../utils/permissionTypes'
import { usePermissionFunctional } from './usePermissionFunctional'
import { usePermissionMembers } from './usePermissionMembers'
import { usePermissionMutations } from './usePermissionMutations'
import { usePermissionRoles } from './usePermissionRoles'

function resolveErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message
  }

  return 'unknown error'
}

export function usePermissionViewModel() {
  const tenantId = useAuthStore((state) => state.user?.tenantId)

  const selectedRoleId = usePermissionUiStore((state) => state.selectedRoleId)
  const setSelectedRoleId = usePermissionUiStore((state) => state.setSelectedRoleId)
  const activeTab = usePermissionUiStore((state) => state.activeTab)
  const setActiveTab = usePermissionUiStore((state) => state.setActiveTab)
  const isAddModalOpen = usePermissionUiStore((state) => state.isAddModalOpen)
  const setAddModalOpen = usePermissionUiStore((state) => state.setAddModalOpen)

  const membersByRoleId = usePermissionCacheStore((state) => state.membersByRoleId)
  const permissionsByRoleId = usePermissionCacheStore(
    (state) => state.permissionsByRoleId,
  )

  const {
    roleDefinitions,
    rolesLoading,
    rolesError,
    createRole,
    updateRole,
    deleteRole,
    refreshRoles,
  } = usePermissionRoles(tenantId)

  const {
    users: selectedRoleUsers,
    memberCount,
    membersLoading,
    membersError,
    refreshMembers,
  } = usePermissionMembers({
    tenantId,
    roleId: selectedRoleId,
    activeTab,
  })

  const {
    permissions: selectedRolePermissions,
    functionalLoading,
    functionalError,
    refreshFunctional,
  } = usePermissionFunctional({
    tenantId,
    roleId: selectedRoleId,
    activeTab,
  })

  const roleMembersMap = useMemo(() => {
    return Object.fromEntries(
      Object.entries(membersByRoleId).map(([roleId, bucket]) => [roleId, bucket.users]),
    )
  }, [membersByRoleId])

  const roles = useMemo<RoleItem[]>(() => {
    return buildRoleItems(roleDefinitions, roleMembersMap)
  }, [roleDefinitions, roleMembersMap])

  const selectedRoleBase = useMemo(
    () => roles.find((role) => role.id === selectedRoleId) ?? roles[0],
    [roles, selectedRoleId],
  )

  const selectedRole = useMemo(() => {
    if (!selectedRoleBase) {
      return undefined
    }

    const cachedPermissions = permissionsByRoleId[selectedRoleBase.id]?.permissions

    return {
      ...selectedRoleBase,
      permissions: cachedPermissions ?? selectedRolePermissions,
      memberCount,
    }
  }, [memberCount, permissionsByRoleId, selectedRoleBase, selectedRolePermissions])

  const selectedRolePreviewUsers = useMemo(
    () => selectedRoleUsers.slice(0, 5),
    [selectedRoleUsers],
  )

  const hasSelectedRoleUsers = selectedRoleUsers.length > 0

  const { updateRolePermission, updateUserModelAccess } = usePermissionMutations({
    tenantId,
    selectedRole,
  })

  const deleteMembers = useCallback(
    async (userIds: string[]): Promise<string[]> => {
      if (!tenantId) {
        toast.error('Current tenant information is missing，Unable to delete member')
        return []
      }
      if (!selectedRoleId) {
        toast.error('Current role information is missing，Unable to delete member')
        return []
      }
      const parsedRoleId = Number(selectedRoleId)
      if (!Number.isFinite(parsedRoleId)) {
        toast.error('roleIDInvalid，Unable to delete member')
        return []
      }

      const normalizedUserIds = Array.from(
        new Set(
          userIds
            .map((userId) => userId.trim())
            .filter((userId) => userId.length > 0),
        ),
      )
      if (normalizedUserIds.length === 0) {
        return []
      }

      const parsedUserIds = Array.from(
        new Set(
          normalizedUserIds
            .map((userId) => Number(userId))
            .filter((userId) => Number.isFinite(userId)),
        ),
      )
      if (parsedUserIds.length === 0) {
        toast.error('memberIDInvalid，cannot be deleted')
        return []
      }

      try {
        await removeTenantRoleMembers(tenantId, parsedRoleId, parsedUserIds)
        await Promise.all([refreshMembers(), refreshRoles()])

        const parsedUserIdSet = new Set(parsedUserIds)
        const successUserIds = normalizedUserIds.filter((userId) =>
          parsedUserIdSet.has(Number(userId)),
        )
        toast.success(
          successUserIds.length === 1
            ? 'Member deleted successfully'
            : `Deleted ${successUserIds.length} members`,
        )
        return successUserIds
      } catch (error) {
        toast.error(`Failed to delete member：${resolveErrorMessage(error)}`)
        return []
      }
    },
    [refreshMembers, refreshRoles, selectedRoleId, tenantId],
  )

  return {
    tenantId,
    roles,
    selectedRole,
    selectedRoleId,
    activeTab,
    isAddModalOpen,
    selectedRoleUsers,
    selectedRolePreviewUsers,
    hasSelectedRoleUsers,
    rolesLoading,
    rolesError,
    membersLoading,
    membersError,
    functionalLoading,
    functionalError,
    setSelectedRoleId,
    setActiveTab,
    setAddModalOpen,
    createRole,
    updateRole,
    deleteRole,
    refreshRoles,
    refreshMembers,
    refreshFunctional,
    updateRolePermission,
    updateUserModelAccess,
    deleteMembers,
  }
}
