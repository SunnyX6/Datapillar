import { useEffect, useMemo, useState, type MouseEvent } from 'react'
import {
  AlertCircle,
  ChevronDown,
  ChevronRight,
  Database,
  Folder,
  Layers,
  Lock,
  Server,
  Shield,
  Table,
  Unlock
} from 'lucide-react'
import { cn } from '@/lib/utils'
import type { UserItem } from './Permission'

type AssetType = 'metalake' | 'catalog' | 'schema' | 'table'

interface GravitinoAsset {
  id: string
  name: string
  type: AssetType
  description: string
  children?: GravitinoAsset[]
}

const MOCK_GRAVITINO_ASSETS: GravitinoAsset[] = [
  {
    id: 'metalake_prod',
    name: 'Prod Metalake',
    type: 'metalake',
    description: '生产环境数据资产与平台元数据集合。',
    children: [
      {
        id: 'cat_pg_payment',
        name: 'pg_payment',
        type: 'catalog',
        description: '支付域主目录。',
        children: [
          {
            id: 'sch_core',
            name: 'core',
            type: 'schema',
            description: '核心业务数据模式。',
            children: [
              {
                id: 'tbl_orders',
                name: 'fact_orders',
                type: 'table',
                description: '订单明细事实表。'
              },
              {
                id: 'tbl_refunds',
                name: 'fact_refunds',
                type: 'table',
                description: '退款明细事实表。'
              }
            ]
          },
          {
            id: 'sch_dim',
            name: 'dim',
            type: 'schema',
            description: '公共维度数据模式。',
            children: [
              {
                id: 'tbl_users',
                name: 'dim_users',
                type: 'table',
                description: '用户维度表。'
              }
            ]
          }
        ]
      },
      {
        id: 'cat_pg_marketing',
        name: 'pg_marketing',
        type: 'catalog',
        description: '营销域数据资产目录。',
        children: [
          {
            id: 'sch_ads',
            name: 'ads',
            type: 'schema',
            description: '广告投放数据模式。',
            children: [
              {
                id: 'tbl_campaigns',
                name: 'fact_campaigns',
                type: 'table',
                description: '广告活动事实表。'
              }
            ]
          }
        ]
      }
    ]
  },
  {
    id: 'metalake_analytics',
    name: 'Analytics Metalake',
    type: 'metalake',
    description: '分析与BI相关的数据资产集合。',
    children: [
      {
        id: 'cat_warehouse',
        name: 'warehouse',
        type: 'catalog',
        description: '数仓统一目录。',
        children: [
          {
            id: 'sch_sales',
            name: 'sales',
            type: 'schema',
            description: '销售数据模式。',
            children: [
              {
                id: 'tbl_sales_daily',
                name: 'fact_sales_daily',
                type: 'table',
                description: '日级销售事实表。'
              }
            ]
          }
        ]
      }
    ]
  }
]

const Switch = ({
  checked,
  onChange,
  color = 'bg-brand-600',
  disabled
}: {
  checked: boolean
  onChange: () => void
  color?: string
  disabled?: boolean
}) => (
  <button
    type="button"
    onClick={onChange}
    disabled={disabled}
    className={cn(
      'relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-600 focus-visible:ring-offset-2',
      checked ? color : 'bg-slate-200',
      disabled ? 'opacity-50 cursor-not-allowed' : ''
    )}
  >
    <span className="sr-only">Use setting</span>
    <span
      aria-hidden="true"
      className={cn(
        'pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
        checked ? 'translate-x-4' : 'translate-x-0'
      )}
    />
  </button>
)

interface DataPermissionProps {
  user: UserItem
}

