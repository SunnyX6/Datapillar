import { useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { StackTaskManager } from './StackTaskManager'
import { StackWorkflowList } from './StackWorkflowList'
import type { StackEnv,WorkflowDefinition } from './types'

const mockWorkflows:WorkflowDefinition[] = [{
 id:'wf_001',name:'Main_Nightly_ETL_Flow',description:'Core transaction link T+1 Offline batch processing,contains ODS Arrive DWS full-link computing.',schedule:'0 2 * * *',status:'running',lastRun:'Yesterday 02:00',nextRun:'Today 02:00',avgDuration:'45m',owner:'Data Team',tags:['Core','P0']
 },{
 id:'wf_002',name:'Hourly_User_Behavior_Agg',description:'Hourly aggregation of user click streams,Used for real-time dashboard data support.',schedule:'0 * * * *',status:'healthy',lastRun:'15 mins ago',nextRun:'45 mins',avgDuration:'4m 12s',owner:'Analyst A',tags:['Traffic','P1']
 },{
 id:'wf_003',name:'Finance_Reconciliation_Month',description:'Monthly financial reconciliation and difference report generation.',schedule:'0 0 1 * *',status:'paused',lastRun:'2023-10-01',nextRun:'-',avgDuration:'2h 10m',owner:'Finance IT',tags:['Finance']
 },{
 id:'wf_004',name:'Marketing_Campaign_Sync_V2',description:'Marketing activity data return and performance attribution calculation.',schedule:'0 6 * * *',status:'error',lastRun:'Today 06:00',nextRun:'Tomorrow 06:00',avgDuration:'12m',owner:'Marketing Dev',tags:['Marketing']
 },{
 id:'wf_005',name:'Log_Archival_S3',description:'The historical log cold backup is archived to S3 bucket.',schedule:'0 1 * * *',status:'healthy',lastRun:'Today 01:00',nextRun:'Tomorrow 01:00',avgDuration:'1h 30m',owner:'Ops Team',tags:['Ops','Maint']
 },{
 id:'wf_006',name:'RAG_Vector_Update_Incremental',description:'Knowledge base vector incremental update task,docking Wiki Document source.',schedule:'*/15 * * * *',status:'warning',lastRun:'5 mins ago',nextRun:'10 mins',avgDuration:'2m',owner:'AI Lab',tags:['AI','Vector']
 },{
 id:'wf_007',name:'Daily_Customer_360_Build',description:'daily customers 360 Portrait construction,Includes label calculation and wide table implementation.',schedule:'0 3 * * *',status:'healthy',lastRun:'Today 03:00',nextRun:'Tomorrow 03:00',avgDuration:'18m',owner:'CDP Team',tags:['CDP','P1']
 },{
 id:'wf_008',name:'Realtime_Anomaly_Detection',description:'Real-time anomaly detection feature aggregation and alarm push.',schedule:'*/5 * * * *',status:'warning',lastRun:'2 mins ago',nextRun:'3 mins',avgDuration:'1m 08s',owner:'SRE',tags:['Realtime','Alert']
 },{
 id:'wf_009',name:'Dimension_SCD2_Sync',description:'Dimensions SCD2 Synchronization and historical zip list maintenance.',schedule:'0 4 * * *',status:'running',lastRun:'Today 04:00',nextRun:'Tomorrow 04:00',avgDuration:'28m',owner:'Data Platform',tags:['Dim','P1']
 },{
 id:'wf_010',name:'BI_Dashboard_Refresh',description:'BI Kanban data refresh and cache warm-up.',schedule:'0 */2 * * *',status:'healthy',lastRun:'1 hour ago',nextRun:'1 hour',avgDuration:'6m 35s',owner:'BI Team',tags:['BI','P2']
 },{
 id:'wf_011',name:'Data_Quality_Rules_Check',description:'Data quality rule inspection(uniqueness/Not empty/Fluctuation),Generate daily report.',schedule:'0 7 * * *',status:'error',lastRun:'Today 07:00',nextRun:'Tomorrow 07:00',avgDuration:'9m',owner:'DQ Team',tags:['DQ','P0']
 },{
 id:'wf_012',name:'Iceberg_Table_Compaction',description:'Iceberg Table small file merging and metadata cleaning,Reduce query overhead.',schedule:'0 1 * * 0',status:'paused',lastRun:'Last Sunday 01:00',nextRun:'-',avgDuration:'1h 05m',owner:'Platform Ops',tags:['Maintenance','P2']
 }]

type DWStackOverViewProps = {
 projectName:string
 projectEnv:StackEnv
 stackName?: string
 onBack:() => void
}

export function DWStackOverView({ projectEnv,projectName,stackName = 'DW Stack',onBack }:DWStackOverViewProps) {
 const [selectedWorkflow,setSelectedWorkflow] = useState<WorkflowDefinition | null>(null)

 if (selectedWorkflow) {
 return <StackTaskManager workflow={selectedWorkflow} onBack={() => setSelectedWorkflow(null)} />
 }

 return (<div className="flex flex-col h-full bg-slate-50/40 dark:bg-slate-900 relative overflow-hidden animate-in fade-in duration-300 @container">
 <div className="h-16 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between px-6 flex-shrink-0 z-20 shadow-sm">
 <div className="flex items-center">
 <button
 onClick={onBack}
 className="mr-4 p-2 text-slate-400 hover:text-slate-900 hover:bg-slate-100 dark:hover:text-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors group"
 >
 <ArrowLeft size={18} className="group-hover:-translate-x-1 transition-transform" />
 </button>
 <div>
 <div className="flex items-center space-x-2 text-legal text-slate-500 dark:text-slate-400 mb-0.5 font-medium">
 <span>{projectName}</span>
 <span className="text-slate-300 dark:text-slate-700">/</span>
 <span className="text-slate-900 dark:text-slate-100">{stackName}</span>
 </div>
 <h1 className="text-body-sm @md:text-subtitle font-semibold text-slate-900 dark:text-slate-100 tracking-tight flex items-center">
 {stackName} Overview
 <span
 className={`ml-3 px-2 py-0.5 rounded text-micro font-bold border ${
 projectEnv === 'PROD'?'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-200 dark:border-emerald-500/20':projectEnv === 'STAGING'?'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-500/10 dark:text-amber-200 dark:border-amber-500/20':'bg-blue-50 text-blue-700 border-blue-100 dark:bg-blue-500/10 dark:text-blue-200 dark:border-blue-500/20'
 }`}
 >
 {projectEnv}
 </span>
 </h1>
 </div>
 </div>

 </div>

 <StackWorkflowList workflows={mockWorkflows} onSelect={setSelectedWorkflow} />
 </div>)
}
