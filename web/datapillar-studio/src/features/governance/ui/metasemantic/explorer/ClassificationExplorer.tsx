import { ArrowLeft,Plus,Shield,Stamp } from 'lucide-react'
import { iconSizeToken } from '@/design-tokens/dimensions'

/** Security level type */
type SecurityLevel = 'L1' | 'L2' | 'L3' | 'L4'

/** Hierarchical classification data structure */
interface Classification {
 id:string
 level:SecurityLevel
 name:string
 description:string
 piiCategories:string[]
 protectedAssets:number
}

/** Mock data */
const MOCK_CLASSIFICATIONS:Classification[] = [{
 id:'1',level:'L1',name:'public data',description:'Data that can be disclosed to the whole society,no secrets involved.',piiCategories:['general information','public documents'],protectedAssets:124
 },{
 id:'2',level:'L2',name:'internal data',description:'Data viewable only by company employees,Leakage will cause minor damage.',piiCategories:['internal projectsID','Non-sensitive remarks'],protectedAssets:124
 },{
 id:'3',level:'L3',name:'Sensitive data',description:'Involving user privacy,Leakage will cause greater legal risks or economic losses.',piiCategories:['Mobile phone number','Email','address','real name'],protectedAssets:124
 },{
 id:'4',level:'L4',name:'extremely confidential',description:'Involving the companys core business secrets or user financial security,Disclosure strictly prohibited.',piiCategories:['Bank card number','ID number','Password hash','Biometrics'],protectedAssets:124
 }]

/** Level configuration - portfolio style */
const LEVEL_CONFIG:Record<SecurityLevel,{
 stampColor:string
 stampBorder:string
 label:string
 folderTab:string
 tagStyle:string
 hoverBorder:string
}> = {
 L1:{
 stampColor:'text-emerald-600 dark:text-emerald-400',stampBorder:'border-emerald-400',label:'public',folderTab:'bg-emerald-500',tagStyle:'text-emerald-700 dark:text-emerald-300 bg-emerald-50 dark:bg-emerald-900/30 border border-emerald-200 dark:border-emerald-800',hoverBorder:'hover:border-emerald-400 dark:hover:border-emerald-400'
 },L2:{
 stampColor:'text-blue-600 dark:text-blue-400',stampBorder:'border-blue-400',label:'internal',folderTab:'bg-blue-500',tagStyle:'text-blue-700 dark:text-blue-300 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-800',hoverBorder:'hover:border-blue-400 dark:hover:border-blue-400'
 },L3:{
 stampColor:'text-amber-600 dark:text-amber-400',stampBorder:'border-amber-400',label:'sensitive',folderTab:'bg-amber-500',tagStyle:'text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800',hoverBorder:'hover:border-amber-400 dark:hover:border-amber-400'
 },L4:{
 stampColor:'text-rose-600 dark:text-rose-400',stampBorder:'border-rose-400',label:'Confidential',folderTab:'bg-rose-600',tagStyle:'text-rose-700 dark:text-rose-300 bg-rose-50 dark:bg-rose-900/30 border border-rose-200 dark:border-rose-800',hoverBorder:'hover:border-rose-500 dark:hover:border-rose-500'
 }
}

interface ClassificationExplorerProps {
 onBack:() => void
}

