import { useMemo, useState } from 'react'
import { Building2, Search, Settings2, UserPlus } from 'lucide-react'
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
import { cn } from '@/lib/utils'
import type {
  AiAccessLevel,
  RoleDefinition,
  UserItem,
  UserStatus,
} from './Permission'
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

interface MembersListProps {
  role: RoleDefinition
  selectedRoleId: string
  users: UserItem[]
  onUpdateUserModelAccess: (
    userId: string,
    modelId: string,
    access: AiAccessLevel,
  ) => void
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
  showToolbar = true,
  isAddModalOpen = false,
  onOpenAddModal,
  onCloseAddModal,
}: MembersListProps) {
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)

  const assignedUsers = useMemo(
    () => users.filter((user) => user.roleId === role.id),
    [users, role.id],
  )

  const selectedUser = useMemo(
    () => users.find((user) => user.id === selectedUserId) ?? null,
    [users, selectedUserId],
  )

  return (
    <div className="flex flex-col gap-6">
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
            <TableHead className="px-6 py-3.5 text-center">权限配置</TableHead>
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
              return (
                <TableRow
                  key={user.id}
                  className="hover:bg-slate-50/80 dark:hover:bg-slate-800/70 transition-colors"
                >
                  <TableCell className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <PermissionAvatar
                        name={user.name}
                        src={user.avatarUrl}
                        size="sm"
                      />
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
                    <Button
                      size="small"
                      variant="outline"
                      onClick={() => setSelectedUserId(user.id)}
                      className="border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300"
                    >
                      <Settings2 size={14} />
                      配置权限
                    </Button>
                  </TableCell>
                </TableRow>
              )
            })
          )}
        </TableBody>
      </Table>

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
