export type FeatureNode = {
  id: string
  name: string
  description: string
  actions?: string[]
}

export type FeatureModule = FeatureNode & {
  children: FeatureNode[]
}

export const FEATURE_SCHEMA: FeatureModule[] = [
  {
    id: 'module_governance',
    name: '数据治理',
    description: '统一治理策略与数据资产入口。',
    children: [
      {
        id: 'feature_catalog',
        name: '数据目录',
        description: '统一的数据资产目录与标签体系。',
        actions: ['CATALOG:READ', 'CATALOG:MANAGE', 'CATALOG:TAG']
      },
      {
        id: 'feature_lineage',
        name: '血缘分析',
        description: '查看上下游血缘关系。',
        actions: ['LINEAGE:READ', 'LINEAGE:EXPORT']
      },
      {
        id: 'feature_quality',
        name: '质量规则',
        description: '配置与审计质量规则。',
        actions: ['QUALITY:READ', 'QUALITY:WRITE', 'QUALITY:ALERT']
      }
    ]
  },
  {
    id: 'module_build',
    name: '开发与发布',
    description: '研发工作流与发布流程。',
    children: [
      {
        id: 'feature_workflow',
        name: '工作流编排',
        description: '可视化编排数据任务。',
        actions: ['FLOW:READ', 'FLOW:DEPLOY', 'FLOW:EXECUTE']
      },
      {
        id: 'feature_ide',
        name: 'SQL IDE',
        description: '在线开发与调试。',
        actions: ['IDE:READ', 'IDE:RUN', 'IDE:SHARE']
      }
    ]
  },
  {
    id: 'module_ai',
    name: 'AI 能力',
    description: '模型与智能能力中心。',
    children: [
      {
        id: 'feature_assistant',
        name: 'AI 辅助修复',
        description: '智能修复与生成。',
        actions: ['AI:READ', 'AI:ASSIST', 'AI:CONFIG']
      },
      {
        id: 'feature_models',
        name: '模型管理',
        description: '模型配置与评测。',
        actions: ['MODEL:READ', 'MODEL:DEPLOY', 'MODEL:CHECK']
      }
    ]
  }
]
