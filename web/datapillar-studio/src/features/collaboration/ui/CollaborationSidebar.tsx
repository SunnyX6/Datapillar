import type { LucideIcon } from 'lucide-react'
import { Archive,AtSign,GitPullRequest,Inbox,Lock,Plus,Send,Server,ShieldAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui'
import { iconSizeToken,panelWidthClassMap } from '@/design-tokens/dimensions'
import { TYPOGRAPHY } from '@/design-tokens/typography'
import { cn } from '@/utils'
import type { CollaborationSidebarNav,QuickFilter,SmartView,TicketView } from '../utils/types'

interface CollaborationSidebarProps {
 activeNav:CollaborationSidebarNav
 inboxCount:number
 sentCount:number
 archiveCount:number
 mentionedCount:number
 urgentCount:number
 dataAccessCount:number
 codeReviewCount:number
 infraOpsCount:number
 onCreate:() => void
 onChangeView:(view:TicketView) => void
 onChangeSmartView:(view:SmartView) => void
 onChangeQuickFilter:(filter:QuickFilter) => void
}

export function CollaborationSidebar({
 activeNav,inboxCount,sentCount,archiveCount,mentionedCount,urgentCount,dataAccessCount,codeReviewCount,infraOpsCount,onCreate,onChangeView,onChangeSmartView,onChangeQuickFilter
}:CollaborationSidebarProps) {
 const { t } = useTranslation('collaboration')
 const folders:Array<{
 view:TicketView
 label:string
 count:number
 icon:LucideIcon
 iconClassName:string
 }> = [{
 view:'INBOX',label:t('sidebar.folders.inbox'),count:inboxCount,icon:Inbox,iconClassName:'text-blue-500'
 },{
 view:'SENT',label:t('sidebar.folders.sent'),count:sentCount,icon:Send,iconClassName:'text-violet-500'
 },{
 view:'ARCHIVE',label:t('sidebar.folders.archive'),count:archiveCount,icon:Archive,iconClassName:'text-amber-500'
 }]

 const smartViews:Array<{
 view:SmartView
 label:string
 count:number
 icon:LucideIcon
 iconClassName:string
 }> = [{
 view:'MENTIONED',label:t('sidebar.smartViews.mentioned'),count:mentionedCount,icon:AtSign,iconClassName:'text-indigo-500'
 },{
 view:'URGENT',label:t('sidebar.smartViews.urgent'),count:urgentCount,icon:ShieldAlert,iconClassName:'text-rose-500'
 }]

 const quickFilters:Array<{
 filter:QuickFilter
 label:string
 count:number
 icon:LucideIcon
 iconClassName:string
 }> = [{
 filter:'DATA_ACCESS',label:t('sidebar.quickFilters.dataAccess'),count:dataAccessCount,icon:Lock,iconClassName:'text-blue-500'
 },{
 filter:'CODE_REVIEW',label:t('sidebar.quickFilters.codeReview'),count:codeReviewCount,icon:GitPullRequest,iconClassName:'text-purple-500'
 },{
 filter:'RESOURCE_OPS',label:t('sidebar.quickFilters.infraOps'),count:infraOpsCount,icon:Server,iconClassName:'text-orange-500'
 }]

 return (<div
 className={cn(panelWidthClassMap.compactResponsive,'bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-800 flex flex-col flex-shrink-0')}
 >
 <div
 className={cn('flex flex-col min-h-0 flex-1','px-3 pt-6 pb-4 lg:px-4 lg:pt-8 lg:pb-6')}
 >
 <div className={cn('flex items-center justify-between mb-4')}>
 <h3
 className={cn(TYPOGRAPHY.caption,'font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider')}
 >
 {t('sidebar.title')}
 </h3>
 </div>

 <div className={cn('flex-1 min-h-0 overflow-y-auto custom-scrollbar pb-6')}>
 <div className="space-y-2.5">
 {folders.map((folder) => (<SidebarItemButton
 key={folder.view}
 label={folder.label}
 count={folder.count}
 icon={folder.icon}
 iconClassName={folder.iconClassName}
 isActive={activeNav.kind === 'FOLDER' && activeNav.view === folder.view}
 onClick={() => onChangeView(folder.view)}
 />))}
 </div>

 <div className="mt-3 pt-3 border-t border-slate-200 dark:border-slate-800">
 <div className={cn(TYPOGRAPHY.micro,'px-3 mb-2 font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider')}>
 {t('sidebar.sections.smartViews')}
 </div>
 <div className="space-y-2.5">
 {smartViews.map((smartView) => (<SidebarItemButton
 key={smartView.view}
 label={smartView.label}
 count={smartView.count}
 icon={smartView.icon}
 iconClassName={smartView.iconClassName}
 isActive={activeNav.kind === 'SMART_VIEW' && activeNav.view === smartView.view}
 onClick={() => onChangeSmartView(smartView.view)}
 />))}
 </div>
 </div>

 <div className="mt-3 pt-3 border-t border-slate-200 dark:border-slate-800">
 <div className={cn(TYPOGRAPHY.micro,'px-3 mb-2 font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider')}>
 {t('sidebar.sections.quickFilters')}
 </div>
 <div className="space-y-2.5">
 {quickFilters.map((quickFilter) => (<SidebarItemButton
 key={quickFilter.filter}
 label={quickFilter.label}
 count={quickFilter.count}
 icon={quickFilter.icon}
 iconClassName={quickFilter.iconClassName}
 isActive={activeNav.kind === 'QUICK_FILTER' && activeNav.filter === quickFilter.filter}
 onClick={() => onChangeQuickFilter(quickFilter.filter)}
 />))}
 </div>
 </div>
 </div>
 </div>

 <div className={cn('border-t border-slate-200 dark:border-slate-800 px-3 py-3 lg:px-4')}>
 <Button
 type="button"
 onClick={onCreate}
 variant="primary"
 size="small"
 className="w-full py-2"
 >
 <Plus size={iconSizeToken.small} />
 {t('sidebar.createButton')}
 </Button>
 </div>
 </div>)
}

function SidebarItemButton({
 label,count,icon:Icon,iconClassName,isActive,onClick
}:{
 label:string
 count:number
 icon:LucideIcon
 iconClassName:string
 isActive:boolean
 onClick:() => void
}) {
 return (<Button
 type="button"
 onClick={onClick}
 variant="ghost"
 size="small"
 className={cn(
 // Collaboration sidebar menu items do not require floating background color(Cover Button ghost Default hover background).
 'group flex w-full items-center gap-2 rounded-lg px-3 py-1 text-left transition-colors shadow-none hover:shadow-none hover:bg-transparent! dark:hover:bg-transparent! focus:outline-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0',
 // Interactive alignment global Sidebar:Only change the color when selected/background color,Not made by bolding"get bigger"illusion
 isActive?'text-blue-600 dark:text-blue-400':'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-slate-100'
 )}
 >
 <Icon
 size={iconSizeToken.tiny}
 className={cn('shrink-0 transition-opacity',isActive?'text-blue-600 dark:text-blue-400':iconClassName,isActive?'opacity-100':'opacity-70 group-hover:opacity-100')}
 />
 <span
 className={cn(TYPOGRAPHY.bodyXs,'flex-1 truncate',isActive?'text-blue-600 dark:text-blue-400':'text-slate-600 dark:text-slate-300 group-hover:text-slate-900 dark:group-hover:text-slate-100')}
 >
 {label}
 </span>
 {count > 0 && (<span
 className={cn(TYPOGRAPHY.micro,// Fixed row height:badge Height should not exceed text line height,avoid count 0/Not 0 The entire row height jumps when switching
 'inline-flex h-4 min-w-[1.25rem] items-center justify-center rounded-md bg-slate-200 dark:bg-slate-800 px-1 font-semibold text-slate-700 dark:text-slate-200')}
 >
 {count}
 </span>)}
 </Button>)
}
