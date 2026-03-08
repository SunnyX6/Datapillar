import { useMemo,useEffect } from 'react'
import { useTranslation } from 'react-i18next'
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
 const { t } = useTranslation('oneSemantics')
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
 id:'METRICS',label:t('semanticHub.categories.metrics.label'),icon:Target,color:'bg-purple-600',description:t('semanticHub.categories.metrics.description'),count:metricsTotal?? 0,trend:t('semanticHub.categories.metrics.trend')
 },{
 id:'GLOSSARY',label:t('semanticHub.categories.wordRoot.label'),icon:Book,color:'bg-blue-600',description:t('semanticHub.categories.wordRoot.description'),count:wordRootsTotal?? 0
 },{
 id:'STANDARDS',label:t('semanticHub.categories.standards.label'),icon:Shield,color:'bg-emerald-600',description:t('semanticHub.categories.standards.description'),count:12,subEntries:[{ id:'datatype',label:t('semanticHub.categories.standards.sub.dataType'),icon:Braces,route:'/governance/semantic/standards/datatypes' },{ id:'valuedomain',label:t('semanticHub.categories.standards.sub.valueDomain'),icon:ListChecks,route:'/governance/semantic/standards/valuedomains' },{ id:'security',label:t('semanticHub.categories.standards.sub.classification'),icon:ShieldCheck,route:'/governance/semantic/standards/security' }]
 },{
 id:'MODELS',label:t('semanticHub.categories.models.label'),icon:Cpu,color:'bg-orange-500',description:t('semanticHub.categories.models.description'),count:8
 },{
 id:'APIS',label:t('semanticHub.categories.apis.label'),icon:Globe,color:'bg-cyan-500',description:t('semanticHub.categories.apis.description'),count:24
 }],[metricsTotal,wordRootsTotal,t])

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
 {t('semanticHub.title.prefix')} <span className="text-blue-600">{t('semanticHub.title.highlight')}</span>
 </h1>
 <p className="text-slate-500 dark:text-slate-400 mt-2 text-body-sm @md:text-body">{t('semanticHub.subtitle')}</p>
 </div>

 <div className="grid grid-cols-1 @md:grid-cols-2 @xl:grid-cols-3 gap-4 @md:gap-6">
 {categories.map((cat) => (<HubAssetCard key={cat.id} config={cat} onClick={() => handleCategoryClick(cat.id)} onSubEntryClick={handleSubEntryClick} />))}
 <div className="@md:col-span-2 @xl:col-span-3 bg-gradient-to-br from-blue-700 via-blue-600 to-indigo-700 rounded-xl @md:rounded-2xl p-6 @md:p-8 flex items-center justify-between text-white shadow-lg overflow-hidden relative group">
 <div className="relative z-10 max-w-lg">
 <h3 className="text-body @md:text-subtitle @xl:text-title font-bold mb-2 @md:mb-3">{t('semanticHub.hero.title')}</h3>
 <p className="text-blue-100 mb-4 @md:mb-6 text-caption @md:text-body-sm opacity-90">
 {t('semanticHub.hero.description')}</p>
 <button className="bg-white text-blue-600 font-semibold px-4 @md:px-6 py-2 @md:py-2.5 rounded-lg @md:rounded-xl shadow-md hover:scale-105 transition-all flex items-center gap-2 text-caption @md:text-body-sm">
 <Zap size={iconSizeToken.medium} /> {t('semanticHub.hero.cta')}
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
