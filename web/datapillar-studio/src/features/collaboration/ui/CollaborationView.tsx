import { useMemo,useState } from 'react'
import {
 CheckCircle2,CheckSquare,ChevronLeft,ChevronsLeft,ChevronsRight,Search,Send,ShieldAlert,Square,Table as TableIcon,User
} from 'lucide-react'
import { Button,Card,Modal } from '@/components/ui'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import { requestTypeConfig,requestTypeMap,type RequestTypeConfig } from '../utils/constants'
import { buildTicketTitle,isTicketMentioned,normalizeTags } from '../utils'
import type {
 CatalogTable,CollaborationSidebarNav,PermissionType,QuickFilter,SmartView,Ticket,TicketPriority,TicketStatus,TicketType,TicketView,UserProfile
} from '../utils/types'
import { CollaborationSidebar } from './CollaborationSidebar'
import { CollaborationTicketList } from './CollaborationTicketList'
import { CollaborationDetailContainer } from './CollaborationDetailContainer'

const mockCatalog:CatalogTable[] = [{
 id:'tb_1',name:'prod.fact_orders',description:'Core order fact table,Contains all transaction details.T+1 update.',owner:'trading team',rows:'1.2B',columns:[{ name:'order_id',type:'BIGINT',desc:'Order primary key' },{ name:'user_id',type:'BIGINT',desc:'UserID',isPII:true },{ name:'amount',type:'DECIMAL',desc:'Transaction amount' },{ name:'sku_id',type:'INT',desc:'merchandiseID' },{ name:'created_at',type:'TIMESTAMP',desc:'Order time' },{ name:'shipping_addr',type:'STRING',desc:'Shipping address',isPII:true }]
 },{
 id:'tb_2',name:'prod.dim_users',description:'User dimension table,Contains portrait attributes and registration information.',owner:'growth team',rows:'450M',columns:[{ name:'user_id',type:'BIGINT',desc:'UserID' },{ name:'phone',type:'STRING',desc:'Mobile phone number',isPII:true },{ name:'email',type:'STRING',desc:'Email',isPII:true },{ name:'age_group',type:'STRING',desc:'age group' },{ name:'vip_level',type:'INT',desc:'Membership level' }]
 },{
 id:'tb_3',name:'stg.marketing_clicks',description:'Marketing Advertising Clickstream Log(Staging environment).',owner:'Marketing team',rows:'5.6B',columns:[{ name:'click_id',type:'STRING',desc:'ClickID' },{ name:'campaign_id',type:'STRING',desc:'ActivitiesID' },{ name:'utm_source',type:'STRING',desc:'Source' },{ name:'ts',type:'TIMESTAMP',desc:'click time' }]
 },{
 id:'tb_4',name:'prod.inventory_sku',description:'Product inventory snapshot,Contains real-time water levels of each storage center.',owner:'supply chain',rows:'85M',columns:[{ name:'sku_id',type:'INT',desc:'merchandiseID' },{ name:'warehouse_id',type:'INT',desc:'warehouseID' },{ name:'quantity',type:'INT',desc:'Stock quantity' },{ name:'last_updated',type:'TIMESTAMP',desc:'Update time' }]
 },{
 id:'tb_5',name:'dwd.user_behavior_log',description:'Cleaned user behavior logs,contains PV/UV Basic data.',owner:'data engineering',rows:'12.5B',columns:[{ name:'event_id',type:'STRING',desc:'eventID' },{ name:'user_id',type:'BIGINT',desc:'UserID',isPII:true },{ name:'page_url',type:'STRING',desc:'PageURL' },{ name:'device_type',type:'STRING',desc:'Device type' }]
 }]

const currentUser:UserProfile = { name:'me(Administrator)',avatar:'me',role:'data architect' }

