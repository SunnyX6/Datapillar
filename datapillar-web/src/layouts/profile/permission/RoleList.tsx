import { IdCard, MoreHorizontal, Plus } from 'lucide-react'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { RoleItem } from './Permission'

interface RoleListProps {
  roles: RoleItem[]
  selectedRoleId: string
  onSelectRole: (roleId: string) => void
}

export function RoleList({ roles, selectedRoleId, onSelectRole }: RoleListProps) {
  return (
    <div className="w-80 flex flex-col border-r border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 h-full">
      <div className="px-4 py-3 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center sticky top-0 bg-white/95 dark:bg-slate-900/95 backdrop-blur z-10">
        <h2 className={cn(TYPOGRAPHY.caption, 'font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider')}>角色列表</h2>
        <button type="button" className="text-slate-400 hover:text-brand-600 hover:bg-brand-50 p-1.5 rounded-md transition-all" aria-label="新增角色">
          <Plus size={16} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-1">
        {roles.map((role) => {
          const isSelected = selectedRoleId === role.id
          return (
            <div
              key={role.id}
              onClick={() => onSelectRole(role.id)}
              className={
                `group flex items-center gap-3 p-3 rounded-lg cursor-pointer transition-colors hover:shadow-sm ${
                  isSelected
                    ? 'text-blue-600 dark:text-blue-400'
                    : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-slate-100'
                }`
              }
            >
              <div
                className={
                  `w-8 h-8 rounded-md flex items-center justify-center text-xs shrink-0 transition-colors bg-slate-100 dark:bg-slate-800 ${
                    isSelected
                      ? 'text-blue-600 dark:text-blue-400'
                      : role.isSystem
                        ? 'text-brand-600'
                        : 'text-slate-500 dark:text-slate-400 group-hover:text-slate-700 dark:group-hover:text-slate-200'
                  }`
                }
              >
                <IdCard size={14} />
              </div>

              <div className="flex-1 min-w-0">
                <div className="flex justify-between items-center mb-0.5">
                  <span
                    className={cn(
                      TYPOGRAPHY.bodyXs,
                      `font-medium truncate ${isSelected ? 'text-blue-600 dark:text-blue-400' : 'text-slate-600 dark:text-slate-300'}`
                    )}
                  >
                    {role.name}
                  </span>
                  {role.isSystem && (
                    <span className={cn(TYPOGRAPHY.micro, 'text-slate-400 bg-slate-100 dark:bg-slate-800 px-1.5 rounded ml-2 shrink-0')}>
                      系统
                    </span>
                  )}
                </div>
                <div
                  className={cn(
                    TYPOGRAPHY.caption,
                    `truncate flex justify-between ${isSelected ? 'text-blue-400 dark:text-blue-300' : 'text-slate-400'}`
                  )}
                >
                  <span>{role.userCount} 位成员</span>
                </div>
              </div>

              {isSelected && (
                <button type="button" className="opacity-0 group-hover:opacity-100 p-1 hover:bg-slate-200 rounded text-slate-400" aria-label="角色操作">
                  <MoreHorizontal size={14} />
                </button>
              )}
            </div>
          )
        })}
      </div>

      <div className="p-4 border-t border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/60">
        <div className={cn(TYPOGRAPHY.caption, 'text-slate-400 text-center')}>
          共 {roles.length} 个角色 · <span className="hover:text-brand-600 cursor-pointer hover:underline">管理元数据</span>
        </div>
      </div>
    </div>
  )
}
