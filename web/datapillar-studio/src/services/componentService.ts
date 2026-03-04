/**
 * Component services
 *
 * Backend canceled /biz/components interface，This uses the built-in component manifest to provide rendering metadata
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
    componentName: 'SQL Task',
    componentType: 'SQL',
    jobParams: {},
    description: 'execute SQL script',
    icon: 'Database',
    color: '#3b82f6',
    sortOrder: 1
  },
  {
    id: 2,
    componentCode: 'PYTHON',
    componentName: 'Python Task',
    componentType: 'SCRIPT',
    jobParams: {},
    description: 'execute Python script',
    icon: 'Code2',
    color: '#10b981',
    sortOrder: 2
  },
  {
    id: 3,
    componentCode: 'SHELL',
    componentName: 'Shell Task',
    componentType: 'SCRIPT',
    jobParams: {},
    description: 'execute Shell command',
    icon: 'Terminal',
    color: '#06b6d4',
    sortOrder: 3
  }
]

/**
 * Get all available components
 */
export function getAllComponents(): Promise<JobComponent[]> {
  return Promise.resolve([...BUILTIN_COMPONENTS])
}

/**
 * According to code Get component information
 */
export function getComponentByCode(code: string): Promise<JobComponent> {
  const normalizedCode = code.trim().toUpperCase()
  const component = BUILTIN_COMPONENTS.find((item) => item.componentCode === normalizedCode)
  if (!component) {
    return Promise.reject(new Error(`Component not found: ${code}`))
  }
  return Promise.resolve(component)
}

/**
 * Component type default style
 */
export const COMPONENT_TYPE_DEFAULTS: Record<string, { icon: string; color: string }> = {
  SQL: { icon: 'Database', color: '#3b82f6' },
  SCRIPT: { icon: 'Terminal', color: '#10b981' },
  SYNC: { icon: 'ArrowRightLeft', color: '#f59e0b' },
  PYTHON: { icon: 'Code2', color: '#10b981' },
  SHELL: { icon: 'Terminal', color: '#06b6d4' }
}

/**
 * Universal default style
 */
export const DEFAULT_COMPONENT_STYLE = { icon: 'Box', color: '#6b7280' }

/**
 * Get component style（priority：Component configuration > typedefault > Universal default）
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