const initialInboxData:Ticket[] = [{
 id:'T-1024',title:'Apply for Core Transaction Form `fact_orders` Read permission',type:'DATA_ACCESS',status:'PENDING',createdAt:'10 minutes ago',updatedAt:'just now',requester:{ name:'Wu Min',avatar:'Wu',role:'data analyst' },assignee:currentUser,details:{
 target:'prod.fact_orders',description:'Q3 Financial audit requires verification of original order details,Apply 30 Read-only permission per day.',priority:'MEDIUM',tags:['Compliance','audit'],permissions:['SELECT','Desensitization'],selectedColumns:['order_id','amount','created_at'],duration:'30 day'
 },timeline:[{ id:'e1',user:{ name:'Wu Min',avatar:'Wu',role:'data analyst' },action:'Create request',time:'10 minutes ago' }]
 },{
 id:'T-1029',title:'ETL change:Adjustment of user attribution logic',type:'CODE_REVIEW',status:'CHANGES_REQUESTED',createdAt:'2 hours ago',updatedAt:'15 minutes ago',requester:{ name:'Li Nan',avatar:'Li',role:'data engineer' },assignee:currentUser,details:{
 target:'dwd_user_attribution',description:'Modify the attribution window period to 30 day,and fix UTM Parameter analysis Bug.',priority:'HIGH',tags:['core link'],diff:{ added:124,removed:45 }
 },timeline:[{ id:'e1',user:{ name:'Li Nan',avatar:'Li',role:'data engineer' },action:'Submit for review',time:'2 hours ago' },{ id:'e2',user:currentUser,action:'Propose changes',comment:'No.45 There is a risk of null pointer in row logic,please check.',time:'30 minutes ago' },{ id:'e3',user:{ name:'Li Nan',avatar:'Li',role:'data engineer' },action:'Update code',comment:'Null pointer issue fixed,Please review.',time:'15 minutes ago' }]
 }]

const initialSentData:Ticket[] = [{
 id:'T-0992',title:'Spark Cluster resource expansion application',type:'RESOURCE_OPS',status:'PENDING',createdAt:'yesterday',updatedAt:'yesterday',requester:currentUser,assignee:{ name:'Operations and maintenance team',avatar:'luck',role:'Operation and maintenance manager' },details:{
 target:'Cluster-01',description:'Double Eleven pre-stress test,Need to temporarily expand computing nodes.',priority:'HIGH',tags:['infrastructure','Cost'],resource:{ current:'50 Nodes',requested:'100 Nodes' }
 },timeline:[{ id:'e1',user:currentUser,action:'Create request',time:'yesterday' }]
 }]

const initialArchiveData:Ticket[] = []

