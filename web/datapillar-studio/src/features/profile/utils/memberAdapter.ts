import { formatTime } from '@/utils'
import type { UserItem, UserStatus } from './permissionTypes'

interface RoleMemberSource {
  userId: number
  nickname?: string | null
  username?: string | null
  email?: string | null
  userLevel?: number | null
  memberStatus?: number
  assignedAt?: string | null
  joinedAt?: string | null
}

function mapMemberStatusToUserStatus(status?: number): UserStatus {
  if (status === 1) {
    return '已激活'
  }
  if (status === 0) {
    return '已禁用'
  }
  return '已邀请'
}

function resolveMemberName(member: RoleMemberSource): string {
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

function resolveMemberEmail(member: RoleMemberSource): string {
  const email = member.email?.trim()
  if (email) {
    return email
  }
  return '-'
}

export function mapRoleMemberToUserItem(
  member: RoleMemberSource,
  roleId: string
): UserItem {
  return {
    id: String(member.userId),
    name: resolveMemberName(member),
    email: resolveMemberEmail(member),
    roleId,
    level: member.userLevel ?? undefined,
    status: mapMemberStatusToUserStatus(member.memberStatus),
    lastActive: formatTime(member.assignedAt ?? member.joinedAt),
    department: undefined
  }
}

export function mapRoleMembersToUserItems(
  members: RoleMemberSource[],
  roleId: string
): UserItem[] {
  return members.map((member) => mapRoleMemberToUserItem(member, roleId))
}