export function DataPermission({ user }: DataPermissionProps) {
  const [selectedAssetId, setSelectedAssetId] = useState<string>('metalake_prod')
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set(['metalake_prod', 'cat_pg_payment']))
  const [localPrivileges, setLocalPrivileges] = useState<Record<string, string[]>>({})

  useEffect(() => {
    const privs: Record<string, string[]> = {}
    user.dataPrivileges?.forEach((item) => {
      privs[item.assetId] = item.privileges
    })
    setLocalPrivileges(privs)
  }, [user])

  const findAsset = (nodes: GravitinoAsset[], id: string): GravitinoAsset | undefined => {
    for (const node of nodes) {
      if (node.id === id) return node
      if (node.children) {
        const found = findAsset(node.children, id)
        if (found) return found
      }
    }
    return undefined
  }

  const selectedAsset = findAsset(MOCK_GRAVITINO_ASSETS, selectedAssetId)

  const getPrivilegeGroups = (assetType: AssetType) => {
    const typeLabel =
      assetType === 'metalake' ? 'Metalake' : assetType === 'catalog' ? 'Catalog' : assetType === 'schema' ? 'Schema' : 'Table'

    const childTypeLabel =
      assetType === 'metalake' ? 'Catalog' : assetType === 'catalog' ? 'Schema' : assetType === 'schema' ? 'Table' : 'Column'

    return [
      {
        name: '元数据访问 (Metadata Access)',
        color: 'bg-blue-600',
        items: [
          { key: 'USAGE', label: 'USAGE', desc: `允许查看 ${typeLabel} 的属性、配置及元数据信息` },
          {
            key: 'SELECT',
            label: 'SELECT',
            desc: assetType === 'table' ? '允许查询表中的数据行' : `允许读取该 ${typeLabel} 下所有资源的元数据`
          }
        ]
      },
      {
        name: '资源与数据管理 (Data Management)',
        color: 'bg-emerald-600',
        items: [
          {
            key: 'CREATE',
            label: 'CREATE',
            desc: assetType === 'table' ? '允许创建子分区或索引' : `允许在此 ${typeLabel} 下创建新的 ${childTypeLabel}`
          },
          { key: 'MODIFY', label: 'MODIFY', desc: '允许执行 UPDATE / INSERT 等数据变更操作' },
          { key: 'ALTER', label: 'ALTER', desc: `允许修改 ${typeLabel} 的结构（如增加列、修改属性）` }
        ]
      },
      {
        name: '高危操作 (Danger Zone)',
        color: 'bg-rose-600',
        danger: true,
        items: [
          { key: 'DROP', label: 'DROP', desc: `高危：允许永久删除此 ${typeLabel} 及其所有包含的对象` }
        ]
      }
    ]
  }

  const privilegeGroups = useMemo(() => {
    if (!selectedAsset) return []
    return getPrivilegeGroups(selectedAsset.type)
  }, [selectedAsset])

  const hasPrivilege = (privilege: string) => {
    return localPrivileges[selectedAssetId]?.includes(privilege) || false
  }

  const togglePrivilege = (privilege: string) => {
    setLocalPrivileges((prev) => {
      const current = prev[selectedAssetId] ?? []
      const next = current.includes(privilege) ? current.filter((p) => p !== privilege) : [...current, privilege]
      return { ...prev, [selectedAssetId]: next }
    })
  }

  const toggleExpand = (id: string, event: MouseEvent) => {
    event.stopPropagation()
    const next = new Set(expandedNodes)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setExpandedNodes(next)
  }

  const renderTree = (nodes: GravitinoAsset[], level = 0) => {
    return nodes.map((node) => {
      const isExpanded = expandedNodes.has(node.id)
      const isSelected = selectedAssetId === node.id
      const hasChildren = node.children && node.children.length > 0

      const Icon = node.type === 'metalake' ? Server : node.type === 'catalog' ? Database : node.type === 'schema' ? Folder : Table

      const colorClass =
        node.type === 'metalake'
          ? 'text-indigo-600'
          : node.type === 'catalog'
            ? 'text-blue-600'
            : node.type === 'schema'
              ? 'text-amber-500'
              : 'text-slate-500'

      const hasPermissions = localPrivileges[node.id]?.length > 0

      return (
        <div key={node.id}>
          <div
            onClick={() => setSelectedAssetId(node.id)}
            className={cn(
              'flex items-center gap-1.5 py-1.5 px-2 cursor-pointer select-none transition-colors border-l-2',
              isSelected
                ? 'bg-brand-50 border-brand-500 text-brand-900 font-medium'
                : 'border-transparent text-slate-600 hover:bg-slate-50 hover:text-slate-900'
            )}
            style={{ paddingLeft: `${level * 16 + 8}px` }}
          >
            <div
              onClick={(event) => hasChildren && toggleExpand(node.id, event)}
              className={cn('p-0.5 rounded hover:bg-black/5 transition-colors', !hasChildren && 'invisible')}
            >
              {isExpanded ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
            </div>

            <Icon size={14} className={cn(colorClass, 'shrink-0')} />
            <span className="truncate text-sm">{node.name}</span>
            {hasPermissions && <span className="w-1.5 h-1.5 rounded-full bg-brand-500 ml-auto" />}
          </div>
          {hasChildren && isExpanded && <div>{renderTree(node.children ?? [], level + 1)}</div>}
        </div>
      )
    })
  }

  return (
    <div className="flex h-full border rounded-xl border-slate-200 overflow-hidden bg-white shadow-sm ring-1 ring-slate-100">
      <div className="w-[280px] bg-slate-50/50 border-r border-slate-200 flex flex-col">
        <div className="p-3 border-b border-slate-200 bg-slate-50 font-bold text-xs text-slate-500 uppercase tracking-wider flex items-center gap-2">
          <Layers size={14} />
          资源拓扑
        </div>
        <div className="flex-1 overflow-y-auto py-2 custom-scrollbar">{renderTree(MOCK_GRAVITINO_ASSETS)}</div>
      </div>

      <div className="flex-1 flex flex-col bg-white min-w-0">
        {selectedAsset ? (
          <div className="flex flex-col h-full animate-in fade-in duration-200">
            <div className="px-6 py-5 border-b border-slate-100">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div
                    className={cn(
                      'p-2 rounded-lg',
                      selectedAsset.type === 'metalake'
                        ? 'bg-indigo-50 text-indigo-600'
                        : selectedAsset.type === 'catalog'
                          ? 'bg-blue-50 text-blue-600'
                          : selectedAsset.type === 'schema'
                            ? 'bg-amber-50 text-amber-600'
                            : 'bg-slate-100 text-slate-600'
                    )}
                  >
                    {selectedAsset.type === 'metalake' ? (
                      <Server size={20} />
                    ) : selectedAsset.type === 'catalog' ? (
                      <Database size={20} />
                    ) : selectedAsset.type === 'schema' ? (
                      <Folder size={20} />
                    ) : (
                      <Table size={20} />
                    )}
                  </div>
                  <div>
                    <h4 className="font-bold text-slate-900 text-base tracking-tight">{selectedAsset.name}</h4>
                    <p className="text-legal text-slate-500 font-mono mt-0.5">{selectedAsset.description || '无描述'}</p>
                  </div>
                </div>
                <div className="text-right">
                  <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">ACL Context</span>
                </div>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-4 bg-slate-50/30 custom-scrollbar">
              <div className="bg-white border border-slate-200 rounded-xl shadow-sm overflow-hidden">
                {privilegeGroups.map((group, idx) => (
                  <div key={group.name} className={cn(idx !== privilegeGroups.length - 1 && 'border-b border-slate-100')}>
                    <div className="px-5 py-2.5 bg-slate-50/80 border-b border-slate-100 backdrop-blur-sm flex items-center justify-between">
                      <h5
                        className={cn(
                          'text-xs font-bold uppercase tracking-wider flex items-center gap-2',
                          group.danger ? 'text-rose-600' : 'text-slate-500'
                        )}
                      >
                        {group.danger ? <AlertCircle size={14} /> : <Shield size={14} />}
                        {group.name}
                      </h5>
                      <span className="text-[10px] font-medium text-slate-400 bg-white px-2 py-0.5 rounded border border-slate-200">
                        {group.items.filter((item) => hasPrivilege(item.key)).length} / {group.items.length} 授权
                      </span>
                    </div>

                    <div className="divide-y divide-slate-50">
                      {group.items.map((item) => {
                        const isActive = hasPrivilege(item.key)
                        return (
                          <div
                            key={item.key}
                            className={cn(
                              'flex items-center justify-between px-5 py-4 transition-all duration-200',
                              isActive ? 'bg-slate-50/50' : 'hover:bg-slate-50/30'
                            )}
                          >
                            <div className="flex items-start gap-4">
                              <div
                                className={cn(
                                  'mt-1 p-1.5 rounded-md transition-colors',
                                  isActive ? 'bg-white shadow-sm ring-1 ring-slate-200' : 'bg-slate-100 text-slate-400'
                                )}
                              >
                                {isActive ? <Unlock size={14} className="text-brand-600" /> : <Lock size={14} />}
                              </div>
                              <div>
                                <div className="flex items-center gap-2">
                                  <span className={cn('text-legal font-bold font-mono', isActive ? 'text-slate-900' : 'text-slate-600')}>
                                    {item.label}
                                  </span>
                                  {group.danger && (
                                    <span className="text-[10px] text-rose-600 bg-rose-50 border border-rose-100 px-1.5 rounded font-medium">
                                      High Risk
                                    </span>
                                  )}
                                </div>
                                <p className={cn('text-micro text-slate-500 mt-1')}>{item.desc}</p>
                              </div>
                            </div>

                            <div className="flex items-center gap-4">
                              <span className={cn('text-xs font-medium transition-colors', isActive ? 'text-brand-600' : 'text-slate-300')}>
                                {isActive ? 'Allow' : 'Deny'}
                              </span>
                              <Switch checked={isActive} onChange={() => togglePrivilege(item.key)} color={group.color} />
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <div className="flex items-center justify-center h-full text-slate-400 flex-col gap-3">
            <Database size={48} className="opacity-20" />
            <p className="font-medium">请从左侧选择数据资产进行配置</p>
          </div>
        )}
      </div>
    </div>
  )
}
