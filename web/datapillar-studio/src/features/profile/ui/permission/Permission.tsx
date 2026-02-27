import { Link2, Shield, Users } from 'lucide-react'
import { Button } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import { usePermissionViewModel } from '../../hooks'
import { FunctionalPermission } from './FunctionalPermission'
import { MembersList } from './MembersList'
import { RoleList } from './RoleList'

export type {
  AiAccessLevel,
  AiModelPermission,
  PermissionResource,
  RoleDefinition,
  RoleItem,
  RoleType,
  UserDataPrivilege,
  UserItem,
  UserStatus,
} from '../../utils/permissionTypes'

export function PermissionLayout() {
  const {
    roles,
    selectedRole,
    selectedRoleId,
    activeTab,
    isAddModalOpen,
    selectedRoleUsers,
    selectedRolePreviewUsers,
    membersLoading,
    functionalLoading,
    setSelectedRoleId,
    setActiveTab,
    setAddModalOpen,
    createRole,
    updateRole,
    deleteRole,
    updateRolePermission,
    updateUserModelAccess,
    deleteMembers,
  } = usePermissionViewModel()

  if (!selectedRole) {
    return null
  }

  const showMembersSkeleton =
    activeTab === 'members' && membersLoading && selectedRoleUsers.length === 0
  const showFunctionalSkeleton =
    activeTab === 'functional' &&
    functionalLoading &&
    selectedRole.permissions.length === 0

  return (
    <section className="flex h-full w-full overflow-hidden bg-slate-50 dark:bg-[#0f172a] @container">
      <div className="flex h-full w-full overflow-hidden">
        <RoleList
          roles={roles}
          selectedRoleId={selectedRoleId}
          onSelectRole={setSelectedRoleId}
          onCreateRole={createRole}
          onUpdateRole={updateRole}
          onDeleteRole={deleteRole}
        />
        <div className="flex-1 overflow-hidden">
          <div className="h-full overflow-hidden flex flex-col bg-white dark:bg-slate-900 shadow-sm dark:shadow-[0_16px_40px_-16px_rgba(0,0,0,0.45)]">
            <div className="px-8 py-6 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90">
              <div className="flex items-center justify-between gap-6">
                <div>
                  <div className="flex items-center gap-3">
                    <h2
                      className={cn(
                        TYPOGRAPHY.body,
                        'font-semibold text-slate-900 dark:text-white tracking-tight',
                      )}
                    >
                      {selectedRole.name}
                    </h2>
                  </div>
                  <div className="flex items-center gap-4 mt-2 min-h-6">
                    <div className="flex -space-x-1.5 min-h-6">
                      {selectedRolePreviewUsers.map((user) => (
                        <div
                          key={user.id}
                          className="w-6 h-6 rounded-full bg-slate-100 dark:bg-slate-800 border-2 border-white dark:border-slate-900 flex items-center justify-center text-tiny font-black text-slate-600 dark:text-slate-200"
                        >
                          {user.name.slice(0, 2).toUpperCase()}
                        </div>
                      ))}
                    </div>
                    <span
                      className={cn(
                        TYPOGRAPHY.caption,
                        'font-semibold text-slate-600 dark:text-slate-300',
                      )}
                    >
                      共 {selectedRole.userCount} 名成员
                    </span>
                  </div>
                </div>
                <Button
                  size="header"
                  onClick={() => {
                    setActiveTab('members')
                    setAddModalOpen(true)
                  }}
                  className="shadow-lg shadow-slate-200/60 dark:shadow-none"
                >
                  <Link2 size={14} />
                  邀请成员
                </Button>
              </div>
            </div>
            <div className="h-12 px-6 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90 flex items-stretch">
              <div className="flex h-full items-stretch justify-between gap-4 w-full">
                <div className="flex h-full items-stretch gap-8">
                  <button
                    type="button"
                    onClick={() => setActiveTab('members')}
                    className={cn(
                      TYPOGRAPHY.bodySm,
                      'h-full -mb-px font-medium border-b-2 transition-all inline-flex items-center gap-2 leading-none',
                      activeTab === 'members'
                        ? 'border-brand-600 text-brand-600'
                        : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
                    )}
                  >
                    <Users size={16} />
                    成员列表
                  </button>
                  <button
                    type="button"
                    onClick={() => setActiveTab('functional')}
                    className={cn(
                      TYPOGRAPHY.bodySm,
                      'h-full -mb-px font-medium border-b-2 transition-all inline-flex items-center gap-2 leading-none',
                      activeTab === 'functional'
                        ? 'border-brand-600 text-brand-600'
                        : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200',
                    )}
                  >
                    <Shield size={16} />
                    功能权限
                  </button>
                </div>
              </div>
            </div>
            <div
              className={cn(
                'flex-1 overflow-y-auto bg-slate-50 dark:bg-slate-950/35 custom-scrollbar',
                activeTab === 'functional' ? 'px-0 py-0' : 'px-6 py-6',
              )}
            >
              {activeTab === 'members' && (
                showMembersSkeleton ? (
                  <MembersLoadingSkeleton />
                ) : (
                  <MembersList
                    role={selectedRole}
                    selectedRoleId={selectedRoleId}
                    users={selectedRoleUsers}
                    onUpdateUserModelAccess={updateUserModelAccess}
                    onDeleteUsers={deleteMembers}
                    showToolbar={false}
                    isAddModalOpen={isAddModalOpen}
                    onOpenAddModal={() => setAddModalOpen(true)}
                    onCloseAddModal={() => setAddModalOpen(false)}
                  />
                )
              )}
              {activeTab === 'functional' && (
                showFunctionalSkeleton ? (
                  <FunctionalLoadingSkeleton />
                ) : (
                  <FunctionalPermission
                    role={selectedRole}
                    onUpdatePermission={(objectId, level) => {
                      void updateRolePermission(objectId, level)
                    }}
                    readonly={Boolean(selectedRole.isSystem)}
                    className="px-6 py-6 animate-in fade-in zoom-in-95 duration-200"
                  />
                )
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

function MembersLoadingSkeleton() {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900 animate-pulse">
      <div className="space-y-4">
        <div className="h-9 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
        <div className="h-9 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
        <div className="h-9 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
        <div className="h-9 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
        <div className="h-9 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
      </div>
    </div>
  )
}

function FunctionalLoadingSkeleton() {
  return (
    <div className="px-6 py-6 animate-pulse">
      <div className="rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
        <div className="space-y-3">
          <div className="h-8 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
          <div className="h-8 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
          <div className="h-8 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
          <div className="h-8 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
          <div className="h-8 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
          <div className="h-8 w-full rounded-lg bg-slate-100 dark:bg-slate-800" />
        </div>
      </div>
    </div>
  )
}
