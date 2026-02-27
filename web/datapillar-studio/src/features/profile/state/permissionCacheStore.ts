import { create } from 'zustand'
import type {
  AiAccessLevel,
  PermissionResource,
  RoleDefinition,
  UserItem,
} from '../utils/permissionTypes'

export interface PermissionMembersCacheBucket {
  users: UserItem[]
  memberCount: number
  loading: boolean
  error: string | null
  fetchedAt: number | null
}

export interface PermissionFunctionalCacheBucket {
  permissions: PermissionResource[]
  loading: boolean
  error: string | null
  fetchedAt: number | null
}

export interface PermissionCacheState {
  roles: RoleDefinition[]
  rolesLoading: boolean
  rolesError: string | null
  rolesFetchedAt: number | null
  membersByRoleId: Record<string, PermissionMembersCacheBucket>
  permissionsByRoleId: Record<string, PermissionFunctionalCacheBucket>
  setRolesLoading: (loading: boolean) => void
  setRolesError: (message: string | null) => void
  replaceRoles: (roles: RoleDefinition[]) => void
  setRoleMemberCount: (roleId: string, memberCount: number) => void
  setMembersLoading: (roleId: string, loading: boolean) => void
  setMembersError: (roleId: string, message: string | null) => void
  setMembersData: (roleId: string, users: UserItem[], memberCount: number) => void
  setPermissionsLoading: (roleId: string, loading: boolean) => void
  setPermissionsError: (roleId: string, message: string | null) => void
  setPermissionsData: (roleId: string, permissions: PermissionResource[]) => void
  updateUserAiModelPermission: (
    roleId: string,
    userId: string,
    aiModelId: number,
    access: AiAccessLevel,
  ) => void
  invalidateMembers: (roleId: string) => void
  invalidatePermissions: (roleId: string) => void
  invalidateRole: (roleId: string) => void
  removeRoleCache: (roleId: string) => void
  removeMembersFromAllRoles: (userIds: string[]) => void
  reset: () => void
}

function createMembersBucket(): PermissionMembersCacheBucket {
  return {
    users: [],
    memberCount: 0,
    loading: false,
    error: null,
    fetchedAt: null,
  }
}

function createFunctionalBucket(): PermissionFunctionalCacheBucket {
  return {
    permissions: [],
    loading: false,
    error: null,
    fetchedAt: null,
  }
}

const DEFAULT_STATE = {
  roles: [] as RoleDefinition[],
  rolesLoading: false,
  rolesError: null as string | null,
  rolesFetchedAt: null as number | null,
  membersByRoleId: {} as Record<string, PermissionMembersCacheBucket>,
  permissionsByRoleId: {} as Record<string, PermissionFunctionalCacheBucket>,
}

function filterCacheByRoleIds<T>(cache: Record<string, T>, roleIds: Set<string>) {
  return Object.fromEntries(
    Object.entries(cache).filter(([roleId]) => roleIds.has(roleId)),
  ) as Record<string, T>
}

