import { useCallback } from 'react'
import { toast } from 'sonner'
import { updateTenantRolePermissions } from '@/services/studioTenantRoleService'
import { usePermissionCacheStore, usePermissionUiStore } from '../state'
import type { PermissionLevel } from '../utils/permissionConstants'
import {
  toRolePermissionAssignments,
  updatePermissionTree,
} from '../utils/permissionSelectors'
import type { AiAccessLevel, RoleDefinition } from '../utils/permissionTypes'

interface UsePermissionMutationsParams {
  tenantId?: number
  selectedRole?: RoleDefinition
}

interface UsePermissionMutationsResult {
  updateRolePermission: (objectId: number, level: PermissionLevel) => Promise<void>
  updateUserModelAccess: (userId: string, aiModelId: number, access: AiAccessLevel) => void
}

function parseRoleId(roleId: string): number | null {
  const parsedRoleId = Number(roleId)
  if (!Number.isFinite(parsedRoleId)) {
    return null
  }

  return parsedRoleId
}

function resolveErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message
  }

  return '未知错误'
}

export function usePermissionMutations(
  params: UsePermissionMutationsParams,
): UsePermissionMutationsResult {
  const selectedRoleId = usePermissionUiStore((state) => state.selectedRoleId)
  const setPermissionsData = usePermissionCacheStore((state) => state.setPermissionsData)
  const updateUserAiModelPermission = usePermissionCacheStore(
    (state) => state.updateUserAiModelPermission,
  )

  const updateRolePermission = useCallback(
    async (objectId: number, level: PermissionLevel) => {
      const selectedRole = params.selectedRole

      if (!params.tenantId || !selectedRole) {
        return
      }

      if (selectedRole.isSystem) {
        toast.error('平台超管角色不允许修改功能权限')
        return
      }

      const parsedRoleId = parseRoleId(selectedRole.id)
      if (parsedRoleId === null) {
        toast.error('角色ID无效，无法更新功能权限')
        return
      }

      const previousPermissions = selectedRole.permissions
      const updateResult = updatePermissionTree(previousPermissions, objectId, level)
      if (!updateResult.updated) {
        return
      }

      setPermissionsData(selectedRole.id, updateResult.resources)

      try {
        await updateTenantRolePermissions(
          params.tenantId,
          parsedRoleId,
          toRolePermissionAssignments(updateResult.resources),
        )
      } catch (error) {
        setPermissionsData(selectedRole.id, previousPermissions)
        const message = resolveErrorMessage(error)
        toast.error(`更新角色功能权限失败：${message}`)
      }
    },
    [params.selectedRole, params.tenantId, setPermissionsData],
  )

  const updateUserModelAccess = useCallback(
    (userId: string, aiModelId: number, access: AiAccessLevel) => {
      if (!selectedRoleId) {
        return
      }

      updateUserAiModelPermission(selectedRoleId, userId, aiModelId, access)
    },
    [selectedRoleId, updateUserAiModelPermission],
  )

  return {
    updateRolePermission,
    updateUserModelAccess,
  }
}
