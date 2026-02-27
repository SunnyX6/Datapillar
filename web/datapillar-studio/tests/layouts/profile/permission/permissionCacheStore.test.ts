import { beforeEach, describe, expect, it } from 'vitest'
import { usePermissionCacheStore } from '@/features/profile/state'
import type { RoleDefinition, UserItem } from '@/features/profile/utils/permissionTypes'

const roleDefinition: RoleDefinition = {
  id: '1',
  type: 'USER',
  name: '数据分析师',
  description: '测试角色',
  permissions: [],
  memberCount: 0,
}

const roleUser: UserItem = {
  id: '1001',
  name: 'sunny',
  email: 'sunny@datapillar.ai',
  roleId: '1',
  status: '已激活',
  lastActive: '2026-02-25 10:00:00',
}

describe('permissionCacheStore', () => {
  beforeEach(() => {
    usePermissionCacheStore.getState().reset()
  })

  it('成员缓存失效时应保留成员数据，仅清理时间戳', () => {
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

  it('功能权限缓存失效时应保留权限树，仅清理时间戳', () => {
    const store = usePermissionCacheStore.getState()
    store.setPermissionsData('1', [
      {
        objectId: 101,
        objectName: '元数据目录',
        sort: 1,
        categoryName: '数据资产',
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

  it('更新用户 AI 模型权限时只应更新目标用户', () => {
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
