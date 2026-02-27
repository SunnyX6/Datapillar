import { useEffect, useMemo, useState } from 'react'
import { Building2, Check, Search, Settings2, Trash2, UserPlus, X } from 'lucide-react'
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import type {
  AiAccessLevel,
  RoleDefinition,
  UserItem,
  UserStatus,
} from '../../utils/permissionTypes'
import { InviteMemberModal } from './InviteMemberModal'
import { PermissionAvatar } from './PermissionAvatar'
import { UserPermissionDrawer } from './UserPermissionDrawer'

const STATUS_STYLES: Record<UserStatus, string> = {
  已激活:
    'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-300 dark:border-emerald-500/30',
  已邀请:
    'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-500/10 dark:text-amber-300 dark:border-amber-500/30',
  已禁用:
    'bg-rose-50 text-rose-700 border-rose-100 dark:bg-rose-500/10 dark:text-rose-300 dark:border-rose-500/30',
}
const PLATFORM_SUPER_ADMIN_LEVEL = 0

function isPlatformSuperAdmin(user: UserItem): boolean {
  return user.level === PLATFORM_SUPER_ADMIN_LEVEL
}

interface MembersListProps {
  role: RoleDefinition
  selectedRoleId: string
  users: UserItem[]
  onUpdateUserModelAccess: (
    userId: string,
    aiModelId: number,
    access: AiAccessLevel,
  ) => void
  onDeleteUsers?: (userIds: string[]) => Promise<string[]>
  showToolbar?: boolean
  isAddModalOpen?: boolean
  onOpenAddModal?: () => void
  onCloseAddModal?: () => void
}

