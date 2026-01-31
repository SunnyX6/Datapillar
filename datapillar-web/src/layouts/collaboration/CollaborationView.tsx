import { useMemo, useState } from 'react'
import {
  CheckCircle2,
  CheckSquare,
  ChevronLeft,
  ChevronsLeft,
  ChevronsRight,
  Search,
  Send,
  ShieldAlert,
  Square,
  Table as TableIcon,
  User
} from 'lucide-react'
import { Button, Card, Modal } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/lib/utils'
import { requestTypeConfig, requestTypeMap, type RequestTypeConfig } from './constants'
import { buildTicketTitle, isTicketMentioned, normalizeTags } from './utils'
import type {
  CatalogTable,
  CollaborationSidebarNav,
  PermissionType,
  QuickFilter,
  SmartView,
  Ticket,
  TicketPriority,
  TicketStatus,
  TicketType,
  TicketView,
  UserProfile
} from './types'
import { CollaborationSidebar } from './CollaborationSidebar'
import { CollaborationTicketList } from './CollaborationTicketList'
import { CollaborationDetailContainer } from './CollaborationDetailContainer'

const mockCatalog: CatalogTable[] = [
  {
    id: 'tb_1',
    name: 'prod.fact_orders',
    description: '核心订单事实表，包含所有交易明细。T+1 更新。',
    owner: '交易团队',
    rows: '1.2B',
    columns: [
      { name: 'order_id', type: 'BIGINT', desc: '订单主键' },
      { name: 'user_id', type: 'BIGINT', desc: '用户ID', isPII: true },
      { name: 'amount', type: 'DECIMAL', desc: '交易金额' },
      { name: 'sku_id', type: 'INT', desc: '商品ID' },
      { name: 'created_at', type: 'TIMESTAMP', desc: '下单时间' },
      { name: 'shipping_addr', type: 'STRING', desc: '收货地址', isPII: true }
    ]
  },
  {
    id: 'tb_2',
    name: 'prod.dim_users',
    description: '用户维度表，包含画像属性与注册信息。',
    owner: '增长团队',
    rows: '450M',
    columns: [
      { name: 'user_id', type: 'BIGINT', desc: '用户ID' },
      { name: 'phone', type: 'STRING', desc: '手机号', isPII: true },
      { name: 'email', type: 'STRING', desc: '邮箱', isPII: true },
      { name: 'age_group', type: 'STRING', desc: '年龄段' },
      { name: 'vip_level', type: 'INT', desc: '会员等级' }
    ]
  },
  {
    id: 'tb_3',
    name: 'stg.marketing_clicks',
    description: '营销广告点击流日志（Staging 环境）。',
    owner: '市场团队',
    rows: '5.6B',
    columns: [
      { name: 'click_id', type: 'STRING', desc: '点击ID' },
      { name: 'campaign_id', type: 'STRING', desc: '活动ID' },
      { name: 'utm_source', type: 'STRING', desc: '来源' },
      { name: 'ts', type: 'TIMESTAMP', desc: '点击时间' }
    ]
  },
  {
    id: 'tb_4',
    name: 'prod.inventory_sku',
    description: '商品库存快照，包含各仓储中心实时水位。',
    owner: '供应链',
    rows: '85M',
    columns: [
      { name: 'sku_id', type: 'INT', desc: '商品ID' },
      { name: 'warehouse_id', type: 'INT', desc: '仓库ID' },
      { name: 'quantity', type: 'INT', desc: '库存数量' },
      { name: 'last_updated', type: 'TIMESTAMP', desc: '更新时间' }
    ]
  },
  {
    id: 'tb_5',
    name: 'dwd.user_behavior_log',
    description: '清洗后的用户行为日志，包含 PV/UV 基础数据。',
    owner: '数据工程',
    rows: '12.5B',
    columns: [
      { name: 'event_id', type: 'STRING', desc: '事件ID' },
      { name: 'user_id', type: 'BIGINT', desc: '用户ID', isPII: true },
      { name: 'page_url', type: 'STRING', desc: '页面URL' },
      { name: 'device_type', type: 'STRING', desc: '设备类型' }
    ]
  }
]

const currentUser: UserProfile = { name: '我（管理员）', avatar: '我', role: '数据架构师' }