/** Portfolio card component */
function ClassificationCard({ item }:{ item:Classification }) {
 const config = LEVEL_CONFIG[item.level]

 return (<div className="group relative h-80">
 {/* Portfolio body - Tall look */}
 <div className={`relative h-full bg-white dark:bg-slate-900 border-2 border-slate-300 dark:border-slate-600 rounded-t-sm rounded-b-lg overflow-hidden shadow-sm hover:shadow-lg transition-all duration-300 ${config.hoverBorder}`}>
 {/* top seal - triangular fold effect */}
 <div className="absolute top-0 left-0 right-0">
 <div className="h-8 bg-slate-100 dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700" />
 <div className="absolute top-8 left-1/2 -translate-x-1/2 w-0 h-0 border-l-[20px] border-r-[20px] border-t-[12px] border-l-transparent border-r-transparent border-t-slate-100 dark:border-t-slate-800" />
 </div>

 {/* Folder index tag */}
 <div className={`absolute -top-1 left-4 w-14 h-7 ${config.folderTab} rounded-b-md shadow flex items-end justify-center pb-1`}>
 <span className="text-white text-micro font-bold">{item.level}</span>
 </div>

 {/* Security level seal */}
 <div className={`absolute top-14 right-3 w-14 h-14 rounded-full border-2 ${config.stampBorder} flex items-center justify-center rotate-[-15deg] opacity-60`}>
 <div className="text-center">
 <Stamp size={16} className={config.stampColor} />
 <span className={`block text-micro font-bold ${config.stampColor} mt-0.5`}>{config.label}</span>
 </div>
 </div>

 {/* content area */}
 <div className="pt-14 px-4 pb-4 h-full flex flex-col">
 {/* Title */}
 <h3 className="font-bold text-slate-800 dark:text-slate-100 text-body-sm mb-2">{item.name}</h3>

 {/* Description */}
 <p className="text-caption text-slate-500 dark:text-slate-400 leading-relaxed mb-4 line-clamp-2">{item.description}</p>

 {/* divider */}
 <div className="border-t border-dashed border-slate-200 dark:border-slate-700 my-3" />

 {/* PII Identify categories */}
 <div className="mb-2">
 <span className="text-micro text-slate-400 dark:text-slate-500 font-medium flex items-center gap-1">
 <Shield size={12} />
 PII Identify categories
 </span>
 </div>

 {/* tag group */}
 <div className="flex flex-wrap gap-1.5 flex-1">
 {item.piiCategories.map((category) => (<span
 key={category}
 className={`px-2 py-0.5 text-micro rounded h-fit ${config.tagStyle}`}
 >
 {category}
 </span>))}
 </div>

 {/* Bottom information */}
 <div className="flex items-center justify-between pt-3 border-t border-slate-100 dark:border-slate-800 mt-auto">
 <span className="text-micro text-slate-500 dark:text-slate-400">
 protected <span className="font-bold text-slate-700 dark:text-slate-200">{item.protectedAssets}</span>
 </span>
 <button className={`text-micro font-medium transition-colors ${config.stampColor} hover:opacity-80`}>
 Configuration →
 </button>
 </div>
 </div>

 {/* Left binding hole */}
 <div className="absolute left-2 top-1/2 -translate-y-1/2 flex flex-col gap-6">
 <div className="w-2 h-2 rounded-full bg-slate-200 dark:bg-slate-700" />
 <div className="w-2 h-2 rounded-full bg-slate-200 dark:bg-slate-700" />
 <div className="w-2 h-2 rounded-full bg-slate-200 dark:bg-slate-700" />
 </div>
 </div>
 </div>)
}

export function ClassificationExplorer({ onBack }:ClassificationExplorerProps) {
 return (<div className="flex-1 flex flex-col overflow-hidden bg-slate-50/40 dark:bg-slate-950/50 animate-in slide-in-from-right-4 duration-300">
 {/* top navigation bar */}
 <div className="h-12 @md:h-14 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 @md:px-6 flex items-center justify-between shadow-sm z-10 flex-shrink-0">
 <div className="flex items-center gap-2 @md:gap-3">
 <button
 onClick={onBack}
 className="p-1 @md:p-1.5 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg text-slate-400 transition-all"
 >
 <ArrowLeft size={iconSizeToken.large} />
 </button>
 <h2 className="text-body-sm @md:text-subtitle font-semibold text-slate-800 dark:text-slate-100">
 Classified safety regulations <span className="font-normal text-slate-400">(Classification)</span>
 </h2>
 </div>
 <button className="bg-slate-900 dark:bg-blue-600 text-white px-3 @md:px-4 py-1 @md:py-1.5 rounded-lg text-caption @md:text-body-sm font-medium flex items-center gap-1 @md:gap-1.5 shadow-md hover:bg-blue-600 dark:hover:bg-blue-500 transition-all">
 <Plus size={iconSizeToken.medium} /> <span className="hidden @md:inline">Add new level</span>
 </button>
 </div>

 {/* card list */}
 <div className="flex-1 min-h-0 p-4 @md:p-6 overflow-auto custom-scrollbar">
 <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 @md:gap-5">
 {MOCK_CLASSIFICATIONS.map((item) => (<ClassificationCard key={item.id} item={item} />))}
 </div>
 </div>
 </div>)
}
