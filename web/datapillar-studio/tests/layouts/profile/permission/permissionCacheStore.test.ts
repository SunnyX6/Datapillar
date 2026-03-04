import { beforeEach, describe, expect, it } from 'vitest'
import { usePermissionCacheStore } from '@/features/profile/state'
import type { RoleDefinition, UserItem } from '@/features/profile/utils/permissionTypes'

const roleDefinition: RoleDefinition = {
  id: '1',
  type: 'USER',
  name: 'data analyst',
  description: 'test role',
  permissions: [],
  memberCount: 0,
}

const roleUser: UserItem = {
  id: '1001',
  name: 'sunny',
  email: 'sunny@datapillar.ai',
  roleId: '1',
  status: 'Activated',
  lastActive: '2026-02-25 10:00:00',
}

describe('permissionCacheStore', () => {
  beforeEach(() => {
    usePermissionCacheStore.getState().reset()
  })

  it('Member data should be retained when member cache is invalidated，Clean timestamps only', () => {
    const store = usePermissionCacheStore.getState()
    store.replaceRoles([roleDefinition])
    store.setMembersData('1', [roleUser], 1)

    const beforeInvalidate = usePermissionCacheStore.getState().membersByRoleId['1']
    expect(beforeInvalidate?.users).toHaveLength(1)
    expect(beforeInvalidate?.fetchedAt).not.toBeNull()

    store.invalidateMembers('1')

    const afterInvalidate = usePermissionCacheStore.getState().membersByRoleId['1']
    expect(afterInvalidate?.users).toHaveLength(1)
    expect(afterInvalidate?.memberCount).toBe(1)
    expect(afterInvalidate?.fetchedAt).toBeNull()
    expect(afterInvalidate?.error).toBeNull()
  })

  it('The permission tree should be preserved when the function permission cache is invalidated，Clean timestamps only', () => {
    const store = usePermissionCacheStore.getState()
    store.setPermissionsData('1', [
      {
        objectId: 101,
        objectName: 'metadata directory',
        sort: 1,
        categoryName: 'data assets',
        level: 'READ',
        tenantLevel: 'ADMIN',
        children: [],
      },
    ])

    const beforeInvalidate = usePermissionCacheStore.getState().permissionsByRoleId['1']
    expect(beforeInvalidate?.permissions).toHaveLength(1)
    expect(beforeInvalidate?.fetchedAt).not.toBeNull()

    store.invalidatePermissions('1')

    const afterInvalidate = usePermissionCacheStore.getState().permissionsByRoleId['1']
    expect(afterInvalidate?.permissions).toHaveLength(1)
    expect(afterInvalidate?.fetchedAt).toBeNull()
    expect(afterInvalidate?.error).toBeNull()
  })

  it('Update user AI Model permissions should only be updated for the target user', () => {
    const store = usePermissionCacheStore.getState()
    store.setMembersData('1', [
      roleUser,
      {
        ...roleUser,
        id: '1002',
        name: 'alice',
      },
    ], 2)

    store.updateUserAiModelPermission('1', '1001', 10001, 'READ')

    const users = usePermissionCacheStore.getState().membersByRoleId['1']?.users ?? []
    const updatedUser = users.find((user) => user.id === '1001')
    const untouchedUser = users.find((user) => user.id === '1002')

    expect(updatedUser?.aiModelPermissions).toEqual([
      { aiModelId: 10001, access: 'READ' },
    ])
    expect(untouchedUser?.aiModelPermissions).toBeUndefined()
  })
})