export const usePermissionCacheStore = create<PermissionCacheState>((set) => ({
  ...DEFAULT_STATE,
  setRolesLoading: (loading) => set({ rolesLoading: loading }),
  setRolesError: (message) => set({ rolesError: message }),
  replaceRoles: (roles) =>
    set((state) => {
      const roleIdSet = new Set(roles.map((role) => role.id))

      return {
        roles,
        rolesLoading: false,
        rolesError: null,
        rolesFetchedAt: Date.now(),
        membersByRoleId: filterCacheByRoleIds(state.membersByRoleId, roleIdSet),
        permissionsByRoleId: filterCacheByRoleIds(
          state.permissionsByRoleId,
          roleIdSet,
        ),
      }
    }),
  setRoleMemberCount: (roleId, memberCount) =>
    set((state) => ({
      roles: state.roles.map((role) => {
        if (role.id !== roleId) {
          return role
        }

        return {
          ...role,
          memberCount,
        }
      }),
    })),
  setMembersLoading: (roleId, loading) =>
    set((state) => ({
      membersByRoleId: {
        ...state.membersByRoleId,
        [roleId]: {
          ...(state.membersByRoleId[roleId] ?? createMembersBucket()),
          loading,
          error: loading ? null : state.membersByRoleId[roleId]?.error ?? null,
        },
      },
    })),
  setMembersError: (roleId, message) =>
    set((state) => ({
      membersByRoleId: {
        ...state.membersByRoleId,
        [roleId]: {
          ...(state.membersByRoleId[roleId] ?? createMembersBucket()),
          loading: false,
          error: message,
        },
      },
    })),
  setMembersData: (roleId, users, memberCount) =>
    set((state) => ({
      roles: state.roles.map((role) => {
        if (role.id !== roleId) {
          return role
        }

        return {
          ...role,
          memberCount,
        }
      }),
      membersByRoleId: {
        ...state.membersByRoleId,
        [roleId]: {
          users,
          memberCount,
          loading: false,
          error: null,
          fetchedAt: Date.now(),
        },
      },
    })),
  setPermissionsLoading: (roleId, loading) =>
    set((state) => ({
      permissionsByRoleId: {
        ...state.permissionsByRoleId,
        [roleId]: {
          ...(state.permissionsByRoleId[roleId] ?? createFunctionalBucket()),
          loading,
          error: loading
            ? null
            : state.permissionsByRoleId[roleId]?.error ?? null,
        },
      },
    })),
  setPermissionsError: (roleId, message) =>
    set((state) => ({
      permissionsByRoleId: {
        ...state.permissionsByRoleId,
        [roleId]: {
          ...(state.permissionsByRoleId[roleId] ?? createFunctionalBucket()),
          loading: false,
          error: message,
        },
      },
    })),
  setPermissionsData: (roleId, permissions) =>
    set((state) => ({
      permissionsByRoleId: {
        ...state.permissionsByRoleId,
        [roleId]: {
          permissions,
          loading: false,
          error: null,
          fetchedAt: Date.now(),
        },
      },
    })),
  updateUserAiModelPermission: (roleId, userId, aiModelId, access) =>
    set((state) => {
      const membersBucket = state.membersByRoleId[roleId]
      if (!membersBucket) {
        return state
      }

      const nextUsers = membersBucket.users.map((user) => {
        if (user.id !== userId) {
          return user
        }

        const nextPermissions = (user.aiModelPermissions ?? []).filter(
          (permission) => permission.aiModelId !== aiModelId,
        )

        if (access !== 'DISABLE') {
          nextPermissions.push({ aiModelId, access })
        }

        return {
          ...user,
          aiModelPermissions:
            nextPermissions.length > 0 ? nextPermissions : undefined,
        }
      })

      return {
        membersByRoleId: {
          ...state.membersByRoleId,
          [roleId]: {
            ...membersBucket,
            users: nextUsers,
          },
        },
      }
    }),
  invalidateMembers: (roleId) =>
    set((state) => {
      const membersBucket = state.membersByRoleId[roleId]
      if (!membersBucket) {
        return state
      }

      return {
        membersByRoleId: {
          ...state.membersByRoleId,
          [roleId]: {
            ...membersBucket,
            fetchedAt: null,
            error: null,
          },
        },
      }
    }),
  invalidatePermissions: (roleId) =>
    set((state) => {
      const permissionBucket = state.permissionsByRoleId[roleId]
      if (!permissionBucket) {
        return state
      }

      return {
        permissionsByRoleId: {
          ...state.permissionsByRoleId,
          [roleId]: {
            ...permissionBucket,
            fetchedAt: null,
            error: null,
          },
        },
      }
    }),
  invalidateRole: (roleId) =>
    set((state) => {
      const nextMembersBucket = state.membersByRoleId[roleId]
      const nextPermissionBucket = state.permissionsByRoleId[roleId]

      if (!nextMembersBucket && !nextPermissionBucket) {
        return state
      }

      return {
        membersByRoleId: !nextMembersBucket
          ? state.membersByRoleId
          : {
              ...state.membersByRoleId,
              [roleId]: {
                ...nextMembersBucket,
                fetchedAt: null,
                error: null,
              },
            },
        permissionsByRoleId: !nextPermissionBucket
          ? state.permissionsByRoleId
          : {
              ...state.permissionsByRoleId,
              [roleId]: {
                ...nextPermissionBucket,
                fetchedAt: null,
                error: null,
              },
            },
      }
    }),
  removeRoleCache: (roleId) =>
    set((state) => {
      if (!state.membersByRoleId[roleId] && !state.permissionsByRoleId[roleId]) {
        return state
      }

      const nextMembersByRoleId = {
        ...state.membersByRoleId,
      }
      const nextPermissionsByRoleId = {
        ...state.permissionsByRoleId,
      }

      delete nextMembersByRoleId[roleId]
      delete nextPermissionsByRoleId[roleId]

      return {
        membersByRoleId: nextMembersByRoleId,
        permissionsByRoleId: nextPermissionsByRoleId,
      }
    }),
  removeMembersFromAllRoles: (userIds) =>
    set((state) => {
      if (userIds.length === 0) {
        return state
      }

      const targetUserIds = new Set(userIds)
      let membersUpdated = false

      const nextMembersByRoleId = Object.fromEntries(
        Object.entries(state.membersByRoleId).map(([roleId, bucket]) => {
          const nextUsers = bucket.users.filter((user) => !targetUserIds.has(user.id))
          if (nextUsers.length === bucket.users.length) {
            return [roleId, bucket]
          }

          membersUpdated = true
          return [
            roleId,
            {
              ...bucket,
              users: nextUsers,
              memberCount: nextUsers.length
            }
          ]
        })
      ) as Record<string, PermissionMembersCacheBucket>

      if (!membersUpdated) {
        return state
      }

      const nextRoles = state.roles.map((role) => {
        const membersBucket = nextMembersByRoleId[role.id]
        if (!membersBucket || role.memberCount === membersBucket.memberCount) {
          return role
        }

        return {
          ...role,
          memberCount: membersBucket.memberCount
        }
      })

      return {
        roles: nextRoles,
        membersByRoleId: nextMembersByRoleId
      }
    }),
  reset: () => set(DEFAULT_STATE),
}))