export function MembersList({
  role,
  selectedRoleId,
  users,
  onUpdateUserModelAccess,
  onDeleteUsers,
  showToolbar = true,
  isAddModalOpen = false,
  onOpenAddModal,
  onCloseAddModal,
}: MembersListProps) {
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [selectedUserIds, setSelectedUserIds] = useState<string[]>([])
  const [isBatchDeleting, setIsBatchDeleting] = useState(false)

  const assignedUsers = useMemo(
    () => users.filter((user) => user.roleId === role.id),
    [users, role.id],
  )
  const selectableUsers = useMemo(
    () => assignedUsers.filter((user) => !isPlatformSuperAdmin(user)),
    [assignedUsers],
  )
  const selectedUserIdSet = useMemo(() => new Set(selectedUserIds), [selectedUserIds])
  const selectedCount = selectedUserIds.length
  const isAllSelected =
    selectableUsers.length > 0 &&
    selectableUsers.every((user) => selectedUserIdSet.has(user.id))

  const selectedUser = useMemo(
    () => users.find((user) => user.id === selectedUserId) ?? null,
    [users, selectedUserId],
  )

  useEffect(() => {
    const assignedUserIdSet = new Set(selectableUsers.map((user) => user.id))
    setSelectedUserIds((prev) => prev.filter((userId) => assignedUserIdSet.has(userId)))
    const currentUser = users.find((item) => item.id === selectedUserId)
    if (
      selectedUserId &&
      (!currentUser ||
        currentUser.roleId !== role.id ||
        isPlatformSuperAdmin(currentUser))
    ) {
      setSelectedUserId(null)
    }
  }, [role.id, selectableUsers, selectedUserId, users])

  const toggleUserSelection = (userId: string) => {
    const targetUser = assignedUsers.find((user) => user.id === userId)
    if (!targetUser || isPlatformSuperAdmin(targetUser)) {
      return
    }
    setSelectedUserIds((prev) =>
      prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId],
    )
  }

  const toggleSelectAll = () => {
    if (isAllSelected) {
      setSelectedUserIds([])
      return
    }
    setSelectedUserIds(selectableUsers.map((user) => user.id))
  }

  const clearSelection = () => {
    setSelectedUserIds([])
  }

  const removeUsers = async (userIds: string[]) => {
    if (!onDeleteUsers || userIds.length === 0) {
      return
    }

    setIsBatchDeleting(true)

    try {
      const deletedUserIds = await onDeleteUsers(userIds)
      if (deletedUserIds.length === 0) {
        return
      }

      const deletedUserIdSet = new Set(deletedUserIds)
      setSelectedUserIds((prev) => prev.filter((userId) => !deletedUserIdSet.has(userId)))
      if (selectedUserId && deletedUserIdSet.has(selectedUserId)) {
        setSelectedUserId(null)
      }
    } finally {
      setIsBatchDeleting(false)
    }
  }

  return (
    <div className="relative flex flex-col gap-6">
      {showToolbar && (
        <div className="flex items-end justify-between gap-4">
          <div>
            <h3
              className={cn(
                TYPOGRAPHY.subtitle,
                'font-bold text-slate-900 dark:text-white',
              )}
            >
              成员列表
            </h3>
            <p
              className={cn(
                TYPOGRAPHY.caption,
                'text-slate-500 dark:text-slate-400 mt-1',
              )}
            >
              管理 <span className="font-semibold text-brand-600">{role.name}</span> 下的成员。
            </p>
          </div>
          <div className="flex items-center gap-3">
            <div className="relative group w-64">
              <Search
                className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 group-focus-within:text-brand-500 dark:group-focus-within:text-brand-400 transition-colors"
                size={14}
              />
              <input
                type="text"
                placeholder="搜索成员..."
                className={cn(
                  TYPOGRAPHY.bodySm,
                  'w-full pl-9 pr-4 py-1.5 bg-white dark:bg-slate-900 border border-brand-300/70 dark:border-brand-400/40 rounded-lg hover:border-brand-400/80 focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 outline-none transition-all text-slate-900 dark:text-slate-100 placeholder:text-slate-400',
                )}
              />
            </div>
            <Button size="small" variant="primary" onClick={onOpenAddModal}>
              <UserPlus size={14} />
              添加成员
            </Button>
          </div>
        </div>
      )}

      <Table
        layout="auto"
        minWidth="none"
        className="shadow-none dark:shadow-none"
        tableClassName={cn(
          TYPOGRAPHY.bodySm,
          'text-slate-900 dark:text-slate-100',
        )}
      >
        <TableHeader
          className={cn(
            TYPOGRAPHY.caption,
            'bg-slate-50/50 dark:bg-slate-800/50 text-slate-500 dark:text-slate-400 font-medium',
          )}
        >
          <TableRow>
            <TableHead className="px-6 py-3.5 w-1/3">用户</TableHead>
            <TableHead className="px-6 py-3.5">部门</TableHead>
            <TableHead className="px-6 py-3.5">权限状态</TableHead>
            <TableHead className="px-6 py-3.5">活跃时间</TableHead>
            <TableHead className="px-6 py-3.5 text-center">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody className="divide-y divide-slate-100 dark:divide-slate-800">
          {assignedUsers.length === 0 ? (
            <TableRow>
              <TableCell colSpan={5} className="px-6 py-16 text-center">
                <div className="flex flex-col items-center justify-center text-slate-400 dark:text-slate-500">
                  <div className="w-12 h-12 rounded-full bg-slate-50 dark:bg-slate-800 flex items-center justify-center mb-3">
                    <UserPlus size={20} />
                  </div>
                  <p
                    className={cn(
                      TYPOGRAPHY.bodySm,
                      'text-slate-600 dark:text-slate-300 font-medium',
                    )}
                  >
                    暂无成员
                  </p>
                  <Button
                    variant="outline"
                    size="small"
                    className="mt-4"
                    onClick={onOpenAddModal}
                  >
                    立即邀请
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ) : (
            assignedUsers.map((user) => {
              const isProtectedUser = isPlatformSuperAdmin(user)
              const isSelected = selectedUserIdSet.has(user.id)
              return (
                <TableRow
                  key={user.id}
                  onClick={() => {
                    toggleUserSelection(user.id)
                  }}
                  className={cn(
                    'transition-colors',
                    isProtectedUser ? 'cursor-default' : 'cursor-pointer',
                    isSelected
                      ? 'bg-brand-50/55 dark:bg-brand-500/10'
                      : isProtectedUser
                        ? ''
                        : 'hover:bg-slate-50/80 dark:hover:bg-slate-800/70',
                  )}
                >
                  <TableCell className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      {isSelected ? (
                        <span className="w-8 h-8 rounded-full bg-brand-600 text-white flex items-center justify-center shadow-sm shrink-0">
                          <Check size={14} />
                        </span>
                      ) : (
                        <PermissionAvatar
                          name={user.name}
                          src={user.avatarUrl}
                          size="sm"
                        />
                      )}
                      <div>
                        <div
                          className={cn(
                            TYPOGRAPHY.bodySm,
                            'font-medium text-slate-900 dark:text-white',
                          )}
                        >
                          {user.name}
                        </div>
                        <div
                          className={cn(
                            TYPOGRAPHY.caption,
                            'text-slate-500 dark:text-slate-400',
                          )}
                        >
                          {user.email}
                        </div>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="px-6 py-4">
                    <div
                      className={cn(
                        TYPOGRAPHY.caption,
                        'flex items-center gap-2 text-slate-500 dark:text-slate-400',
                      )}
                    >
                      <Building2
                        size={12}
                        className="text-slate-400 dark:text-slate-500"
                      />
                      <span>{user.department ?? '-'}</span>
                    </div>
                  </TableCell>
                  <TableCell className="px-6 py-4">
                    <div className="flex flex-col items-start gap-1">
                      <StatusBadge status={user.status} />
                      {isProtectedUser && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs border bg-violet-50 text-violet-700 border-violet-100 dark:bg-violet-500/10 dark:text-violet-300 dark:border-violet-500/30">
                          平台超管
                        </span>
                      )}
                    </div>
                  </TableCell>
                  <TableCell
                    className={cn(
                      TYPOGRAPHY.caption,
                      'px-6 py-4 text-slate-500 dark:text-slate-400 font-mono',
                    )}
                  >
                    {user.lastActive}
                  </TableCell>
                  <TableCell className="px-6 py-4 text-center">
                    {!isProtectedUser && (
                      <Button
                        size="small"
                        variant="outline"
                        onClick={(event) => {
                          event.stopPropagation()
                          setSelectedUserId(user.id)
                        }}
                        className="border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300"
                      >
                        <Settings2 size={14} />
                        配置权限
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              )
            })
          )}
        </TableBody>
      </Table>

      {selectedCount > 0 && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 animate-in slide-in-from-bottom-3 fade-in duration-200">
          <div className="inline-flex items-center rounded-2xl border border-slate-700 bg-slate-900 text-white shadow-2xl shadow-slate-900/30 px-2 py-1.5">
            <div className="flex items-center gap-2 px-2">
              <span className="inline-flex size-5 items-center justify-center rounded-full bg-brand-500 text-micro font-bold text-white">
                {selectedCount}
              </span>
              <span className={cn(TYPOGRAPHY.caption, 'font-medium whitespace-nowrap')}>
                已选择
              </span>
            </div>

            <div className="mx-1 h-4 w-px bg-slate-700" />

            <button
              type="button"
              aria-label="全选所有成员"
              onClick={toggleSelectAll}
              className={cn(
                TYPOGRAPHY.caption,
                'px-2 py-1 text-slate-300 transition-colors hover:text-white',
              )}
            >
              {isAllSelected ? '取消全选' : '全选所有'}
            </button>

            <div className="mx-1 h-4 w-px bg-slate-700" />

            <button
              type="button"
              aria-label="批量删除成员"
              onClick={() => {
                void removeUsers(selectedUserIds)
              }}
              disabled={isBatchDeleting}
              className={cn(
                TYPOGRAPHY.caption,
                'inline-flex items-center gap-1.5 px-2 py-1 text-rose-400 transition-colors hover:text-rose-300 disabled:cursor-not-allowed disabled:opacity-50',
              )}
            >
              <Trash2 size={12} />
              批量删除
            </button>

            <button
              type="button"
              aria-label="关闭批量操作"
              onClick={clearSelection}
              className="ml-1 inline-flex size-6 items-center justify-center rounded-lg text-slate-400 transition-colors hover:text-white"
            >
              <X size={12} />
            </button>
          </div>
        </div>
      )}

      <InviteMemberModal
        key={`${selectedRoleId}-${isAddModalOpen ? 'open' : 'closed'}`}
        isOpen={isAddModalOpen}
        onClose={onCloseAddModal ?? (() => {})}
        role={role}
        roleId={selectedRoleId}
      />

      {selectedUser && (
        <UserPermissionDrawer
          isOpen={!!selectedUser}
          onClose={() => setSelectedUserId(null)}
          user={selectedUser}
          role={role}
          onUpdateModelAccess={onUpdateUserModelAccess}
        />
      )}
    </div>
  )
}

interface StatusBadgeProps {
  status: UserStatus
}

function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs border ${STATUS_STYLES[status]}`}
    >
      {status}
    </span>
  )
}
