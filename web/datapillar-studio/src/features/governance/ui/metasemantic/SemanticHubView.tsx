import { useMemo,useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Target,Book,Shield,Cpu,Globe,Workflow,Zap,Braces,ListChecks,ShieldCheck } from 'lucide-react'
import { HubAssetCard } from './components'
import type { SemanticCategory,CategoryConfig } from './types'
import { iconSizeToken } from '@/design-tokens/dimensions'
import { useSemanticStatsStore } from '@/features/governance/state'
import { fetchMetrics,fetchWordRoots } from '@/services/oneMetaSemanticService'

/** Category to route mapping */
const CATEGORY_ROUTES:Record<SemanticCategory,string> = {
 HOME:'/governance/semantic',METRICS:'/governance/semantic/metrics',GLOSSARY:'/governance/semantic/wordroots',STANDARDS:'/governance/semantic/standards',MODELS:'/governance/semantic/models',APIS:'/governance/semantic/apis'
}

export function SemanticHubView() {
 const navigate = useNavigate()
 const metricsTotal = useSemanticStatsStore((state) => state.metricsTotal)
 const wordRootsTotal = useSemanticStatsStore((state) => state.wordRootsTotal)
 const setMetricsTotal = useSemanticStatsStore((state) => state.setMetricsTotal)
 const setWordRootsTotal = useSemanticStatsStore((state) => state.setWordRootsTotal)

 // Get statistics on first load(Get only total,limit=1 Reduce data transfer)
 useEffect(() => {
 if (metricsTotal === null) {
 fetchMetrics(0,1).then((res) => setMetricsTotal(res.total)).catch(() => {})
 }
 if (wordRootsTotal === null) {
 fetchWordRoots(0,1).then((res) => setWordRootsTotal(res.total)).catch(() => {})
 }
 },[metricsTotal,wordRootsTotal,setMetricsTotal,setWordRootsTotal])

 const categories:CategoryConfig[] = useMemo(() => [{
 id:'METRICS',label:'indicator center',icon:Target,color:'bg-purple-600',description:'Unified business caliber,Precipitated enterprise atomic index and derived index system.',count:metricsTotal?? 0,trend:'NEW'
 },{
 id:'GLOSSARY',label:'canonical root',icon:Book,color:'bg-blue-600',description:'The cornerstone of data standardized naming,Standardize field semantics and physical naming.',count:wordRootsTotal?? 0
 },{
 id:'STANDARDS',label:'Standard specifications',icon:Shield,color:'bg-emerald-600',description:'data type standards,Value range constraints and hierarchical classification safety specifications.',count:12,subEntries:[{ id:'datatype',label:'data type',icon:Braces,route:'/governance/semantic/standards/datatypes' },{ id:'valuedomain',label:'range',icon:ListChecks,route:'/governance/semantic/standards/valuedomains' },{ id:'security',label:'Classification',icon:ShieldCheck,route:'/governance/semantic/standards/security' }]
 },{
 id:'MODELS',label:'AI Features',icon:Cpu,color:'bg-orange-500',description:'Model feature database and feature lineage,speed up AI Scene data supply.',count:8
 },{
 id:'APIS',label:'Data services',icon:Globe,color:'bg-cyan-500',description:'Data-as-a-Service,Unified management API Service metadata.',count:24
 }],[metricsTotal,wordRootsTotal])

 const handleCategoryClick = (categoryId:SemanticCategory) => {
 const route = CATEGORY_ROUTES[categoryId]
 if (route) {
 navigate(route)
 }
 }

 const handleSubEntryClick = (route:string) => {
 navigate(route)
 }

 return (<div className="flex h-full w-full overflow-hidden bg-white dark:bg-slate-900 @container">
 <div className="flex-1 overflow-auto p-4 @md:p-6 @xl:p-8 custom-scrollbar">
 <div className="animate-in fade-in duration-500">
 <div className="mb-6 @md:mb-8">
 <h1 className="text-heading @md:text-title @xl:text-display font-black text-slate-900 dark:text-slate-100 tracking-tight">
 One Meta <span className="text-blue-600">Semantic</span>
 </h1>
 <p className="text-slate-500 dark:text-slate-400 mt-2 text-body-sm @md:text-body">Enterprise-level semantic asset lake,Connect business definitions to physical data.</p>
 </div>

 <div className="grid grid-cols-1 @md:grid-cols-2 @xl:grid-cols-3 gap-4 @md:gap-6">
 {categories.map((cat) => (<HubAssetCard key={cat.id} config={cat} onClick={() => handleCategoryClick(cat.id)} onSubEntryClick={handleSubEntryClick} />))}
 <div className="@md:col-span-2 @xl:col-span-3 bg-gradient-to-br from-blue-700 via-blue-600 to-indigo-700 rounded-xl @md:rounded-2xl p-6 @md:p-8 flex items-center justify-between text-white shadow-lg overflow-hidden relative group">
 <div className="relative z-10 max-w-lg">
 <h3 className="text-body @md:text-subtitle @xl:text-title font-bold mb-2 @md:mb-3">want to AI Understand your data better?</h3>
 <p className="text-blue-100 mb-4 @md:mb-6 text-caption @md:text-body-sm opacity-90">
 Pass One Meta Improve physical assets,Metadata such as semantics,Can significantly improve enterprise-level AI Agent The accuracy and quality of automatic report generation.</p>
 <button className="bg-white text-blue-600 font-semibold px-4 @md:px-6 py-2 @md:py-2.5 rounded-lg @md:rounded-xl shadow-md hover:scale-105 transition-all flex items-center gap-2 text-caption @md:text-body-sm">
 <Zap size={iconSizeToken.medium} /> Turn on semantic enhancement now
 </button>
 </div>
 <div className="opacity-10 absolute right-0 top-0 bottom-0 pointer-events-none group-hover:scale-110 transition-transform duration-1000 hidden @md:block">
 <Workflow size={320} className="translate-x-24 -translate-y-8" />
 </div>
 </div>
 </div>
 </div>
 </div>
 </div>)
}
