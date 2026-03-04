import type { LucideIcon } from 'lucide-react'
import {
 Activity,AlertCircle,CheckCircle2,Clock,Database,FileDiff,GitPullRequest,Globe,Lock,Server,XCircle
} from 'lucide-react'
import type { TicketPriority,TicketStatus,TicketType } from './types'

export type RequestTypeConfig = {
 id:TicketType
 title:string
 desc:string
 icon:LucideIcon
 cardClass:string
 iconClass:string
 accentClass:string
}

export const requestTypeConfig:RequestTypeConfig[] = [{
 id:'DATA_ACCESS',title:'Data permissions',desc:'Apply for table-level or field-level read permissions,Support automatic identification of sensitive fields.',icon:Database,cardClass:'hover:border-blue-500 hover:bg-blue-50/30 dark:hover:border-blue-600 dark:hover:bg-blue-900/20',iconClass:'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400',accentClass:'text-blue-600 dark:text-blue-400'
 },{
 id:'CODE_REVIEW',title:'code review',desc:'Submit ETL Task or data flow changes,Trigger downstream impact analysis.',icon:GitPullRequest,cardClass:'hover:border-purple-500 hover:bg-purple-50/30 dark:hover:border-purple-600 dark:hover:bg-purple-900/20',iconClass:'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400',accentClass:'text-purple-600 dark:text-purple-400'
 },{
 id:'RESOURCE_OPS',title:'Resource operation and maintenance',desc:'Apply for computing cluster expansion or adjust queue quota priority.',icon:Server,cardClass:'hover:border-orange-500 hover:bg-orange-50/30 dark:hover:border-orange-600 dark:hover:bg-orange-900/20',iconClass:'bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400',accentClass:'text-orange-600 dark:text-orange-400'
 },{
 id:'API_PUBLISH',title:'Service release',desc:'Publish the data table as high concurrency API interface,Configure current limiting and authentication.',icon:Globe,cardClass:'hover:border-emerald-500 hover:bg-emerald-50/30 dark:hover:border-emerald-600 dark:hover:bg-emerald-900/20',iconClass:'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400',accentClass:'text-emerald-600 dark:text-emerald-400'
 },{
 id:'SCHEMA_CHANGE',title:'Model changes',desc:'Apply DDL change(Add Column / Index),Execute online Schema change.',icon:FileDiff,cardClass:'hover:border-pink-500 hover:bg-pink-50/30 dark:hover:border-pink-600 dark:hover:bg-pink-900/20',iconClass:'bg-pink-100 text-pink-600 dark:bg-pink-900/30 dark:text-pink-400',accentClass:'text-pink-600 dark:text-pink-400'
 },{
 id:'DQ_REPORT',title:'Quality reporting',desc:'Report data anomalies or SLA delay,Trigger quality work order transfer.',icon:Activity,cardClass:'hover:border-red-500 hover:bg-red-50/30 dark:hover:border-red-600 dark:hover:bg-red-900/20',iconClass:'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',accentClass:'text-red-600 dark:text-red-400'
 }]

export const requestTypeMap = requestTypeConfig.reduce((acc,item) => {
 acc[item.id] = item
 return acc
 },{} as Record<TicketType,RequestTypeConfig>)

export const statusConfigMap:Record<TicketStatus,{ label:string;color:string;icon:LucideIcon }> = {
 PENDING:{ color:'bg-blue-50 text-blue-600 border-blue-100 dark:bg-blue-900/30 dark:text-blue-400 dark:border-blue-800',icon:Clock,label:'Pending' },APPROVED:{ color:'bg-emerald-50 text-emerald-600 border-emerald-100 dark:bg-emerald-900/30 dark:text-emerald-400 dark:border-emerald-800',icon:CheckCircle2,label:'Approved' },REJECTED:{ color:'bg-rose-50 text-rose-600 border-rose-100 dark:bg-rose-900/30 dark:text-rose-400 dark:border-rose-800',icon:XCircle,label:'Rejected' },CHANGES_REQUESTED:{ color:'bg-amber-50 text-amber-600 border-amber-100 dark:bg-amber-900/30 dark:text-amber-400 dark:border-amber-800',icon:AlertCircle,label:'Need to modify' }
}

export const priorityConfigMap:Record<TicketPriority,{ label:string;className:string }> = {
 HIGH:{ label:'high',className:'bg-rose-50 text-rose-600 dark:bg-rose-900/30 dark:text-rose-400' },MEDIUM:{ label:'in',className:'bg-amber-50 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400' },LOW:{ label:'low',className:'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300' }
}

export const statusFilterOptions:Array<{ value:TicketStatus | 'ALL';label:string }> = [{ value:'ALL',label:'All status' },{ value:'PENDING',label:statusConfigMap.PENDING.label },{ value:'CHANGES_REQUESTED',label:statusConfigMap.CHANGES_REQUESTED.label },{ value:'APPROVED',label:statusConfigMap.APPROVED.label },{ value:'REJECTED',label:statusConfigMap.REJECTED.label }]

export const typeIconMap:Record<TicketType,{ icon:LucideIcon;className:string }> = {
 DATA_ACCESS:{ icon:Lock,className:'text-blue-500' },CODE_REVIEW:{ icon:GitPullRequest,className:'text-purple-500' },RESOURCE_OPS:{ icon:Server,className:'text-orange-500' },API_PUBLISH:{ icon:Globe,className:'text-emerald-500' },SCHEMA_CHANGE:{ icon:FileDiff,className:'text-pink-500' },DQ_REPORT:{ icon:Activity,className:'text-red-500' }
}
