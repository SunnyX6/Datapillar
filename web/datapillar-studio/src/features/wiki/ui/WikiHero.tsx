import { BookOpen,Layers,Search,Sparkles,Plus } from 'lucide-react'
import { Button,Card } from '@/components/ui'
import { contentMaxWidthClassMap,paddingClassMap } from '@/design-tokens/dimensions'
import { RESPONSIVE_TYPOGRAPHY,TYPOGRAPHY } from '@/design-tokens/typography'

type WikiHeroProps = {
 onCreate:() => void
}

const HERO_FEATURES = [{
 title:'business isolation',description:'Create separate spaces by department or project,Permissions and data security physical isolation.',icon:Layers,tone:'text-indigo-600 bg-indigo-50 dark:bg-indigo-500/10 dark:text-indigo-300'
 },{
 title:'Multiple segmentation strategies',description:'upload PDF/Word,Select a segmentation strategy according to document type and complete vectorization processing.',icon:Sparkles,tone:'text-emerald-600 bg-emerald-50 dark:bg-emerald-500/10 dark:text-emerald-300'
 },{
 title:'Multiple recall',description:'Support semantic search,Hybrid retrieval and reordering,Query can be initiated based on the entire database or a single document range.',icon:Search,tone:'text-sky-600 bg-sky-50 dark:bg-sky-500/10 dark:text-sky-300'
 }]

export function WikiHero({ onCreate }:WikiHeroProps) {
 return (<div className="relative h-full w-full overflow-hidden @container">
 <div className="absolute inset-0 bg-[radial-gradient(#e2e8f0_1px,transparent_1px)] dark:bg-[radial-gradient(#1e293b_1px,transparent_1px)] [background-size:18px_18px] opacity-60" />
 <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,_rgba(99,102,241,0.18)_0%,_transparent_60%)] dark:opacity-80" />

 <div className="relative z-10 flex h-full w-full items-start justify-center pt-24 @md:pt-28 @xl:pt-36 pb-10 @md:pb-12">
 <div className={`w-full ${contentMaxWidthClassMap.normal} ${paddingClassMap.md} h-full py-0 @md:py-0`}>
 <div className="flex h-full flex-col items-center text-center">
 <div className="flex flex-col items-center text-center mt-4 @md:mt-6">
 <div className="relative mb-6">
 <div className="absolute inset-0 rounded-3xl bg-indigo-500/15 blur-3xl" />
 <div className="relative size-16 @md:size-20 rounded-2xl border border-indigo-200/70 dark:border-indigo-500/30 bg-white/80 dark:bg-slate-900/70 flex items-center justify-center shadow-lg">
 <BookOpen size={30} className="text-indigo-500 dark:text-indigo-300" />
 </div>
 </div>

 <h1 className={`${RESPONSIVE_TYPOGRAPHY.pageTitle} font-bold text-slate-900 dark:text-slate-100 tracking-tight`}>
 Build Datapillar Enterprise knowledge brain
 </h1>
 <p className={`mt-3 ${TYPOGRAPHY.body} text-slate-500 dark:text-slate-400 max-w-2xl`}>
 Datapillar Wiki is a new generation RAG knowledge engine.By creating independent knowledge spaces(Namespace),You can isolate documents from different business domains,and use AI Perform deep semantic searches.</p>
 <Button
 variant="primary"
 size="large"
 className="mt-6 shadow-lg hover:shadow-xl"
 onClick={onCreate}
 >
 <Plus size={16} />
 Create the first knowledge space
 </Button>
 </div>

 <div className="mt-auto @xl:mt-20 w-full pb-4 @md:pb-6">
 <div className="grid grid-cols-1 @md:grid-cols-3 gap-4">
 {HERO_FEATURES.map((feature) => {
 const Icon = feature.icon
 return (<Card key={feature.title} padding="sm" className="text-left h-full">
 <div className="flex flex-col">
 <div className={`size-10 rounded-xl flex items-center justify-center ${feature.tone}`}>
 <Icon size={18} />
 </div>
 <div className="mt-4">
 <h3 className={`${TYPOGRAPHY.body} font-semibold text-slate-800 dark:text-slate-100`}>
 {feature.title}
 </h3>
 <p className={`${TYPOGRAPHY.caption} text-slate-500 dark:text-slate-400 mt-1`}>
 {feature.description}
 </p>
 </div>
 </div>
 </Card>)
 })}
 </div>
 </div>
 </div>
 </div>
 </div>
 </div>)
}
