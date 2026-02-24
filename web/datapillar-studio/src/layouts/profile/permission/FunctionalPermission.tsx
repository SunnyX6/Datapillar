import { useMemo, useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import type { RoleDefinition } from './Permission'
import type { PermissionLevel } from './permissionConstants'
import { PERMISSION_LEVELS } from './permissionConstants'

const PERMISSION_LABELS: Record<PermissionLevel, string> = {
  DISABLE: '禁止',
  READ: '查看',
  ADMIN: '管理'
}

interface FunctionalPermissionProps {
  role: RoleDefinition
  onUpdatePermission: (objectId: number, level: PermissionLevel) => void
  className?: string
  readonly?: boolean
}

type PermissionItem = RoleDefinition['permissions'][number]

function buildPermissionMeta(permission: PermissionItem): string {
  const fields = [
    permission.objectPath,
    permission.objectType,
    permission.location,
  ].filter((item): item is string => Boolean(item && item.trim()))
  if (fields.length === 0) {
    return '未配置对象信息'
  }
  return fields.join(' · ')
}

export function FunctionalPermission(props: FunctionalPermissionProps) {
  const permissions = props.role.permissions
  const [collapsedObjectIds, setCollapsedObjectIds] = useState<Set<number>>(
    new Set(),
  )

  const groupedPermissions = useMemo(() => {
    return permissions.reduce<Record<string, PermissionItem[]>>((acc, permission) => {
      if (!acc[permission.categoryName]) acc[permission.categoryName] = []
      acc[permission.categoryName].push(permission)
      return acc
    }, {})
  }, [permissions])

  const handlePermissionChange = (objectId: number, level: PermissionLevel) => {
    props.onUpdatePermission(objectId, level)
  }

  const handleToggleNode = (objectId: number) => {
    setCollapsedObjectIds((prev) => {
      const next = new Set(prev)
      if (next.has(objectId)) {
        next.delete(objectId)
      } else {
        next.add(objectId)
      }
      return next
    })
  }

  const renderPermissionNodes = (
    nodes: PermissionItem[],
    depth = 0,
  ): JSX.Element[] => {
    return nodes.map((permission) => {
      const hasChildren = permission.children.length > 0
      const isCollapsed = collapsedObjectIds.has(permission.objectId)
      return (
        <div key={permission.objectId}>
          <div
            data-testid={`permission-row-${permission.objectId}`}
            className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 py-2.5 px-3 group hover:bg-slate-50 dark:hover:bg-slate-800/70 transition-colors"
          >
            <div
              data-testid={`permission-main-${permission.objectId}`}
              className="min-w-0 flex items-start gap-2.5 pr-2"
              style={{ paddingLeft: `${depth * 20}px` }}
            >
              <div className="mt-0.5 h-4 w-4 shrink-0 flex items-center justify-center">
                {hasChildren ? (
                  <button
                    type="button"
                    onClick={() => handleToggleNode(permission.objectId)}
                    className="shrink-0 text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-200 transition-colors"
                    aria-label={isCollapsed ? '展开子节点' : '收起子节点'}
                    aria-expanded={!isCollapsed}
                  >
                    {isCollapsed ? (
                      <ChevronRight size={14} />
                    ) : (
                      <ChevronDown size={14} />
                    )}
                  </button>
                ) : depth > 0 ? (
                  <span className="inline-block h-3 w-3 rounded-bl-sm border-l border-b border-slate-200 dark:border-slate-700" />
                ) : (
                  <span className="inline-block h-3 w-3" />
                )}
              </div>
              <div className="min-w-0">
                <div
                  className={cn(
                    TYPOGRAPHY.bodySm,
                    'font-medium text-slate-900 dark:text-white mb-0.5 truncate',
                  )}
                >
                  {permission.objectName}
                </div>
                <div
                  className={cn(
                    TYPOGRAPHY.caption,
                    'text-slate-500 dark:text-slate-400 truncate',
                  )}
                >
                  {buildPermissionMeta(permission)}
                </div>
              </div>
            </div>

            <div
              data-testid={`permission-actions-${permission.objectId}`}
              className="flex shrink-0 bg-slate-100 dark:bg-slate-800/80 p-0.5 rounded-lg border border-slate-200 dark:border-slate-700"
            >
              {PERMISSION_LEVELS.map((level) => {
                const isActive = permission.level === level
                let activeClass = ''
                if (isActive) {
                  if (level === 'ADMIN') activeClass = 'bg-white dark:bg-slate-900 text-brand-600 dark:text-brand-300 shadow-sm font-bold ring-1 ring-black/5 dark:ring-white/10'
                  else if (level === 'READ') activeClass = 'bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-300 shadow-sm font-bold ring-1 ring-black/5 dark:ring-white/10'
                  else activeClass = 'bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 shadow-sm font-bold ring-1 ring-black/5 dark:ring-white/10'
                } else {
                  activeClass = 'text-slate-500 hover:text-slate-700 hover:bg-white/50 dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-800/60'
                }

                return (
                  <button
                    key={level}
                    type="button"
                    onClick={() =>
                      handlePermissionChange(permission.objectId, level)
                    }
                    disabled={props.readonly || permission.tenantLevel === 'DISABLE'}
                    className={`px-2 py-0.5 rounded text-xs transition-all ${
                      props.readonly || permission.tenantLevel === 'DISABLE'
                        ? 'cursor-not-allowed opacity-50'
                        : ''
                    } ${activeClass}`}
                  >
                    {PERMISSION_LABELS[level]}
                  </button>
                )
              })}
            </div>
          </div>
          {hasChildren && !isCollapsed
            ? renderPermissionNodes(permission.children, depth + 1)
            : null}
        </div>
      )
    })
  }

  return (
    <div className={cn('space-y-4', props.className)}>
      {Object.entries(groupedPermissions).map(([category, categoryPermissions]) => (
        <div key={category}>
          <h3 className={cn(TYPOGRAPHY.micro, 'font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider mb-2 px-1')}>
            {category}
          </h3>
          <div className="bg-white dark:bg-slate-900/90 rounded-xl border border-slate-200 dark:border-slate-800 shadow-none dark:shadow-none overflow-hidden divide-y divide-slate-100 dark:divide-slate-800">
            {renderPermissionNodes(categoryPermissions)}
          </div>
        </div>
      ))}
    </div>
  )
}