const initialInboxData: Ticket[] = [
  {
    id: 'T-1024',
    title: '申请核心交易表 `fact_orders` 读取权限',
    type: 'DATA_ACCESS',
    status: 'PENDING',
    createdAt: '10 分钟前',
    updatedAt: '刚刚',
    requester: { name: '吴敏', avatar: '吴', role: '数据分析师' },
    assignee: currentUser,
    details: {
      target: 'prod.fact_orders',
      description: 'Q3 财务审计需要核对原始订单明细，申请 30 天只读权限。',
      priority: 'MEDIUM',
      tags: ['合规', '审计'],
      permissions: ['SELECT', '脱敏'],
      selectedColumns: ['order_id', 'amount', 'created_at'],
      duration: '30 天'
    },
    timeline: [
      { id: 'e1', user: { name: '吴敏', avatar: '吴', role: '数据分析师' }, action: '创建请求', time: '10 分钟前' }
    ]
  },
  {
    id: 'T-1029',
    title: 'ETL 变更：用户归因逻辑调整',
    type: 'CODE_REVIEW',
    status: 'CHANGES_REQUESTED',
    createdAt: '2 小时前',
    updatedAt: '15 分钟前',
    requester: { name: '李南', avatar: '李', role: '数据工程师' },
    assignee: currentUser,
    details: {
      target: 'dwd_user_attribution',
      description: '修改归因窗口期为 30 天，并修复 UTM 参数解析 Bug。',
      priority: 'HIGH',
      tags: ['核心链路'],
      diff: { added: 124, removed: 45 }
    },
    timeline: [
      { id: 'e1', user: { name: '李南', avatar: '李', role: '数据工程师' }, action: '提交评审', time: '2 小时前' },
      { id: 'e2', user: currentUser, action: '提出修改', comment: '第 45 行逻辑存在空指针风险，请检查。', time: '30 分钟前' },
      { id: 'e3', user: { name: '李南', avatar: '李', role: '数据工程师' }, action: '更新代码', comment: '已修复空指针问题，请复查。', time: '15 分钟前' }
    ]
  }
]

const initialSentData: Ticket[] = [
  {
    id: 'T-0992',
    title: 'Spark 集群资源扩容申请',
    type: 'RESOURCE_OPS',
    status: 'PENDING',
    createdAt: '昨天',
    updatedAt: '昨天',
    requester: currentUser,
    assignee: { name: '运维团队', avatar: '运', role: '运维负责人' },
    details: {
      target: 'Cluster-01',
      description: '双十一预压测，需要临时扩容计算节点。',
      priority: 'HIGH',
      tags: ['基础设施', '成本'],
      resource: { current: '50 Nodes', requested: '100 Nodes' }
    },
    timeline: [{ id: 'e1', user: currentUser, action: '创建请求', time: '昨天' }]
  }
]

const initialArchiveData: Ticket[] = []

