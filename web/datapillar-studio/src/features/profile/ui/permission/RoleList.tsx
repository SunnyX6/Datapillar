import { useMemo, useState } from 'react'
import { ChevronRight, Fingerprint, Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { Modal, ModalCancelButton, ModalPrimaryButton, Tooltip } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import type { RoleItem, RoleType } from '../../utils/permissionTypes'
import { CreateRoleModal } from './CreateRoleModal'

interface RoleListProps {
  roles: RoleItem[]
  selectedRoleId: string
  onSelectRole: (roleId: string) => void
  onCreateRole: (payload: {
    name: string
    description?: string
    type: RoleType
  }) => Promise<boolean> | boolean
  onUpdateRole?: (
    roleId: string,
    payload: {
      name: string
      description?: string
      type: RoleType
    }
  ) => Promise<boolean> | boolean
  onDeleteRole?: (roleId: string) => Promise<boolean> | boolean
}

export function RoleList({
  roles,
  selectedRoleId,
  onSelectRole,
  onCreateRole,
  onUpdateRole,
  onDeleteRole,
}: RoleListProps) {
  const [keyword, setKeyword] = useState('')
  const [isCreateRoleOpen, setIsCreateRoleOpen] = useState(false)
  const [editingRole, setEditingRole] = useState<RoleItem | null>(null)
  const [deletingRole, setDeletingRole] = useState<RoleItem | null>(null)
  const [deletingRoleSubmitting, setDeletingRoleSubmitting] = useState(false)

  const filteredRoles = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase()
    if (!normalizedKeyword) {
      return roles
    }
    return roles.filter(
      (role) =>
        role.name.toLowerCase().includes(normalizedKeyword) ||
        role.id.toLowerCase().includes(normalizedKeyword),
    )
  }, [keyword, roles])

  const editExistingNames = useMemo(
    () =>
      editingRole
        ? roles
            .filter((role) => role.id !== editingRole.id)
            .map((role) => role.name)
        : [],
    [editingRole, roles],
  )

  const handleDeleteRole = async () => {
    if (!deletingRole || !onDeleteRole || deletingRoleSubmitting) {
      return
    }
    setDeletingRoleSubmitting(true)
    try {
      const deleted = await onDeleteRole(deletingRole.id)
      if (deleted === false) {
        return
      }
      setDeletingRole(null)
    } finally {
      setDeletingRoleSubmitting(false)
    }
  }

  return (
    <div className="w-80 flex flex-col border-r border-slate-200 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-900/70 h-full">
      <div className="px-6 py-5 border-b border-slate-200 dark:border-slate-800/80 bg-slate-50/90 dark:bg-slate-900/80 backdrop-blur z-10">
        <div className="flex justify-between items-center mb-4">
          <h2
            className={cn(
              TYPOGRAPHY.micro,
              'font-semibold text-slate-500 dark:text-slate-300 uppercase tracking-widest flex items-center gap-2',
            )}
          >
            <Fingerprint size={12} />
            身份角色
          </h2>
          <button
            type="button"
            onClick={() => setIsCreateRoleOpen(true)}
            className="text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 p-1.5 rounded-md transition-all"
            aria-label="新增角色"
          >
            <Plus size={16} />
          </button>
        </div>
        <div className="relative group">
          <Search
            className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500"
            size={14}
          />
          <input
            type="text"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索角色名称..."
            className={cn(
              TYPOGRAPHY.caption,
              'w-full pl-9 pr-4 py-2 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:border-brand-500 transition-all text-slate-800 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500',
            )}
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-1">
        {filteredRoles.map((role) => {
          const isSelected = selectedRoleId === role.id
          const canEditRole = !role.isSystem
          const canDeleteRole = !role.isSystem && role.userCount === 0
          const editDisabledReason = role.isSystem
            ? '平台超管角色不允许编辑'
            : '编辑角色'
          const deleteDisabledReason = role.isSystem
            ? '平台超管角色不允许删除'
            : role.userCount > 0
              ? '角色下存在成员，无法删除'
              : '删除角色'

          return (
            <div
              key={role.id}
              className={`w-full text-left group px-4 py-3.5 rounded-xl transition-all ${
                isSelected
                  ? 'bg-white dark:bg-slate-900 ring-1 ring-slate-200 dark:ring-slate-700 shadow-sm'
                  : 'hover:bg-slate-100/80 dark:hover:bg-slate-800/80'
              }`}
            >
              <div className="flex items-start gap-2">
                <button
                  type="button"
                  onClick={() => onSelectRole(role.id)}
                  className="flex-1 text-left"
                >
                  <div className="flex items-center mb-0.5">
                    <span
                      className={cn(
                        TYPOGRAPHY.bodyXs,
                        isSelected
                          ? 'text-slate-900 dark:text-white font-semibold'
                          : 'text-slate-600 dark:text-slate-300 font-medium',
                      )}
                    >
                      {role.name}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span
                      className={cn(
                        TYPOGRAPHY.nano,
                        isSelected
                          ? 'text-slate-500 dark:text-slate-300 font-semibold'
                          : 'text-slate-400 dark:text-slate-500',
                      )}
                    >
                      {role.userCount} 位成员
                    </span>
                    <span
                      className={cn(
                        TYPOGRAPHY.micro,
                        'px-1.5 py-0.5 rounded border',
                        role.type === 'ADMIN'
                          ? 'border-indigo-200 bg-indigo-50 text-indigo-700 dark:border-indigo-500/30 dark:bg-indigo-500/10 dark:text-indigo-300'
                          : 'border-slate-200 bg-slate-100 text-slate-600 dark:border-slate-600 dark:bg-slate-700/70 dark:text-slate-300',
                      )}
                    >
                      {role.type}
                    </span>
                    <span
                      className={cn(
                        'w-1.5 h-1.5 rounded-full',
                        role.isSystem || role.userCount > 0
                          ? 'bg-emerald-400'
                          : 'bg-slate-300',
                      )}
                    />
                  </div>
                </button>
                <div
                  className={cn(
                    'flex items-center gap-1 pt-0.5 transition-opacity',
                    isSelected
                      ? 'opacity-100 pointer-events-auto'
                      : 'opacity-0 pointer-events-none group-hover:opacity-100 group-hover:pointer-events-auto',
                  )}
                >
                  <Tooltip content={editDisabledReason} side="top">
                    <button
                      type="button"
                      aria-label={`编辑角色-${role.name}`}
                      disabled={!canEditRole}
                      onClick={() => {
                        if (!canEditRole) {
                          return
                        }
                        setEditingRole(role)
                      }}
                      className={cn(
                        'rounded-md p-1 transition-colors',
                        canEditRole
                          ? 'text-slate-400 hover:text-slate-700 hover:bg-slate-200/70 dark:text-slate-500 dark:hover:text-slate-200 dark:hover:bg-slate-700'
                          : 'text-slate-300 dark:text-slate-700 cursor-not-allowed',
                      )}
                    >
                      <Pencil size={14} />
                    </button>
                  </Tooltip>
                  <Tooltip content={deleteDisabledReason} side="top">
                    <button
                      type="button"
                      aria-label={`删除角色-${role.name}`}
                      disabled={!canDeleteRole}
                      onClick={() => {
                        if (!canDeleteRole) {
                          return
                        }
                        setDeletingRole(role)
                      }}
                      className={cn(
                        'rounded-md p-1 transition-colors',
                        canDeleteRole
                          ? 'text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:text-slate-500 dark:hover:text-rose-400 dark:hover:bg-rose-500/10'
                          : 'text-slate-300 dark:text-slate-700 cursor-not-allowed',
                      )}
                    >
                      <Trash2 size={14} />
                    </button>
                  </Tooltip>
                  <ChevronRight
                    size={14}
                    className={cn(
                      isSelected
                        ? 'text-slate-500'
                        : 'text-slate-300',
                    )}
                  />
                </div>
              </div>
            </div>
          )
        })}
      </div>

      <CreateRoleModal
        isOpen={isCreateRoleOpen}
        onClose={() => setIsCreateRoleOpen(false)}
        existingNames={roles.map((role) => role.name)}
        onCreate={onCreateRole}
      />
      <CreateRoleModal
        isOpen={Boolean(editingRole)}
        onClose={() => setEditingRole(null)}
        existingNames={editExistingNames}
        initialValues={
          editingRole
            ? {
                name: editingRole.name,
                description: editingRole.description,
                type: editingRole.type,
              }
            : undefined
        }
        title="编辑角色"
        submitLabel="保存修改"
        submittingLabel="保存中..."
        onCreate={async (payload) => {
          if (!editingRole || !onUpdateRole) {
            return false
          }
          return onUpdateRole(editingRole.id, payload)
        }}
      />
      <Modal
        isOpen={Boolean(deletingRole)}
        onClose={() => {
          if (deletingRoleSubmitting) {
            return
          }
          setDeletingRole(null)
        }}
        size="mini"
        title="删除角色"
        subtitle={
          deletingRole ? (
            <p className={cn(TYPOGRAPHY.caption, 'text-slate-500 dark:text-slate-400')}>
              确认删除角色「{deletingRole.name}」？该操作不可恢复。
            </p>
          ) : null
        }
        footerLeft={
          <ModalCancelButton
            disabled={deletingRoleSubmitting}
            onClick={() => setDeletingRole(null)}
          >
            取消
          </ModalCancelButton>
        }
        footerRight={
          <ModalPrimaryButton
            loading={deletingRoleSubmitting}
            disabled={deletingRoleSubmitting}
            variant="amber"
            onClick={() => void handleDeleteRole()}
          >
            确认删除
          </ModalPrimaryButton>
        }
      >
        <p className={cn(TYPOGRAPHY.bodySm, 'text-slate-600 dark:text-slate-300')}>
          删除后，该角色对应的权限配置将无法恢复。
        </p>
      </Modal>
    </div>
  )
}
