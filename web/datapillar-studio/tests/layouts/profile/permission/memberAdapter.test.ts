import { describe, expect, it } from 'vitest'
import {
  mapRoleMemberToUserItem,
  mapRoleMembersToUserItems
} from '@/layouts/profile/permission/memberAdapter'
import type { StudioRoleMember } from '@/types/studio/role'

describe('memberAdapter', () => {
  it('应将角色成员映射为页面用户模型', () => {
    const member: StudioRoleMember = {
      userId: 101,
      username: 'sunny',
      nickname: 'Sunny',
      email: 'sunny@datapillar.ai',
      phone: '13800000001',
      memberStatus: 1,
      joinedAt: '2026-02-01T10:00:00',
      assignedAt: '2026-02-02T12:00:00'
    }

    const user = mapRoleMemberToUserItem(member, '3')

    expect(user.id).toBe('101')
    expect(user.name).toBe('Sunny')
    expect(user.email).toBe('sunny@datapillar.ai')
    expect(user.roleId).toBe('3')
    expect(user.status).toBe('已激活')
    expect(user.lastActive).toBe('2026-02-02 12:00:00')
  })

  it('应处理空昵称和未知状态', () => {
    const member: StudioRoleMember = {
      userId: 102,
      username: 'alice',
      nickname: '   ',
      email: null,
      phone: null,
      memberStatus: 2,
      joinedAt: '2026-02-03T09:30:00',
      assignedAt: '2026-02-03T10:00:00'
    }

    const users = mapRoleMembersToUserItems([member], '5')

    expect(users).toHaveLength(1)
    expect(users[0].name).toBe('alice')
    expect(users[0].email).toBe('-')
    expect(users[0].status).toBe('已邀请')
  })
})