export function CollaborationView() {
  const [inboxTickets, setInboxTickets] = useState<Ticket[]>(initialInboxData)
  const [sentTickets, setSentTickets] = useState<Ticket[]>(initialSentData)
  const [archiveTickets, setArchiveTickets] = useState<Ticket[]>(initialArchiveData)
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(initialInboxData[0]?.id ?? null)
  const [activeNav, setActiveNav] = useState<CollaborationSidebarNav>({ kind: 'FOLDER', view: 'INBOX' })

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [createStep, setCreateStep] = useState<'SELECT_TYPE' | 'FILL_FORM'>('SELECT_TYPE')
  const [createType, setCreateType] = useState<TicketType | null>(null)
  const [showMoreTypes, setShowMoreTypes] = useState(false)

  const [searchQuery, setSearchQuery] = useState('')
  const [selectedTable, setSelectedTable] = useState<CatalogTable | null>(null)
  const [selectedColumns, setSelectedColumns] = useState<string[]>([])
  const [accessReason, setAccessReason] = useState('')
  const [accessDuration, setAccessDuration] = useState('30 天')
  const [permissionType, setPermissionType] = useState<PermissionType>('SELECT')

  const [genericTarget, setGenericTarget] = useState('')
  const [genericDescription, setGenericDescription] = useState('')
  const [genericPriority, setGenericPriority] = useState<TicketPriority>('MEDIUM')
  const [genericExpectedDate, setGenericExpectedDate] = useState('')
  const [genericTags, setGenericTags] = useState('')

  const [listQuery, setListQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<TicketStatus | 'ALL'>('ALL')
  const [isFilterOpen, setIsFilterOpen] = useState(false)

  const filteredCatalog = useMemo(() => {
    if (!searchQuery) return mockCatalog
    return mockCatalog.filter((table) => table.name.toLowerCase().includes(searchQuery.toLowerCase()))
  }, [searchQuery])

  const hasPIISelected = useMemo(() => {
    if (!selectedTable) return false
    return selectedTable.columns.some((column) => column.isPII && selectedColumns.includes(column.name))
  }, [selectedColumns, selectedTable])

  const activeTickets = useMemo(() => [...inboxTickets, ...sentTickets], [inboxTickets, sentTickets])

  const currentList = useMemo(() => {
    if (activeNav.kind === 'FOLDER') {
      return activeNav.view === 'INBOX' ? inboxTickets : activeNav.view === 'SENT' ? sentTickets : archiveTickets
    }
    return activeTickets
  }, [activeNav, activeTickets, archiveTickets, inboxTickets, sentTickets])

  const mentionedCount = useMemo(
    () => activeTickets.filter((ticket) => isTicketMentioned(ticket, currentUser)).length,
    [activeTickets]
  )

  const urgentCount = useMemo(
    () => activeTickets.filter((ticket) => ticket.details.priority === 'HIGH').length,
    [activeTickets]
  )

  const dataAccessCount = useMemo(
    () => activeTickets.filter((ticket) => ticket.type === 'DATA_ACCESS').length,
    [activeTickets]
  )

  const codeReviewCount = useMemo(
    () => activeTickets.filter((ticket) => ticket.type === 'CODE_REVIEW').length,
    [activeTickets]
  )


  const infraOpsCount = useMemo(
    () => activeTickets.filter((ticket) => ticket.type === 'RESOURCE_OPS').length,
    [activeTickets]
  )

  const filteredList = useMemo(() => {
    const normalizedQuery = listQuery.trim().toLowerCase()
    return currentList.filter((ticket) => {
      if (statusFilter !== 'ALL' && ticket.status !== statusFilter) {
        return false
      }
      if (activeNav.kind === 'QUICK_FILTER' && ticket.type !== activeNav.filter) {
        return false
      }
      if (activeNav.kind === 'SMART_VIEW' && activeNav.view === 'URGENT' && ticket.details.priority !== 'HIGH') {
        return false
      }
      if (activeNav.kind === 'SMART_VIEW' && activeNav.view === 'MENTIONED' && !isTicketMentioned(ticket, currentUser)) {
        return false
      }
      if (!normalizedQuery) {
        return true
      }
      return ticket.title.toLowerCase().includes(normalizedQuery) || ticket.id.toLowerCase().includes(normalizedQuery)
    })
  }, [activeNav, currentList, listQuery, statusFilter])

  const ticketViewById = useMemo(() => {
    const map = new Map<string, TicketView>()
    inboxTickets.forEach((ticket) => map.set(ticket.id, 'INBOX'))
    sentTickets.forEach((ticket) => map.set(ticket.id, 'SENT'))
    archiveTickets.forEach((ticket) => map.set(ticket.id, 'ARCHIVE'))
    return map
  }, [archiveTickets, inboxTickets, sentTickets])

  const getTicketView = (ticketId: string): TicketView => {
    return ticketViewById.get(ticketId) ?? 'INBOX'
  }

  const effectiveSelectedTicketId = useMemo(() => {
    const firstId = filteredList[0]?.id ?? null
    if (!firstId) return null
    if (selectedTicketId && filteredList.some((ticket) => ticket.id === selectedTicketId)) {
      return selectedTicketId
    }
    return firstId
  }, [filteredList, selectedTicketId])

  const selectedTicketView: TicketView = effectiveSelectedTicketId
    ? getTicketView(effectiveSelectedTicketId)
    : activeNav.kind === 'FOLDER'
      ? activeNav.view
      : 'INBOX'

  const selectedTicket = useMemo(() => {
    return [...inboxTickets, ...sentTickets, ...archiveTickets].find((ticket) => ticket.id === effectiveSelectedTicketId) || null
  }, [archiveTickets, inboxTickets, sentTickets, effectiveSelectedTicketId])

  const createTypeLabel = createType ? requestTypeMap[createType]?.title ?? createType : ''

  const isDataAccessInvalid =
    createType === 'DATA_ACCESS' && (!selectedTable || !accessReason.trim() || selectedColumns.length === 0)

  const isGenericInvalid =
    createType !== null && createType !== 'DATA_ACCESS' && (!genericTarget.trim() || !genericDescription.trim())

  const isSubmitDisabled =
    createStep !== 'FILL_FORM' || createType === null || isDataAccessInvalid || isGenericInvalid

  const applyTicketUpdate = (ticketId: string, updater: (ticket: Ticket) => Ticket) => {
    setInboxTickets((prev) => prev.map((ticket) => (ticket.id === ticketId ? updater(ticket) : ticket)))
    setSentTickets((prev) => prev.map((ticket) => (ticket.id === ticketId ? updater(ticket) : ticket)))
    setArchiveTickets((prev) => prev.map((ticket) => (ticket.id === ticketId ? updater(ticket) : ticket)))
  }

  const handleViewChange = (nextView: TicketView) => {
    const nextList = nextView === 'INBOX' ? inboxTickets : nextView === 'SENT' ? sentTickets : archiveTickets
    setActiveNav({ kind: 'FOLDER', view: nextView })
    setSelectedTicketId(nextList[0]?.id ?? null)
  }

  const handleSmartViewChange = (nextView: SmartView) => {
    setActiveNav({ kind: 'SMART_VIEW', view: nextView })
  }

  const handleQuickFilterChange = (nextFilter: QuickFilter) => {
    setActiveNav({ kind: 'QUICK_FILTER', filter: nextFilter })
  }

  const toggleColumn = (columnName: string) => {
    setSelectedColumns((prev) =>
      prev.includes(columnName) ? prev.filter((item) => item !== columnName) : [...prev, columnName]
    )
  }

  const toggleAllColumns = () => {
    if (!selectedTable) {
      return
    }
    setSelectedColumns((prev) =>
      prev.length === selectedTable.columns.length ? [] : selectedTable.columns.map((column) => column.name)
    )
  }

  const handleStartCreate = () => {
    setCreateStep('SELECT_TYPE')
    setCreateType(null)
    setShowMoreTypes(false)
    setSearchQuery('')
    setSelectedTable(null)
    setSelectedColumns([])
    setAccessReason('')
    setAccessDuration('30 天')
    setPermissionType('SELECT')
    setGenericTarget('')
    setGenericDescription('')
    setGenericPriority('MEDIUM')
    setGenericExpectedDate('')
    setGenericTags('')
    setIsCreateOpen(true)
  }

  const handleSelectType = (type: TicketType) => {
    setCreateType(type)
    setCreateStep('FILL_FORM')
  }

  const handlePermissionTypeChange = (value: string) => {
    if (value === 'SELECT' || value === 'EXPORT') {
      setPermissionType(value)
    }
  }

  const handleSubmitRequest = () => {
    if (!createType) {
      return
    }

    const newId = `T-${Math.floor(Math.random() * 9000) + 1000}`
    const baseDetails = {
      description: accessReason.trim() || genericDescription.trim(),
      priority: createType === 'DATA_ACCESS' ? 'MEDIUM' : genericPriority,
      tags: ['协作请求']
    }

    let detailsPayload: Ticket['details'] = {
      ...baseDetails,
      target: '未填写'
    }

    if (createType === 'DATA_ACCESS') {
      detailsPayload = {
        ...baseDetails,
        target: selectedTable?.name || '未选择表',
        permissions: [permissionType, hasPIISelected ? '脱敏' : '原始'],
        selectedColumns,
        duration: accessDuration
      }
    } else {
      const nextTags = normalizeTags(genericTags)
      detailsPayload = {
        ...baseDetails,
        target: genericTarget.trim(),
        description: genericDescription.trim(),
        priority: genericPriority,
        tags: nextTags.length > 0 ? nextTags : ['协作请求'],
        expectedDate: genericExpectedDate || undefined
      }
    }

    const newTicket: Ticket = {
      id: newId,
      title: buildTicketTitle(requestTypeMap[createType]?.title ?? createType, detailsPayload.target),
      type: createType,
      status: 'PENDING',
      createdAt: '刚刚',
      updatedAt: '刚刚',
      requester: currentUser,
      assignee: { name: 'DataOwner 团队', avatar: '数', role: '资产负责人' },
      details: detailsPayload,
      timeline: [{ id: `e_${Date.now()}`, user: currentUser, action: '创建请求', time: '刚刚' }]
    }

    setSentTickets((prev) => [newTicket, ...prev])
    setIsCreateOpen(false)
    setActiveNav({ kind: 'FOLDER', view: 'SENT' })
    setSelectedTicketId(newId)
  }

  const createModalTitle =
    createStep === 'SELECT_TYPE' ? '发起新的协作请求' : `申请详情：${createTypeLabel}`
  const createModalFooterLeft =
    createStep === 'FILL_FORM' ? null : (
      <span className={cn(TYPOGRAPHY.caption, 'text-slate-400 dark:text-slate-500')}>请选择一个类型以继续</span>
    )
  const createModalFooterRight = (
    <>
      <Button
        type="button"
        onClick={() => setIsCreateOpen(false)}
        variant="ghost"
        size="small"
        className={cn(
          'px-4 py-2 font-bold text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-800/60 rounded-lg transition-colors',
          TYPOGRAPHY.bodySm
        )}
      >
        取消
      </Button>

      {createStep === 'FILL_FORM' && (
        <Button
          type="button"
          onClick={handleSubmitRequest}
          disabled={isSubmitDisabled}
          variant="primary"
          size="small"
          className={cn(
            'px-6 py-2 font-bold rounded-lg shadow-lg shadow-slate-200 dark:shadow-black/30 transition-all flex items-center gap-2',
            TYPOGRAPHY.bodySm,
            isSubmitDisabled ? 'opacity-50 cursor-not-allowed' : 'hover:-translate-y-0.5'
          )}
        >
          <Send size={14} />
          提交申请
        </Button>
      )}
    </>
  )

  const handleStatusUpdate = (nextStatus: TicketStatus, action: string, comment?: string) => {
    if (!effectiveSelectedTicketId) {
      return
    }
    const nowLabel = '刚刚'
    const event = {
      id: `e_${Date.now()}`,
      user: currentUser,
      action,
      comment,
      time: nowLabel
    }

    applyTicketUpdate(effectiveSelectedTicketId, (ticket) => ({
      ...ticket,
      status: nextStatus,
      updatedAt: nowLabel,
      timeline: [...ticket.timeline, event]
    }))
  }

  const handleApprove = () => {
    handleStatusUpdate('APPROVED', '批准请求')
  }

  const handleReject = () => {
    handleStatusUpdate('REJECTED', '拒绝请求')
  }

  const handleCancel = () => {
    handleStatusUpdate('REJECTED', '撤回申请')
  }

  const handleAddComment = (comment: string) => {
    if (!effectiveSelectedTicketId) {
      return
    }
    const trimmedComment = comment.trim()
    if (!trimmedComment) {
      return
    }
    const nowLabel = '刚刚'
    const event = {
      id: `e_${Date.now()}`,
      user: currentUser,
      action: '补充说明',
      comment: trimmedComment,
      time: nowLabel
    }

    applyTicketUpdate(effectiveSelectedTicketId, (ticket) => ({
      ...ticket,
      updatedAt: nowLabel,
      timeline: [...ticket.timeline, event]
    }))
  }

  return (
    <div className="flex h-full bg-white dark:bg-slate-900 overflow-hidden animate-in fade-in duration-300">
      <CollaborationSidebar
        activeNav={activeNav}
        onCreate={handleStartCreate}
        onChangeView={handleViewChange}
        inboxCount={inboxTickets.length}
        sentCount={sentTickets.length}
        archiveCount={archiveTickets.length}
        mentionedCount={mentionedCount}
        urgentCount={urgentCount}
        dataAccessCount={dataAccessCount}
        codeReviewCount={codeReviewCount}
        infraOpsCount={infraOpsCount}
        onChangeSmartView={handleSmartViewChange}
        onChangeQuickFilter={handleQuickFilterChange}
      />

      <CollaborationTicketList
        tickets={filteredList}
        selectedTicketId={effectiveSelectedTicketId}
        listQuery={listQuery}
        statusFilter={statusFilter}
        isFilterOpen={isFilterOpen}
        getTicketView={getTicketView}
        onListQueryChange={setListQuery}
        onToggleFilter={() => setIsFilterOpen((prev) => !prev)}
        onSelectStatus={(next) => {
          setStatusFilter(next)
          setIsFilterOpen(false)
        }}
        onSelectTicket={setSelectedTicketId}
      />

      <CollaborationDetailContainer
        key={effectiveSelectedTicketId ?? 'empty'}
        selectedTicketView={selectedTicketView}
        selectedTicket={selectedTicket}
        onApprove={handleApprove}
        onReject={handleReject}
        onCancel={handleCancel}
        onAddComment={handleAddComment}
      />

      <Modal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        size="md"
        title={createModalTitle}
        footerLeft={createModalFooterLeft}
        footerRight={createModalFooterRight}
      >
        <div className="-mx-8 -my-4 bg-white dark:bg-slate-900">
          {createStep === 'FILL_FORM' && (
            <div className="px-6 pt-4">
              <Button
                type="button"
                onClick={() => setCreateStep('SELECT_TYPE')}
                variant="ghost"
                size="iconSm"
                className="rounded-full hover:bg-slate-200 dark:hover:bg-slate-800 text-slate-500 dark:text-slate-400"
              >
                <ChevronLeft size={18} />
              </Button>
            </div>
          )}

          <div className="p-0 relative">
            {createStep === 'SELECT_TYPE' && (
              <div className="p-8 h-full flex flex-col justify-center overflow-hidden relative">
                  <div className="relative w-full h-64 overflow-hidden">
                    <div
                      className={cn(
                        'absolute inset-0 flex transition-transform duration-500 ease-in-out',
                        showMoreTypes ? '-translate-x-full' : 'translate-x-0'
                      )}
                    >
                      <div className="w-full flex-shrink-0 flex space-x-4 pr-1">
                        {requestTypeConfig.slice(0, 3).map((type) => (
                          <RequestTypeCard key={type.id} type={type} onSelect={handleSelectType} />
                        ))}

                        <Card
                          onClick={() => setShowMoreTypes(true)}
                          padding="none"
                          variant="default"
                          className="w-16 flex flex-col items-center justify-center p-2 border-2 border-dashed border-slate-200 dark:border-slate-700 hover:border-brand-300 dark:hover:border-brand-400/50 hover:bg-brand-50 dark:hover:bg-brand-900/20 text-slate-400 dark:text-slate-500 hover:text-brand-600 dark:hover:text-brand-300 transition-all flex-shrink-0 cursor-pointer group select-none"
                        >
                          <ChevronsRight size={24} className="mb-2 group-hover:translate-x-1 transition-transform" />
                          <span className="text-micro font-bold uppercase tracking-wider">更多</span>
                        </Card>
                      </div>

                      <div className="w-full flex-shrink-0 flex space-x-4 pl-1">
                        <Card
                          onClick={() => setShowMoreTypes(false)}
                          padding="none"
                          variant="default"
                          className="w-16 flex flex-col items-center justify-center p-2 border-2 border-dashed border-slate-200 dark:border-slate-700 hover:border-brand-300 dark:hover:border-brand-400/50 hover:bg-brand-50 dark:hover:bg-brand-900/20 text-slate-400 dark:text-slate-500 hover:text-brand-600 dark:hover:text-brand-300 transition-all flex-shrink-0 cursor-pointer group select-none"
                        >
                          <ChevronsLeft size={24} className="mb-2 group-hover:-translate-x-1 transition-transform" />
                          <span className="text-micro font-bold uppercase tracking-wider">返回</span>
                        </Card>

                        {requestTypeConfig.slice(3, 6).map((type) => (
                          <RequestTypeCard key={type.id} type={type} onSelect={handleSelectType} />
                        ))}
                      </div>
                    </div>
                  </div>
                  <div className="mt-8 flex justify-center space-x-2">
                    <div
                      className={cn(
                        'h-1.5 rounded-full transition-all duration-300',
                        !showMoreTypes ? 'w-8 bg-brand-500' : 'w-2 bg-slate-200 dark:bg-slate-700'
                      )}
                    ></div>
                    <div
                      className={cn(
                        'h-1.5 rounded-full transition-all duration-300',
                        showMoreTypes ? 'w-8 bg-brand-500' : 'w-2 bg-slate-200 dark:bg-slate-700'
                      )}
                    ></div>
                  </div>
              </div>
            )}

            {createStep === 'FILL_FORM' && createType === 'DATA_ACCESS' && (
              <div className="flex h-[550px]">
                <div className="w-1/2 p-6 border-r border-slate-200 dark:border-slate-800 flex flex-col">
                  <div className="mb-4">
                    <label className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide mb-2 block')}>
                      1. 搜索并选择数据表
                    </label>
                    <div className="relative group">
                      <Search className="absolute left-3 top-3.5 text-slate-400 dark:text-slate-500 group-focus-within:text-blue-500" size={16} />
                      <input
                        type="text"
                        value={searchQuery}
                        onChange={(event) => setSearchQuery(event.target.value)}
                        placeholder="输入表名关键词（例如 order, user）..."
                        className={cn(
                          'w-full pl-10 pr-4 py-3 bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all',
                          TYPOGRAPHY.bodySm
                        )}
                        autoFocus
                      />
                    </div>
                  </div>

                  <div className="flex-1 overflow-y-auto custom-scrollbar -mx-2 px-2">
                    {searchQuery && filteredCatalog.length === 0 && (
                      <div className={cn(TYPOGRAPHY.bodySm, 'text-center py-10 text-slate-400 dark:text-slate-500')}>
                        未找到匹配的数据表
                      </div>
                    )}

                    {filteredCatalog.length > 0 && (
                      <>
                        {!searchQuery && (
                          <div className="px-1 mb-2 text-micro text-slate-400 dark:text-slate-500 font-bold uppercase tracking-wider">推荐表</div>
                        )}
                        {filteredCatalog.map((table) => (
                          <div
                            key={table.id}
                            onClick={() => {
                              setSelectedTable(table)
                              setSelectedColumns([])
                            }}
                            className={cn(
                              'p-4 mb-3 rounded-xl border cursor-pointer transition-all group relative',
                              selectedTable?.id === table.id
                                ? 'bg-blue-50 border-blue-500 shadow-md ring-1 ring-blue-200 dark:bg-blue-900/20 dark:border-blue-600 dark:ring-blue-900/30'
                                : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 hover:border-blue-200 dark:hover:border-blue-600 hover:shadow-sm'
                            )}
                          >
                            <div className="flex justify-between items-start mb-1">
                              <h4
                                className={cn(
                                  'font-bold font-mono',
                                  TYPOGRAPHY.bodySm,
                                  selectedTable?.id === table.id ? 'text-blue-700 dark:text-blue-300' : 'text-slate-900 dark:text-slate-100'
                                )}
                              >
                                {table.name}
                              </h4>
                              <span className="text-micro text-slate-500 dark:text-slate-300 bg-slate-100 dark:bg-slate-800/80 px-1.5 py-0.5 rounded">
                                {table.rows} rows
                              </span>
                            </div>
                            <p className={cn(TYPOGRAPHY.caption, 'text-slate-500 dark:text-slate-400 line-clamp-2 mb-2')}>
                              {table.description}
                            </p>
                            <div className="flex items-center text-micro text-slate-400 dark:text-slate-500">
                              <User size={10} className="mr-1" />
                              负责人：{table.owner}
                            </div>
                            {selectedTable?.id === table.id && (
                              <div className="absolute right-3 top-1/2 -translate-y-1/2">
                                <CheckCircle2 className="text-blue-600" size={20} />
                              </div>
                            )}
                          </div>
                        ))}
                      </>
                    )}
                  </div>
                </div>

                <div className="w-1/2 p-6 flex flex-col bg-slate-50/30 dark:bg-slate-800/30">
                  {selectedTable ? (
                    <>
                      <div className="mb-4">
                        <div className="flex justify-between items-center mb-2">
                          <label className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide')}>
                            2. 选择字段
                          </label>
                          <Button
                            type="button"
                            onClick={toggleAllColumns}
                            variant="link"
                            size="tiny"
                            className="text-micro text-blue-600 font-bold hover:underline"
                          >
                            {selectedColumns.length === selectedTable.columns.length ? '取消全选' : '全选'}
                          </Button>
                        </div>

                        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl overflow-hidden max-h-[200px] overflow-y-auto custom-scrollbar">
                          <table className={cn('w-full text-left', TYPOGRAPHY.caption)}>
                            <thead className="bg-slate-50 dark:bg-slate-800/60 border-b border-slate-200 dark:border-slate-700">
                              <tr>
                                <th className="px-3 py-2 w-8">
                                  <div className="w-3 h-3 border border-slate-300 dark:border-slate-600 rounded"></div>
                                </th>
                                <th className="px-3 py-2 font-medium text-slate-500 dark:text-slate-400">字段名</th>
                                <th className="px-3 py-2 font-medium text-slate-500 dark:text-slate-400 text-right">类型</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                              {selectedTable.columns.map((column) => (
                                <tr
                                  key={column.name}
                                  onClick={() => toggleColumn(column.name)}
                                  className={cn(
                                    'cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors',
                                    selectedColumns.includes(column.name) && 'bg-blue-50/30 dark:bg-blue-900/20'
                                  )}
                                >
                                  <td className="px-3 py-2 text-center">
                                    {selectedColumns.includes(column.name) ? (
                                      <CheckSquare size={14} className="text-blue-600" />
                                    ) : (
                                      <Square size={14} className="text-slate-300 dark:text-slate-600" />
                                    )}
                                  </td>
                                  <td className="px-3 py-2 font-mono text-slate-700 dark:text-slate-200 flex items-center">
                                    {column.name}
                                    {column.isPII && (
                                      <span title="敏感字段">
                                        <ShieldAlert size={12} className="ml-1.5 text-amber-500" />
                                      </span>
                                    )}
                                  </td>
                                  <td className="px-3 py-2 text-right text-slate-400 dark:text-slate-500 font-mono text-micro">
                                    {column.type}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                        {selectedColumns.length > 0 && (
                          <div className="mt-2 text-micro text-slate-500 dark:text-slate-400 flex items-center justify-between">
                            <span>
                              已选择 <strong className="text-slate-900 dark:text-slate-100">{selectedColumns.length}</strong> 列
                            </span>
                            {hasPIISelected && (
                              <span className="flex items-center text-amber-600 dark:text-amber-400 font-bold bg-amber-50 dark:bg-amber-900/20 px-2 py-0.5 rounded">
                                <ShieldAlert size={10} className="mr-1" /> 包含敏感字段
                              </span>
                            )}
                          </div>
                        )}
                      </div>

                      <div className="space-y-4">
                        <div className="grid grid-cols-2 gap-4">
                          <div>
                            <label className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
                              权限类型
                            </label>
                            <select
                              value={permissionType}
                              onChange={(event) => handlePermissionTypeChange(event.target.value)}
                              className={cn(
                                'w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:border-blue-500 outline-none',
                                TYPOGRAPHY.bodySm
                              )}
                            >
                              <option value="SELECT">SELECT（查询）</option>
                              <option value="EXPORT">EXPORT（导出）</option>
                            </select>
                          </div>
                          <div>
                            <label className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
                              期限
                            </label>
                            <select
                              value={accessDuration}
                              onChange={(event) => setAccessDuration(event.target.value)}
                              className={cn(
                                'w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:border-blue-500 outline-none',
                                TYPOGRAPHY.bodySm
                              )}
                            >
                              <option>7 天</option>
                              <option>30 天</option>
                              <option>90 天</option>
                              <option>长期</option>
                            </select>
                          </div>
                        </div>

                        <div>
                          <label className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
                            3. 业务理由
                          </label>
                          <textarea
                            value={accessReason}
                            onChange={(event) => setAccessReason(event.target.value)}
                            placeholder="请说明申请原因与使用场景"
                            className={cn(
                              'w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:border-blue-500 outline-none resize-none h-20',
                              TYPOGRAPHY.bodySm
                            )}
                          />
                        </div>
                      </div>
                    </>
                  ) : (
                    <div className="h-full flex flex-col items-center justify-center text-slate-400 dark:text-slate-500 border-2 border-dashed border-slate-200 dark:border-slate-700 rounded-xl bg-slate-50/50 dark:bg-slate-800/30">
                      <TableIcon size={32} className="mb-3 opacity-20" />
                      <p className={TYPOGRAPHY.bodySm}>请先在左侧选择一个数据表</p>
                    </div>
                  )}
                </div>
              </div>
            )}

            {createStep === 'FILL_FORM' && createType && createType !== 'DATA_ACCESS' && (
              <div className="p-8 space-y-4">
	                <div className="grid grid-cols-2 gap-4">
	                  <div>
	                    <label className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
	                      目标对象
	                    </label>
	                    <input
	                      value={genericTarget}
	                      onChange={(event) => setGenericTarget(event.target.value)}
	                      placeholder="请输入需要协作的资产/服务"
	                      className={cn(
	                        'w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:border-brand-500 outline-none',
	                        TYPOGRAPHY.bodySm
	                      )}
	                    />
	                  </div>
	                  <div>
	                    <label className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
	                      期望完成日期
	                    </label>
	                    <input
	                      type="date"
	                      value={genericExpectedDate}
	                      onChange={(event) => setGenericExpectedDate(event.target.value)}
	                      className={cn(
	                        'w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:border-brand-500 outline-none',
	                        TYPOGRAPHY.bodySm
	                      )}
	                    />
	                  </div>
	                </div>
	                <div className="grid grid-cols-2 gap-4">
	                  <div>
	                    <label className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
	                      优先级
	                    </label>
	                    <select
	                      value={genericPriority}
	                      onChange={(event) => setGenericPriority(event.target.value as TicketPriority)}
	                      className={cn(
	                        'w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:border-brand-500 outline-none',
	                        TYPOGRAPHY.bodySm
	                      )}
	                    >
                      <option value="HIGH">高</option>
                      <option value="MEDIUM">中</option>
                      <option value="LOW">低</option>
                    </select>
	                  </div>
	                  <div>
	                    <label className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
	                      标签
	                    </label>
	                    <input
	                      value={genericTags}
	                      onChange={(event) => setGenericTags(event.target.value)}
	                      placeholder="例如：发布, 变更"
	                      className={cn(
	                        'w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:border-brand-500 outline-none',
	                        TYPOGRAPHY.bodySm
	                      )}
	                    />
	                  </div>
	                </div>
	                <div>
	                  <label className={cn(TYPOGRAPHY.caption, 'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
	                    需求描述
	                  </label>
	                  <textarea
	                    value={genericDescription}
	                    onChange={(event) => setGenericDescription(event.target.value)}
	                    placeholder="请描述变更/发布/问题详情"
	                    className={cn(
	                      'w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:border-brand-500 outline-none resize-none h-28',
	                      TYPOGRAPHY.bodySm
	                    )}
	                  />
	                </div>
              </div>
            )}
          </div>
        </div>
      </Modal>
    </div>
  )
}

function RequestTypeCard({ type, onSelect }: { type: RequestTypeConfig; onSelect: (typeId: TicketType) => void }) {
  return (
    <Card
      onClick={() => onSelect(type.id)}
      padding="none"
      variant="default"
      className={cn(
        'flex-1 min-w-0 flex flex-col items-center justify-center p-6 border-2 border-slate-100 dark:border-slate-800 transition-all group text-center cursor-pointer',
        type.cardClass
      )}
    >
      <div
        className={cn(
          'w-14 h-14 rounded-full flex items-center justify-center mb-4 group-hover:scale-110 transition-transform shadow-sm',
          type.iconClass
        )}
      >
        <type.icon size={28} />
      </div>
      <span className="text-subtitle font-bold text-slate-900 dark:text-slate-100 mb-2">{type.title}</span>
      <span className="text-caption text-slate-500 dark:text-slate-400 leading-relaxed px-2 line-clamp-3">{type.desc}</span>
    </Card>
  )
}
