import { useState } from 'react'
import { Check, Plus, Search } from 'lucide-react'
import { Button, Card } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { RoleItem } from '../permission/Permission'
import type { PermissionLevel } from '../permission/permissionConstants'

interface FeatureStepGrantProps {
  roles: RoleItem[]
  initialPerms: Record<string, PermissionLevel>
  permissionLevels: PermissionLevel[]
  onPermissionChange: (roleId: string, level: PermissionLevel) => void
  cardClassName: string
  sectionTitleClassName: string
}

export function FeatureStepGrant({
  roles,
  initialPerms,
  permissionLevels,
  onPermissionChange,
  cardClassName,
  sectionTitleClassName
}: FeatureStepGrantProps) {
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([])
  const [pendingLevel, setPendingLevel] = useState<PermissionLevel | null>(null)
  const selectedCount = selectedRoleIds.length
  const isAllSelected = roles.length > 0 && roles.every((role) => selectedRoleIds.includes(role.id))

  const toggleRoleSelection = (roleId: string) => {
    setSelectedRoleIds((prev) => {
      const next = prev.includes(roleId) ? prev.filter((id) => id !== roleId) : [...prev, roleId]
      if (next.length === 0) setPendingLevel(null)
      return next
    })
  }

  const toggleSelectAll = () => {
    if (isAllSelected) {
      setSelectedRoleIds([])
      setPendingLevel(null)
      return
    }
    setSelectedRoleIds(roles.map((role) => role.id))
  }

  const applyPermission = (roleId: string, level: PermissionLevel) => {
    if (selectedRoleIds.length > 0) {
      setPendingLevel(level)
      return
    }
    onPermissionChange(roleId, level)
  }

  const handleBulkApply = () => {
    if (!pendingLevel || selectedRoleIds.length === 0) return
    selectedRoleIds.forEach((id) => onPermissionChange(id, pendingLevel))
    setSelectedRoleIds([])
    setPendingLevel(null)
  }

  return (
    <div className="space-y-4 animate-in slide-in-from-right-4 duration-300">
      <div className="space-y-2">
        <div className="flex items-center justify-between px-1">
          <h3 className={sectionTitleClassName}>默认授权矩阵 (Inheritance)</h3>
          <Button size="small" variant="ghost" className={cn(TYPOGRAPHY.micro, 'text-brand-600 hover:text-brand-700 dark:text-brand-300 dark:hover:text-brand-200')}>
            <Plus size={12} />
            添加角色
          </Button>
        </div>
        <Card variant="default" padding="none" className={cn(cardClassName, 'overflow-hidden shadow-none dark:shadow-none')}>
          <div className="flex items-center justify-between px-3 py-3 border-b border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900/80">
            <div className="relative w-72">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500" />
              <input
                type="text"
                placeholder="搜索角色名或 ID..."
                className={cn(
                  TYPOGRAPHY.legal,
                  'w-full pl-9 pr-3 py-2 bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-full outline-none focus:border-brand-400 dark:focus:border-brand-400/70 focus:ring-2 focus:ring-brand-500/10 dark:focus:ring-brand-500/20 text-slate-600 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500'
                )}
              />
            </div>
            <div className="flex items-center gap-5 flex-wrap justify-end">
              <div className={cn(TYPOGRAPHY.legal, 'flex items-center gap-2 text-slate-500 dark:text-slate-400')}>
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                系统角色
              </div>
              <div className={cn(TYPOGRAPHY.legal, 'flex items-center gap-2 text-slate-400 dark:text-slate-500')}>
                <span className="w-2 h-2 rounded-full bg-slate-300 dark:bg-slate-600" />
                自定义角色
              </div>
            </div>
          </div>
          <div className={cn('relative', selectedCount > 0 && 'pb-16')}>
            <table className="w-full border-collapse table-auto">
              <thead className="bg-slate-50 dark:bg-slate-800/60 text-slate-400 dark:text-slate-400 uppercase tracking-wider border-b border-slate-100 dark:border-slate-800">
                <tr className={cn(TYPOGRAPHY.micro, 'font-bold')}>
                  <th className="px-3 py-2 w-10 text-left">
                    <button
                      type="button"
                      onClick={toggleSelectAll}
                      className={cn(
                        'inline-flex w-4 h-4 rounded border items-center justify-center transition-colors',
                        isAllSelected
                          ? 'bg-brand-600 border-brand-600 dark:bg-brand-500/80 dark:border-brand-400'
                          : 'border-slate-300 bg-white hover:border-slate-400 dark:border-slate-600 dark:bg-slate-900 dark:hover:border-slate-500'
                      )}
                    >
                      {isAllSelected && <Check size={8} className="text-white" />}
                    </button>
                  </th>
                  <th className="px-3 py-2 text-left">角色身份 (IDENTITY)</th>
                  <th className="px-3 py-2 text-left w-40">覆盖成员 (REACH)</th>
                  <th className="px-3 py-2 text-left w-40">权限溯源 (ORIGIN)</th>
                  <th className="px-3 py-2 text-right">授权策略 (POLICY)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50 dark:divide-slate-800">
                {roles.map((role) => {
                  const current = initialPerms[role.id] ?? 'NONE'
                  return (
                    <tr
                      key={role.id}
                      className={cn(
                        'hover:bg-slate-50/50 dark:hover:bg-slate-800/40 transition-colors',
                        selectedRoleIds.includes(role.id) && 'bg-brand-50/40 dark:bg-brand-500/10'
                      )}
                    >
                      <td className="px-3 py-2">
                        <button
                          type="button"
                          onClick={() => toggleRoleSelection(role.id)}
                          className={cn(
                            'inline-flex w-4 h-4 rounded border items-center justify-center transition-colors',
                            selectedRoleIds.includes(role.id)
                              ? 'bg-brand-600 border-brand-600 dark:bg-brand-500/80 dark:border-brand-400'
                              : 'border-slate-300 bg-white hover:border-slate-400 dark:border-slate-600 dark:bg-slate-900 dark:hover:border-slate-500'
                          )}
                        >
                          {selectedRoleIds.includes(role.id) && <Check size={8} className="text-white" />}
                        </button>
                      </td>
                      <td className="px-3 py-2">
                        <div className={cn(TYPOGRAPHY.legal, 'font-black text-slate-700 dark:text-slate-100')}>{role.name}</div>
                      </td>
                      <td className="px-3 py-2">
                        <div className={cn(TYPOGRAPHY.legal, 'text-slate-400 dark:text-slate-500')}>{role.userCount} 成员受影响</div>
                      </td>
                      <td className="px-3 py-2">
                        <div className={cn(TYPOGRAPHY.legal, 'text-slate-400 dark:text-slate-500')}>{role.isSystem ? '系统角色' : '自定义角色'}</div>
                      </td>
                      <td className="px-3 py-2 text-right">
                        <div className="flex justify-end">
                          <div className="inline-flex bg-slate-100 dark:bg-slate-800/80 p-1 rounded-lg border border-slate-200 dark:border-slate-700 gap-1">
                            {permissionLevels.map((level) => (
                              <button
                                key={level}
                                type="button"
                                onClick={() => applyPermission(role.id, level)}
                                className={cn(
                                  TYPOGRAPHY.nano,
                                  `px-2.5 py-0.5 rounded-md font-black transition-all ${
                                    current === level
                                      ? 'bg-white shadow-sm text-brand-600 dark:bg-slate-900 dark:text-brand-300 dark:shadow-none'
                                      : 'text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-200 dark:hover:bg-slate-800/60'
                                  }`
                                )}
                              >
                                {level === 'NONE' ? '禁止' : level === 'READ' ? '只读' : '管理'}
                              </button>
                            ))}
                          </div>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>

            {selectedCount > 0 && (
              <div className="absolute bottom-3 right-4 z-20">
                <div className="bg-slate-900 dark:bg-slate-800 text-white dark:text-slate-100 px-3 py-1 rounded-full shadow-xl shadow-slate-900/20 flex items-center gap-2 border border-slate-800 dark:border-slate-700">
                  <div className={cn(TYPOGRAPHY.caption, 'font-medium whitespace-nowrap')}>已选 {selectedCount} 项</div>
                  {pendingLevel && (
                    <div className={cn(TYPOGRAPHY.caption, 'text-slate-300 dark:text-slate-400 whitespace-nowrap')}>
                      策略：{pendingLevel === 'NONE' ? '禁止' : pendingLevel === 'READ' ? '只读' : '管理'}
                    </div>
                  )}
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedRoleIds([])
                      setPendingLevel(null)
                    }}
                    className="text-xs text-slate-400 dark:text-slate-400 hover:text-white transition-colors"
                  >
                    清空
                  </button>
                  <Button
                    size="small"
                    variant="primary"
                    disabled={!pendingLevel}
                    className="bg-brand-500 hover:bg-brand-400 dark:bg-brand-400 dark:hover:bg-brand-300 text-white border-0 shadow-lg shadow-brand-500/25 px-3 py-1 h-7"
                    onClick={handleBulkApply}
                  >
                    确认授权
                  </Button>
                </div>
              </div>
            )}
          </div>
        </Card>
      </div>
    </div>
  )
}
