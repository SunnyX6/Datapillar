import type { PermissionLevel } from './permissionConstants'

export const PERMISSION_RESOURCES: Array<{
  id: string
  name: string
  category: string
  description: string
}> = [
  { id: 'asset.catalog', name: '元数据目录', category: '数据资产', description: '浏览数据目录与资产信息' },
  { id: 'asset.lineage', name: '数据血缘', category: '数据资产', description: '查看上下游血缘关系' },
  { id: 'asset.quality', name: '质量规则', category: '数据资产', description: '管理质量规则与校验' },
  { id: 'asset.security', name: '敏感字段策略', category: '数据资产', description: '配置敏感字段与访问策略' },
  { id: 'build.workflow', name: '工作流编排', category: '开发与发布', description: '编排与调度数据工作流' },
  { id: 'build.ide', name: 'SQL IDE', category: '开发与发布', description: '在线 SQL 开发与调试' },
  { id: 'build.release', name: '发布审批', category: '开发与发布', description: '提交与审批发布流程' },
  { id: 'ai.assistant', name: 'AI 辅助修复', category: 'AI 能力', description: '使用 AI 辅助修复与生成' },
  { id: 'ai.models', name: '模型管理', category: 'AI 能力', description: '查看与管理模型资源' },
  { id: 'ai.cost', name: '成本卫士', category: 'AI 能力', description: '成本分析与预算控制' }
]

export const buildPermissions = (defaultLevel: PermissionLevel, overrides: Record<string, PermissionLevel> = {}) =>
  PERMISSION_RESOURCES.map((resource) => ({
    ...resource,
    level: overrides[resource.id] ?? defaultLevel
  }))