export function CollaborationView() {
 const [inboxTickets,setInboxTickets] = useState<Ticket[]>(initialInboxData)
 const [sentTickets,setSentTickets] = useState<Ticket[]>(initialSentData)
 const [archiveTickets,setArchiveTickets] = useState<Ticket[]>(initialArchiveData)
 const [selectedTicketId,setSelectedTicketId] = useState<string | null>(initialInboxData[0]?.id?? null)
 const [activeNav,setActiveNav] = useState<CollaborationSidebarNav>({ kind:'FOLDER',view:'INBOX' })

 const [isCreateOpen,setIsCreateOpen] = useState(false)
 const [createStep,setCreateStep] = useState<'SELECT_TYPE' | 'FILL_FORM'>('SELECT_TYPE')
 const [createType,setCreateType] = useState<TicketType | null>(null)
 const [showMoreTypes,setShowMoreTypes] = useState(false)

 const [searchQuery,setSearchQuery] = useState('')
 const [selectedTable,setSelectedTable] = useState<CatalogTable | null>(null)
 const [selectedColumns,setSelectedColumns] = useState<string[]>([])
 const [accessReason,setAccessReason] = useState('')
 const [accessDuration,setAccessDuration] = useState('30 day')
 const [permissionType,setPermissionType] = useState<PermissionType>('SELECT')

 const [genericTarget,setGenericTarget] = useState('')
 const [genericDescription,setGenericDescription] = useState('')
 const [genericPriority,setGenericPriority] = useState<TicketPriority>('MEDIUM')
 const [genericExpectedDate,setGenericExpectedDate] = useState('')
 const [genericTags,setGenericTags] = useState('')

 const [listQuery,setListQuery] = useState('')
 const [statusFilter,setStatusFilter] = useState<TicketStatus | 'ALL'>('ALL')
 const [isFilterOpen,setIsFilterOpen] = useState(false)

 const filteredCatalog = useMemo(() => {
 if (!searchQuery) return mockCatalog
 return mockCatalog.filter((table) => table.name.toLowerCase().includes(searchQuery.toLowerCase()))
 },[searchQuery])

 const hasPIISelected = useMemo(() => {
 if (!selectedTable) return false
 return selectedTable.columns.some((column) => column.isPII && selectedColumns.includes(column.name))
 },[selectedColumns,selectedTable])

 const activeTickets = useMemo(() => [...inboxTickets,...sentTickets],[inboxTickets,sentTickets])

 const currentList = useMemo(() => {
 if (activeNav.kind === 'FOLDER') {
 return activeNav.view === 'INBOX'?inboxTickets:activeNav.view === 'SENT'?sentTickets:archiveTickets
 }
 return activeTickets
 },[activeNav,activeTickets,archiveTickets,inboxTickets,sentTickets])

 const mentionedCount = useMemo(() => activeTickets.filter((ticket) => isTicketMentioned(ticket,currentUser)).length,[activeTickets])

 const urgentCount = useMemo(() => activeTickets.filter((ticket) => ticket.details.priority === 'HIGH').length,[activeTickets])

 const dataAccessCount = useMemo(() => activeTickets.filter((ticket) => ticket.type === 'DATA_ACCESS').length,[activeTickets])

 const codeReviewCount = useMemo(() => activeTickets.filter((ticket) => ticket.type === 'CODE_REVIEW').length,[activeTickets])


 const infraOpsCount = useMemo(() => activeTickets.filter((ticket) => ticket.type === 'RESOURCE_OPS').length,[activeTickets])

 const filteredList = useMemo(() => {
 const normalizedQuery = listQuery.trim().toLowerCase()
 return currentList.filter((ticket) => {
 if (statusFilter!== 'ALL' && ticket.status!== statusFilter) {
 return false
 }
 if (activeNav.kind === 'QUICK_FILTER' && ticket.type!== activeNav.filter) {
 return false
 }
 if (activeNav.kind === 'SMART_VIEW' && activeNav.view === 'URGENT' && ticket.details.priority!== 'HIGH') {
 return false
 }
 if (activeNav.kind === 'SMART_VIEW' && activeNav.view === 'MENTIONED' &&!isTicketMentioned(ticket,currentUser)) {
 return false
 }
 if (!normalizedQuery) {
 return true
 }
 return ticket.title.toLowerCase().includes(normalizedQuery) || ticket.id.toLowerCase().includes(normalizedQuery)
 })
 },[activeNav,currentList,listQuery,statusFilter])

 const ticketViewById = useMemo(() => {
 const map = new Map<string,TicketView>()
 inboxTickets.forEach((ticket) => map.set(ticket.id,'INBOX'))
 sentTickets.forEach((ticket) => map.set(ticket.id,'SENT'))
 archiveTickets.forEach((ticket) => map.set(ticket.id,'ARCHIVE'))
 return map
 },[archiveTickets,inboxTickets,sentTickets])

 const getTicketView = (ticketId:string):TicketView => {
 return ticketViewById.get(ticketId)?? 'INBOX'
 }

 const effectiveSelectedTicketId = useMemo(() => {
 const firstId = filteredList[0]?.id?? null
 if (!firstId) return null
 if (selectedTicketId && filteredList.some((ticket) => ticket.id === selectedTicketId)) {
 return selectedTicketId
 }
 return firstId
 },[filteredList,selectedTicketId])

 const selectedTicketView:TicketView = effectiveSelectedTicketId?getTicketView(effectiveSelectedTicketId):activeNav.kind === 'FOLDER'?activeNav.view:'INBOX'

 const selectedTicket = useMemo(() => {
 return [...inboxTickets,...sentTickets,...archiveTickets].find((ticket) => ticket.id === effectiveSelectedTicketId) || null
 },[archiveTickets,inboxTickets,sentTickets,effectiveSelectedTicketId])

 const createTypeLabel = createType?requestTypeMap[createType]?.title?? createType:''

 const isDataAccessInvalid =
 createType === 'DATA_ACCESS' && (!selectedTable ||!accessReason.trim() || selectedColumns.length === 0)

 const isGenericInvalid =
 createType!== null && createType!== 'DATA_ACCESS' && (!genericTarget.trim() ||!genericDescription.trim())

 const isSubmitDisabled =
 createStep!== 'FILL_FORM' || createType === null || isDataAccessInvalid || isGenericInvalid

 const applyTicketUpdate = (ticketId:string,updater:(ticket:Ticket) => Ticket) => {
 setInboxTickets((prev) => prev.map((ticket) => (ticket.id === ticketId?updater(ticket):ticket)))
 setSentTickets((prev) => prev.map((ticket) => (ticket.id === ticketId?updater(ticket):ticket)))
 setArchiveTickets((prev) => prev.map((ticket) => (ticket.id === ticketId?updater(ticket):ticket)))
 }

 const handleViewChange = (nextView:TicketView) => {
 const nextList = nextView === 'INBOX'?inboxTickets:nextView === 'SENT'?sentTickets:archiveTickets
 setActiveNav({ kind:'FOLDER',view:nextView })
 setSelectedTicketId(nextList[0]?.id?? null)
 }

 const handleSmartViewChange = (nextView:SmartView) => {
 setActiveNav({ kind:'SMART_VIEW',view:nextView })
 }

 const handleQuickFilterChange = (nextFilter:QuickFilter) => {
 setActiveNav({ kind:'QUICK_FILTER',filter:nextFilter })
 }

 const toggleColumn = (columnName:string) => {
 setSelectedColumns((prev) =>
 prev.includes(columnName)?prev.filter((item) => item!== columnName):[...prev,columnName])
 }

 const toggleAllColumns = () => {
 if (!selectedTable) {
 return
 }
 setSelectedColumns((prev) =>
 prev.length === selectedTable.columns.length?[]:selectedTable.columns.map((column) => column.name))
 }

 const handleStartCreate = () => {
 setCreateStep('SELECT_TYPE')
 setCreateType(null)
 setShowMoreTypes(false)
 setSearchQuery('')
 setSelectedTable(null)
 setSelectedColumns([])
 setAccessReason('')
 setAccessDuration('30 day')
 setPermissionType('SELECT')
 setGenericTarget('')
 setGenericDescription('')
 setGenericPriority('MEDIUM')
 setGenericExpectedDate('')
 setGenericTags('')
 setIsCreateOpen(true)
 }

 const handleSelectType = (type:TicketType) => {
 setCreateType(type)
 setCreateStep('FILL_FORM')
 }

 const handlePermissionTypeChange = (value:string) => {
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
 description:accessReason.trim() || genericDescription.trim(),priority:createType === 'DATA_ACCESS'?'MEDIUM':genericPriority,tags:['Collaboration request']
 }

 let detailsPayload:Ticket['details'] = {...baseDetails,target:'Not filled in'
 }

 if (createType === 'DATA_ACCESS') {
 detailsPayload = {...baseDetails,target:selectedTable?.name || 'No table selected',permissions:[permissionType,hasPIISelected?'Desensitization':'original'],selectedColumns,duration:accessDuration
 }
 } else {
 const nextTags = normalizeTags(genericTags)
 detailsPayload = {...baseDetails,target:genericTarget.trim(),description:genericDescription.trim(),priority:genericPriority,tags:nextTags.length > 0?nextTags:['Collaboration request'],expectedDate:genericExpectedDate || undefined
 }
 }

 const newTicket:Ticket = {
 id:newId,title:buildTicketTitle(requestTypeMap[createType]?.title?? createType,detailsPayload.target),type:createType,status:'PENDING',createdAt:'just now',updatedAt:'just now',requester:currentUser,assignee:{ name:'DataOwner team',avatar:'number',role:'Asset Manager' },details:detailsPayload,timeline:[{ id:`e_${Date.now()}`,user:currentUser,action:'Create request',time:'just now' }]
 }

 setSentTickets((prev) => [newTicket,...prev])
 setIsCreateOpen(false)
 setActiveNav({ kind:'FOLDER',view:'SENT' })
 setSelectedTicketId(newId)
 }

 const createModalTitle =
 createStep === 'SELECT_TYPE'?'Initiate a new collaboration request':`Application details:${createTypeLabel}`
 const createModalFooterLeft =
 createStep === 'FILL_FORM'?null:(<span className={cn(TYPOGRAPHY.caption,'text-slate-400 dark:text-slate-500')}>Please select a type to continue</span>)
 const createModalFooterRight = (<>
 <Button
 type="button"
 onClick={() => setIsCreateOpen(false)}
 variant="ghost"
 size="small"
 className={cn('px-4 py-2 font-bold text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 hover:bg-slate-200/50 dark:hover:bg-slate-800/60 rounded-lg transition-colors',TYPOGRAPHY.bodySm)}
 >
 Cancel
 </Button>

 {createStep === 'FILL_FORM' && (<Button
 type="button"
 onClick={handleSubmitRequest}
 disabled={isSubmitDisabled}
 variant="primary"
 size="small"
 className={cn('px-6 py-2 font-bold rounded-lg shadow-lg shadow-slate-200 dark:shadow-black/30 transition-all flex items-center gap-2',TYPOGRAPHY.bodySm,isSubmitDisabled?'opacity-50 cursor-not-allowed':'hover:-translate-y-0.5')}
 >
 <Send size={14} />
 Submit application
 </Button>)}
 </>)

 const handleStatusUpdate = (nextStatus:TicketStatus,action:string,comment?: string) => {
 if (!effectiveSelectedTicketId) {
 return
 }
 const nowLabel = 'just now'
 const event = {
 id:`e_${Date.now()}`,user:currentUser,action,comment,time:nowLabel
 }

 applyTicketUpdate(effectiveSelectedTicketId,(ticket) => ({...ticket,status:nextStatus,updatedAt:nowLabel,timeline:[...ticket.timeline,event]
 }))
 }

 const handleApprove = () => {
 handleStatusUpdate('APPROVED','Approve request')
 }

 const handleReject = () => {
 handleStatusUpdate('REJECTED','Deny request')
 }

 const handleCancel = () => {
 handleStatusUpdate('REJECTED','Withdraw application')
 }

 const handleAddComment = (comment:string) => {
 if (!effectiveSelectedTicketId) {
 return
 }
 const trimmedComment = comment.trim()
 if (!trimmedComment) {
 return
 }
 const nowLabel = 'just now'
 const event = {
 id:`e_${Date.now()}`,user:currentUser,action:'Additional information',comment:trimmedComment,time:nowLabel
 }

 applyTicketUpdate(effectiveSelectedTicketId,(ticket) => ({...ticket,updatedAt:nowLabel,timeline:[...ticket.timeline,event]
 }))
 }

 return (<div className="flex h-full bg-white dark:bg-slate-900 overflow-hidden animate-in fade-in duration-300">
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
 onToggleFilter={() => setIsFilterOpen((prev) =>!prev)}
 onSelectStatus={(next) => {
 setStatusFilter(next)
 setIsFilterOpen(false)
 }}
 onSelectTicket={setSelectedTicketId}
 />

 <CollaborationDetailContainer
 key={effectiveSelectedTicketId?? 'empty'}
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
 {createStep === 'FILL_FORM' && (<div className="px-6 pt-4">
 <Button
 type="button"
 onClick={() => setCreateStep('SELECT_TYPE')}
 variant="ghost"
 size="iconSm"
 className="rounded-full hover:bg-slate-200 dark:hover:bg-slate-800 text-slate-500 dark:text-slate-400"
 >
 <ChevronLeft size={18} />
 </Button>
 </div>)}

 <div className="p-0 relative">
 {createStep === 'SELECT_TYPE' && (<div className="p-8 h-full flex flex-col justify-center overflow-hidden relative">
 <div className="relative w-full h-64 overflow-hidden">
 <div
 className={cn('absolute inset-0 flex transition-transform duration-500 ease-in-out',showMoreTypes?'-translate-x-full':'translate-x-0')}
 >
 <div className="w-full flex-shrink-0 flex space-x-4 pr-1">
 {requestTypeConfig.slice(0,3).map((type) => (<RequestTypeCard key={type.id} type={type} onSelect={handleSelectType} />))}

 <Card
 onClick={() => setShowMoreTypes(true)}
 padding="none"
 variant="default"
 className="w-16 flex flex-col items-center justify-center p-2 border-2 border-dashed border-slate-200 dark:border-slate-700 hover:border-brand-300 dark:hover:border-brand-400/50 hover:bg-brand-50 dark:hover:bg-brand-900/20 text-slate-400 dark:text-slate-500 hover:text-brand-600 dark:hover:text-brand-300 transition-all flex-shrink-0 cursor-pointer group select-none"
 >
 <ChevronsRight size={24} className="mb-2 group-hover:translate-x-1 transition-transform" />
 <span className="text-micro font-bold uppercase tracking-wider">More</span>
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
 <span className="text-micro font-bold uppercase tracking-wider">Return</span>
 </Card>

 {requestTypeConfig.slice(3,6).map((type) => (<RequestTypeCard key={type.id} type={type} onSelect={handleSelectType} />))}
 </div>
 </div>
 </div>
 <div className="mt-8 flex justify-center space-x-2">
 <div
 className={cn('h-1.5 rounded-full transition-all duration-300',!showMoreTypes?'w-8 bg-brand-500':'w-2 bg-slate-200 dark:bg-slate-700')}
 ></div>
 <div
 className={cn('h-1.5 rounded-full transition-all duration-300',showMoreTypes?'w-8 bg-brand-500':'w-2 bg-slate-200 dark:bg-slate-700')}
 ></div>
 </div>
 </div>)}

 {createStep === 'FILL_FORM' && createType === 'DATA_ACCESS' && (<div className="flex h-[550px]">
 <div className="w-1/2 p-6 border-r border-slate-200 dark:border-slate-800 flex flex-col">
 <div className="mb-4">
 <label className={cn(TYPOGRAPHY.caption,'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide mb-2 block')}>
 1.Search and select data table
 </label>
 <div className="relative group">
 <Search className="absolute left-3 top-3.5 text-slate-400 dark:text-slate-500 group-focus-within:text-blue-500" size={16} />
 <input
 type="text"
 value={searchQuery}
 onChange={(event) => setSearchQuery(event.target.value)}
 placeholder="Enter table name keywords(For example order,user)..."
 className={cn('w-full pl-10 pr-4 py-3 bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700 rounded-xl text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all',TYPOGRAPHY.bodySm)}
 autoFocus
 />
 </div>
 </div>

 <div className="flex-1 overflow-y-auto custom-scrollbar -mx-2 px-2">
 {searchQuery && filteredCatalog.length === 0 && (<div className={cn(TYPOGRAPHY.bodySm,'text-center py-10 text-slate-400 dark:text-slate-500')}>
 No matching data table found
 </div>)}

 {filteredCatalog.length > 0 && (<>
 {!searchQuery && (<div className="px-1 mb-2 text-micro text-slate-400 dark:text-slate-500 font-bold uppercase tracking-wider">Recommendation table</div>)}
 {filteredCatalog.map((table) => (<div
 key={table.id}
 onClick={() => {
 setSelectedTable(table)
 setSelectedColumns([])
 }}
 className={cn('p-4 mb-3 rounded-xl border cursor-pointer transition-all group relative',selectedTable?.id === table.id?'bg-blue-50 border-blue-500 shadow-md ring-1 ring-blue-200 dark:bg-blue-900/20 dark:border-blue-600 dark:ring-blue-900/30':'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 hover:border-blue-200 dark:hover:border-blue-600 hover:shadow-sm')}
 >
 <div className="flex justify-between items-start mb-1">
 <h4
 className={cn('font-bold font-mono',TYPOGRAPHY.bodySm,selectedTable?.id === table.id?'text-blue-700 dark:text-blue-300':'text-slate-900 dark:text-slate-100')}
 >
 {table.name}
 </h4>
 <span className="text-micro text-slate-500 dark:text-slate-300 bg-slate-100 dark:bg-slate-800/80 px-1.5 py-0.5 rounded">
 {table.rows} rows
 </span>
 </div>
 <p className={cn(TYPOGRAPHY.caption,'text-slate-500 dark:text-slate-400 line-clamp-2 mb-2')}>
 {table.description}
 </p>
 <div className="flex items-center text-micro text-slate-400 dark:text-slate-500">
 <User size={10} className="mr-1" />
 person in charge:{table.owner}
 </div>
 {selectedTable?.id === table.id && (<div className="absolute right-3 top-1/2 -translate-y-1/2">
 <CheckCircle2 className="text-blue-600" size={20} />
 </div>)}
 </div>))}
 </>)}
 </div>
 </div>

 <div className="w-1/2 p-6 flex flex-col bg-slate-50/30 dark:bg-slate-800/30">
 {selectedTable?(<>
 <div className="mb-4">
 <div className="flex justify-between items-center mb-2">
 <label className={cn(TYPOGRAPHY.caption,'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide')}>
 2.Select field
 </label>
 <Button
 type="button"
 onClick={toggleAllColumns}
 variant="link"
 size="tiny"
 className="text-micro text-blue-600 font-bold hover:underline"
 >
 {selectedColumns.length === selectedTable.columns.length?'Deselect all':'Select all'}
 </Button>
 </div>

 <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl overflow-hidden max-h-[200px] overflow-y-auto custom-scrollbar">
 <table className={cn('w-full text-left',TYPOGRAPHY.caption)}>
 <thead className="bg-slate-50 dark:bg-slate-800/60 border-b border-slate-200 dark:border-slate-700">
 <tr>
 <th className="px-3 py-2 w-8">
 <div className="w-3 h-3 border border-slate-300 dark:border-slate-600 rounded"></div>
 </th>
 <th className="px-3 py-2 font-medium text-slate-500 dark:text-slate-400">Field name</th>
 <th className="px-3 py-2 font-medium text-slate-500 dark:text-slate-400 text-right">Type</th>
 </tr>
 </thead>
 <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
 {selectedTable.columns.map((column) => (<tr
 key={column.name}
 onClick={() => toggleColumn(column.name)}
 className={cn('cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800/60 transition-colors',selectedColumns.includes(column.name) && 'bg-blue-50/30 dark:bg-blue-900/20')}
 >
 <td className="px-3 py-2 text-center">
 {selectedColumns.includes(column.name)?(<CheckSquare size={14} className="text-blue-600" />):(<Square size={14} className="text-slate-300 dark:text-slate-600" />)}
 </td>
 <td className="px-3 py-2 font-mono text-slate-700 dark:text-slate-200 flex items-center">
 {column.name}
 {column.isPII && (<span title="Sensitive fields">
 <ShieldAlert size={12} className="ml-1.5 text-amber-500" />
 </span>)}
 </td>
 <td className="px-3 py-2 text-right text-slate-400 dark:text-slate-500 font-mono text-micro">
 {column.type}
 </td>
 </tr>))}
 </tbody>
 </table>
 </div>
 {selectedColumns.length > 0 && (<div className="mt-2 text-micro text-slate-500 dark:text-slate-400 flex items-center justify-between">
 <span>
 Selected <strong className="text-slate-900 dark:text-slate-100">{selectedColumns.length}</strong> Column
 </span>
 {hasPIISelected && (<span className="flex items-center text-amber-600 dark:text-amber-400 font-bold bg-amber-50 dark:bg-amber-900/20 px-2 py-0.5 rounded">
 <ShieldAlert size={10} className="mr-1" /> Contains sensitive fields
 </span>)}
 </div>)}
 </div>

 <div className="space-y-4">
 <div className="grid grid-cols-2 gap-4">
 <div>
 <label className={cn(TYPOGRAPHY.caption,'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
 Permission type
 </label>
 <select
 value={permissionType}
 onChange={(event) => handlePermissionTypeChange(event.target.value)}
 className={cn('w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:border-blue-500 outline-none',TYPOGRAPHY.bodySm)}
 >
 <option value="SELECT">SELECT(Query)</option>
 <option value="EXPORT">EXPORT(Export)</option>
 </select>
 </div>
 <div>
 <label className={cn(TYPOGRAPHY.caption,'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
 term
 </label>
 <select
 value={accessDuration}
 onChange={(event) => setAccessDuration(event.target.value)}
 className={cn('w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:border-blue-500 outline-none',TYPOGRAPHY.bodySm)}
 >
 <option>7 day</option>
 <option>30 day</option>
 <option>90 day</option>
 <option>long term</option>
 </select>
 </div>
 </div>

 <div>
 <label className={cn(TYPOGRAPHY.caption,'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
 3.business rationale
 </label>
 <textarea
 value={accessReason}
 onChange={(event) => setAccessReason(event.target.value)}
 placeholder="Please explain the reason for application and usage scenarios"
 className={cn('w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:border-blue-500 outline-none resize-none h-20',TYPOGRAPHY.bodySm)}
 />
 </div>
 </div>
 </>):(<div className="h-full flex flex-col items-center justify-center text-slate-400 dark:text-slate-500 border-2 border-dashed border-slate-200 dark:border-slate-700 rounded-xl bg-slate-50/50 dark:bg-slate-800/30">
 <TableIcon size={32} className="mb-3 opacity-20" />
 <p className={TYPOGRAPHY.bodySm}>Please select a data table on the left first</p>
 </div>)}
 </div>
 </div>)}

 {createStep === 'FILL_FORM' && createType && createType!== 'DATA_ACCESS' && (<div className="p-8 space-y-4">
	 <div className="grid grid-cols-2 gap-4">
	 <div>
	 <label className={cn(TYPOGRAPHY.caption,'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
	 target audience
	 </label>
	 <input
	 value={genericTarget}
	 onChange={(event) => setGenericTarget(event.target.value)}
	 placeholder="Please enter the assets that require collaboration/service"
	 className={cn('w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:border-brand-500 outline-none',TYPOGRAPHY.bodySm)}
	 />
	 </div>
	 <div>
	 <label className={cn(TYPOGRAPHY.caption,'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
	 expected completion date
	 </label>
	 <input
	 type="date"
	 value={genericExpectedDate}
	 onChange={(event) => setGenericExpectedDate(event.target.value)}
	 className={cn('w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:border-brand-500 outline-none',TYPOGRAPHY.bodySm)}
	 />
	 </div>
	 </div>
	 <div className="grid grid-cols-2 gap-4">
	 <div>
	 <label className={cn(TYPOGRAPHY.caption,'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
	 priority
	 </label>
	 <select
	 value={genericPriority}
	 onChange={(event) => setGenericPriority(event.target.value as TicketPriority)}
	 className={cn('w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 focus:border-brand-500 outline-none',TYPOGRAPHY.bodySm)}
	 >
 <option value="HIGH">high</option>
 <option value="MEDIUM">in</option>
 <option value="LOW">low</option>
 </select>
	 </div>
	 <div>
	 <label className={cn(TYPOGRAPHY.caption,'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
	 label
	 </label>
	 <input
	 value={genericTags}
	 onChange={(event) => setGenericTags(event.target.value)}
	 placeholder="For example:publish,change"
	 className={cn('w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:border-brand-500 outline-none',TYPOGRAPHY.bodySm)}
	 />
	 </div>
	 </div>
	 <div>
	 <label className={cn(TYPOGRAPHY.caption,'font-bold text-slate-700 dark:text-slate-300 uppercase tracking-wide block mb-1.5')}>
	 Requirement description
	 </label>
	 <textarea
	 value={genericDescription}
	 onChange={(event) => setGenericDescription(event.target.value)}
	 placeholder="Please describe the changes/publish/Problem details"
	 className={cn('w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:border-brand-500 outline-none resize-none h-28',TYPOGRAPHY.bodySm)}
	 />
	 </div>
 </div>)}
 </div>
 </div>
 </Modal>
 </div>)
}

function RequestTypeCard({ type,onSelect }:{ type:RequestTypeConfig;onSelect:(typeId:TicketType) => void }) {
 return (<Card
 onClick={() => onSelect(type.id)}
 padding="none"
 variant="default"
 className={cn('flex-1 min-w-0 flex flex-col items-center justify-center p-6 border-2 border-slate-100 dark:border-slate-800 transition-all group text-center cursor-pointer',type.cardClass)}
 >
 <div
 className={cn('w-14 h-14 rounded-full flex items-center justify-center mb-4 group-hover:scale-110 transition-transform shadow-sm',type.iconClass)}
 >
 <type.icon size={28} />
 </div>
 <span className="text-subtitle font-bold text-slate-900 dark:text-slate-100 mb-2">{type.title}</span>
 <span className="text-caption text-slate-500 dark:text-slate-400 leading-relaxed px-2 line-clamp-3">{type.desc}</span>
 </Card>)
}
