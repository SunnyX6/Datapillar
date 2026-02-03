/**
 * 项目创建 - 技术栈蓝图与默认模块映射
 *
 * 说明：
 * - 该映射用于「创建新项目」时初始化项目卡片里的模块列表（mock 数据）
 * - 需要保持顺序稳定，避免用户因点击顺序不同导致模块排序乱跳
 */

export type ModuleType = 'offline' | 'realtime' | 'vector' | 'serving' | 'bi'

export interface ProjectModule {
  type: ModuleType
  name: string
  status: 'active' | 'beta' | 'maintenance'
  stats: string
  load: number
}

export type ProjectTemplateType = 'BATCH_ETL' | 'STREAM_PROCESS' | 'RAG_KNOWLEDGE' | 'DATA_SERVICE'

const TEMPLATE_ORDER: ProjectTemplateType[] = ['BATCH_ETL', 'STREAM_PROCESS', 'RAG_KNOWLEDGE', 'DATA_SERVICE']

const TEMPLATE_MODULES: Record<ProjectTemplateType, ProjectModule[]> = {
  BATCH_ETL: [{ type: 'offline', name: '离线数仓', status: 'active', stats: '空流水线', load: 0 }],
  STREAM_PROCESS: [{ type: 'realtime', name: '实时计算', status: 'active', stats: '空作业', load: 0 }],
  RAG_KNOWLEDGE: [
    { type: 'vector', name: '向量索引', status: 'active', stats: '0 文档', load: 0 },
    { type: 'offline', name: '清洗管道', status: 'active', stats: '默认流程', load: 0 }
  ],
  DATA_SERVICE: [{ type: 'serving', name: '数据 API', status: 'active', stats: '0 接口', load: 0 }]
}

const normalizeTemplateSelection = (templates: ProjectTemplateType[]) => {
  const set = new Set<ProjectTemplateType>(templates)
  return TEMPLATE_ORDER.filter((tpl) => set.has(tpl))
}

export function buildInitialModulesFromTemplates(templates: ProjectTemplateType[]): ProjectModule[] {
  const normalized = normalizeTemplateSelection(templates)
  return normalized.flatMap((tpl) => TEMPLATE_MODULES[tpl].map((mod) => ({ ...mod })))
}

