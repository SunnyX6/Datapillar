import { useCallback, useEffect, useRef } from 'react'
import { toast } from 'sonner'
import {
  createTenantRole,
  deleteTenantRole,
  listTenantRoles,
  updateTenantRole,
} from '@/services/studioTenantRoleService'
import { usePermissionCacheStore, usePermissionUiStore } from '../state'
import {
  mapStudioRoleToDefinition,
  type RoleDefinition,
  type RoleType,
} from '../utils/permissionTypes'

interface UsePermissionRolesResult {
  roleDefinitions: RoleDefinition[]
  rolesLoading: boolean
  rolesError: string | null
  refreshRoles: () => Promise<void>
  createRole: (payload: {
    name: string
    description?: string
    type: RoleType
  }) => Promise<boolean>
  updateRole: (
    roleId: string,
    payload: {
      name: string
      description?: string
      type: RoleType
    },
  ) => Promise<boolean>
  deleteRole: (roleId: string) => Promise<boolean>
}

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

export function usePermissionRoles(tenantId?: number): UsePermissionRolesResult {
  const roleDefinitions = usePermissionCacheStore((state) => state.roles)
  const rolesLoading = usePermissionCacheStore((state) => state.rolesLoading)
  const rolesError = usePermissionCacheStore((state) => state.rolesError)
  const rolesFetchedAt = usePermissionCacheStore((state) => state.rolesFetchedAt)
  const replaceRoles = usePermissionCacheStore((state) => state.replaceRoles)
  const setRolesLoading = usePermissionCacheStore((state) => state.setRolesLoading)
  const setRolesError = usePermissionCacheStore((state) => state.setRolesError)
  const resetCacheStore = usePermissionCacheStore((state) => state.reset)

  const selectedRoleId = usePermissionUiStore((state) => state.selectedRoleId)
  const setSelectedRoleId = usePermissionUiStore((state) => state.setSelectedRoleId)
  const setActiveTab = usePermissionUiStore((state) => state.setActiveTab)
  const setAddModalOpen = usePermissionUiStore((state) => state.setAddModalOpen)
  const resetUiStore = usePermissionUiStore((state) => state.reset)

  const tenantRef = useRef<number | undefined>(undefined)

  const syncRoles = useCallback(
    (roles: RoleDefinition[]) => {
      replaceRoles(roles)
    },
    [replaceRoles],
  )

  const loadRoles = useCallback(
    async (force = false) => {
      if (!tenantId) {
        return
      }

      if (!force && rolesFetchedAt) {
        return
      }

      setRolesLoading(true)
      setRolesError(null)

      try {
        const rolesFromBackend = await listTenantRoles(tenantId)
        syncRoles(rolesFromBackend.map(mapStudioRoleToDefinition))
      } catch (error) {
        setRolesLoading(false)
        const message = resolveErrorMessage(error)
        setRolesError(message)
        toast.error(`加载角色列表失败：${message}`)
      }
    },
    [rolesFetchedAt, setRolesError, setRolesLoading, syncRoles, tenantId],
  )

  const refreshRoles = useCallback(async () => {
    await loadRoles(true)
  }, [loadRoles])

  useEffect(() => {
    if (!tenantId) {
      tenantRef.current = undefined
      resetCacheStore()
      resetUiStore()
      return
    }

    if (tenantRef.current !== tenantId) {
      tenantRef.current = tenantId
      resetCacheStore()
      resetUiStore()
    }

    void loadRoles(false)
  }, [loadRoles, resetCacheStore, resetUiStore, tenantId])

  useEffect(() => {
    if (roleDefinitions.length === 0) {
      if (selectedRoleId) {
        setSelectedRoleId('')
      }
      return
    }

    if (
      !selectedRoleId ||
      !roleDefinitions.some((role) => role.id === selectedRoleId)
    ) {
      setSelectedRoleId(roleDefinitions[0].id)
    }
  }, [roleDefinitions, selectedRoleId, setSelectedRoleId])

  const createRole = useCallback(
    async (payload: {
      name: string
      description?: string
      type: RoleType
    }): Promise<boolean> => {
      if (!tenantId) {
        toast.error('当前租户信息缺失，无法创建角色')
        return false
      }

      try {
        await createTenantRole(tenantId, payload)
        await loadRoles(true)
        setActiveTab('members')
        setAddModalOpen(false)
        toast.success(`角色「${payload.name}」创建成功`)
        return true
      } catch (error) {
        const message = resolveErrorMessage(error)
        toast.error(`创建角色失败：${message}`)
        return false
      }
    },
    [loadRoles, setActiveTab, setAddModalOpen, tenantId],
  )

  const updateRole = useCallback(
    async (
      roleId: string,
      payload: {
        name: string
        description?: string
        type: RoleType
      },
    ): Promise<boolean> => {
      if (!tenantId) {
        toast.error('当前租户信息缺失，无法更新角色')
        return false
      }

      const parsedRoleId = parseRoleId(roleId)
      if (parsedRoleId === null) {
        toast.error('角色ID无效，无法更新')
        return false
      }

      try {
        await updateTenantRole(tenantId, parsedRoleId, payload)
        await loadRoles(true)
        toast.success(`角色「${payload.name}」更新成功`)
        return true
      } catch (error) {
        const message = resolveErrorMessage(error)
        toast.error(`更新角色失败：${message}`)
        return false
      }
    },
    [loadRoles, tenantId],
  )

  const deleteRole = useCallback(
    async (roleId: string): Promise<boolean> => {
      if (!tenantId) {
        toast.error('当前租户信息缺失，无法删除角色')
        return false
      }

      const parsedRoleId = parseRoleId(roleId)
      if (parsedRoleId === null) {
        toast.error('角色ID无效，无法删除')
        return false
      }

      try {
        await deleteTenantRole(tenantId, parsedRoleId)
        await loadRoles(true)
        setActiveTab('members')
        toast.success('角色删除成功')
        return true
      } catch (error) {
        const message = resolveErrorMessage(error)
        toast.error(`删除角色失败：${message}`)
        return false
      }
    },
    [loadRoles, setActiveTab, tenantId],
  )

  return {
    roleDefinitions,
    rolesLoading,
    rolesError,
    refreshRoles,
    createRole,
    updateRole,
    deleteRole,
  }
}
