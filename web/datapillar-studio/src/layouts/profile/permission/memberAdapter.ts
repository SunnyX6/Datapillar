import { formatTime } from '@/lib/utils'
import type { StudioRoleMember } from '@/types/studio/role'
import type { UserItem, UserStatus } from './Permission'

function mapMemberStatusToUserStatus(status?: number): UserStatus {
  if (status === 1) {
    return '已激活'
  }
  if (status === 0) {
    return '已禁用'
  }
  return '已邀请'
}

function resolveMemberName(member: StudioRoleMember): string {
  const nickname = member.nickname?.trim()
  if (nickname) {
    return nickname
  }
  const username = member.username?.trim()
  if (username) {
    return username
  }
  return `用户${member.userId}`
}

function resolveMemberEmail(member: StudioRoleMember): string {
  const email = member.email?.trim()
  if (email) {
    return email
  }
  return '-'
}

export function mapRoleMemberToUserItem(
  member: StudioRoleMember,
  roleId: string
): UserItem {
  return {
    id: String(member.userId),
    name: resolveMemberName(member),
    email: resolveMemberEmail(member),
    roleId,
    status: mapMemberStatusToUserStatus(member.memberStatus),
    lastActive: formatTime(member.assignedAt ?? member.joinedAt),
    department: undefined
  }
}

export function mapRoleMembersToUserItems(
  members: StudioRoleMember[],
  roleId: string
): UserItem[] {
  return members.map((member) => mapRoleMemberToUserItem(member, roleId))
}
