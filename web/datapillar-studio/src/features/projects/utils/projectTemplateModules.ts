/**
 * Project creation - Technology stack blueprint and default module mapping
 *
 * Description:* - This mapping is used for"Create new project"Initialize the module list in the project card(mock data)
 * - Need to keep the order stable,Prevent users from jumping around in module sorting due to different click orders.*/

export type ModuleType = 'offline' | 'realtime' | 'vector' | 'serving' | 'bi'

export interface ProjectModule {
 type:ModuleType
 name:string
 status:'active' | 'beta' | 'maintenance'
 stats:string
 load:number
}

export type ProjectTemplateType = 'BATCH_ETL' | 'STREAM_PROCESS' | 'RAG_KNOWLEDGE' | 'DATA_SERVICE'

const TEMPLATE_ORDER:ProjectTemplateType[] = ['BATCH_ETL','STREAM_PROCESS','RAG_KNOWLEDGE','DATA_SERVICE']

const TEMPLATE_MODULES:Record<ProjectTemplateType,ProjectModule[]> = {
 BATCH_ETL:[{ type:'offline',name:'Offline data warehouse',status:'active',stats:'Empty pipeline',load:0 }],STREAM_PROCESS:[{ type:'realtime',name:'real time calculation',status:'active',stats:'Empty job',load:0 }],RAG_KNOWLEDGE:[{ type:'vector',name:'vector index',status:'active',stats:'0 Documentation',load:0 },{ type:'offline',name:'Clean pipes',status:'active',stats:'Default process',load:0 }],DATA_SERVICE:[{ type:'serving',name:'data API',status:'active',stats:'0 interface',load:0 }]
}

const normalizeTemplateSelection = (templates:ProjectTemplateType[]) => {
 const set = new Set<ProjectTemplateType>(templates)
 return TEMPLATE_ORDER.filter((tpl) => set.has(tpl))
}

export function buildInitialModulesFromTemplates(templates:ProjectTemplateType[]):ProjectModule[] {
 const normalized = normalizeTemplateSelection(templates)
 return normalized.flatMap((tpl) => TEMPLATE_MODULES[tpl].map((mod) => ({...mod })))
}
