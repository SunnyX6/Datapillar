/**
 * 组件服务
 *
 * 后端已取消 /biz/components 接口，这里使用内置组件清单提供渲染元数据
 */

export interface JobComponent {
  id: number
  componentCode: string
  componentName: string
  componentType: 'SQL' | 'SCRIPT' | 'SYNC' | string
  jobParams: Record<string, unknown>
  description: string
  icon: string | null
  color: string | null
  sortOrder: number
}

const BUILTIN_COMPONENTS: JobComponent[] = [
  {
    id: 1,
    componentCode: 'SQL',
    componentName: 'SQL 任务',
    componentType: 'SQL',
    jobParams: {},
    description: '执行 SQL 脚本',
    icon: 'Database',
    color: '#3b82f6',
    sortOrder: 1
  },
  {
    id: 2,
    componentCode: 'PYTHON',
    componentName: 'Python 任务',
    componentType: 'SCRIPT',
    jobParams: {},
    description: '执行 Python 脚本',
    icon: 'Code2',
    color: '#10b981',
    sortOrder: 2
  },
  {
    id: 3,
    componentCode: 'SHELL',
    componentName: 'Shell 任务',
    componentType: 'SCRIPT',
    jobParams: {},
    description: '执行 Shell 命令',
    icon: 'Terminal',
    color: '#06b6d4',
    sortOrder: 3
  }
]

/**
 * 获取所有可用组件
 */
export function getAllComponents(): Promise<JobComponent[]> {
  return Promise.resolve([...BUILTIN_COMPONENTS])
}

/**
 * 根据 code 获取组件信息
 */
export function getComponentByCode(code: string): Promise<JobComponent> {
  const normalizedCode = code.trim().toUpperCase()
  const component = BUILTIN_COMPONENTS.find((item) => item.componentCode === normalizedCode)
  if (!component) {
    return Promise.reject(new Error(`未找到组件: ${code}`))
  }
  return Promise.resolve(component)
}

/**
 * 组件类型默认样式
 */
export const COMPONENT_TYPE_DEFAULTS: Record<string, { icon: string; color: string }> = {
  SQL: { icon: 'Database', color: '#3b82f6' },
  SCRIPT: { icon: 'Terminal', color: '#10b981' },
  SYNC: { icon: 'ArrowRightLeft', color: '#f59e0b' },
  PYTHON: { icon: 'Code2', color: '#10b981' },
  SHELL: { icon: 'Terminal', color: '#06b6d4' }
}

/**
 * 通用默认样式
 */
export const DEFAULT_COMPONENT_STYLE = { icon: 'Box', color: '#6b7280' }

/**
 * 获取组件样式（优先级：组件配置 > 类型默认 > 通用默认）
 */
export function getComponentStyle(component: JobComponent | undefined): { icon: string; color: string } {
  if (component?.icon && component?.color) {
    return { icon: component.icon, color: component.color }
  }

  if (component?.componentType && COMPONENT_TYPE_DEFAULTS[component.componentType]) {
    return COMPONENT_TYPE_DEFAULTS[component.componentType]
  }

  return DEFAULT_COMPONENT_STYLE
}
