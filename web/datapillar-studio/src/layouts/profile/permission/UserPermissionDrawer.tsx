import { useState } from 'react'
import { createPortal } from 'react-dom'
import { Database, Save, Shield, SlidersHorizontal, X } from 'lucide-react'
import { Button } from '@/components/ui'
import { drawerWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { RoleDefinition, UserItem } from './Permission'
import type { PermissionLevel } from './permissionConstants'
import { DataPermission } from './DataPermission'
import { FunctionalPermission } from './FunctionalPermission'
import { PermissionAvatar } from './PermissionAvatar'

interface UserPermissionDrawerProps {
  isOpen: boolean
  onClose: () => void
  user: UserItem
  role: RoleDefinition
  onUpdatePermissions: (userId: string, resourceId: string, level: PermissionLevel) => void
}

export function UserPermissionDrawer({ isOpen, onClose, user, role, onUpdatePermissions }: UserPermissionDrawerProps) {
  const [activeTab, setActiveTab] = useState<'functional' | 'data'>('functional')

  if (!isOpen) return null
  if (typeof document === 'undefined') return null

  return createPortal(
    <aside
      className={cn(
        'fixed right-0 top-14 bottom-0 z-30 bg-white dark:bg-slate-900/90 shadow-2xl border-l border-slate-200 dark:border-slate-800 flex flex-col animate-in slide-in-from-right duration-500',
        drawerWidthClassMap.wide
      )}
    >
      <div className="px-6 py-6 bg-white dark:bg-slate-900/90 flex-shrink-0">
        <div className="flex justify-between items-start">
          <div className="flex items-center gap-4">
            <PermissionAvatar name={user.name} src={user.avatarUrl} size="lg" className="ring-4 ring-white dark:ring-slate-800 shadow-sm" />
            <div>
              <h2 className={cn(TYPOGRAPHY.heading, 'font-bold text-slate-900 dark:text-white')}>{user.name} 的权限配置</h2>
              <div className={cn(TYPOGRAPHY.caption, 'flex items-center gap-2 mt-1 text-slate-500 dark:text-slate-400')}>
                <span>所属角色:</span>
                <span
                  className={cn(
                    TYPOGRAPHY.caption,
                    'inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full font-medium bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200'
                  )}
                >
                  <Shield size={10} /> {role.name}
                </span>
                {user.department && (
                  <>
                    <span className="w-1 h-1 bg-slate-300 dark:bg-slate-600 rounded-full mx-1" />
                    <span>部门: {user.department}</span>
                  </>
                )}
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 hover:bg-slate-200 dark:hover:bg-slate-800 rounded-full text-slate-400 dark:text-slate-500 transition-colors"
            aria-label="关闭"
          >
            <X size={20} />
          </button>
        </div>
      </div>

      <div className="px-6 pt-3 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/90 flex-shrink-0">
        <div className="flex gap-8">
          <button
            type="button"
            onClick={() => setActiveTab('functional')}
            className={cn(
              TYPOGRAPHY.bodySm,
              'pb-3 font-medium border-b-2 transition-all flex items-center gap-2',
              activeTab === 'functional'
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
            )}
          >
            <SlidersHorizontal size={16} />
            功能权限 (Functional)
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('data')}
            className={cn(
              TYPOGRAPHY.bodySm,
              'pb-3 font-medium border-b-2 transition-all flex items-center gap-2',
              activeTab === 'data'
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
            )}
          >
            <Database size={16} />
            数据权限 (Gravitino)
            {user.dataPrivileges && user.dataPrivileges.length > 0 && <span className="w-2 h-2 rounded-full bg-brand-500" />}
          </button>
        </div>
      </div>

      <div className="flex-1 min-h-0 bg-slate-50/30 dark:bg-slate-950/35 overflow-hidden">
        {activeTab === 'functional' && (
          <div className="h-full overflow-y-auto p-6 custom-scrollbar animate-in fade-in duration-200">
            <FunctionalPermission
              mode="user"
              role={role}
              user={user}
              onUpdatePermission={onUpdatePermissions}
              className="space-y-6"
            />
          </div>
        )}

        {activeTab === 'data' && (
          <div className="h-full p-6 animate-in fade-in duration-200">
            <DataPermission key={user.id} user={user} />
          </div>
        )}
      </div>

      <div className="px-6 py-4 bg-white dark:bg-slate-900/90 border-t border-slate-200 dark:border-slate-800 flex justify-between items-center z-10 shrink-0">
        <div className={cn(TYPOGRAPHY.caption, 'text-slate-400 dark:text-slate-500')}>修改即时生效</div>
        <div className="flex gap-3">
          <Button size="small" onClick={onClose} className="bg-brand-600 text-white hover:bg-brand-700">
            <Save size={16} />
            完成配置
          </Button>
        </div>
      </div>
    </aside>,
    document.body
  )
}
