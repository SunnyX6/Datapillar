import { describe, expect, it } from 'vitest'
import {
  mapRoleMemberToUserItem,
  mapRoleMembersToUserItems
} from '@/features/profile/utils/memberAdapter'
import type { StudioRoleMember } from '@/services/types/studio/role'

describe('memberAdapter', () => {
  it('Role members should be mapped to the page user model', () => {
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
    expect(user.status).toBe('Activated')
    expect(user.lastActive).toBe('2026-02-02 12:00:00')
  })

  it('Empty nicknames and unknown status should be handled', () => {
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
    expect(users[0].status).toBe('Invited')
  })
})
