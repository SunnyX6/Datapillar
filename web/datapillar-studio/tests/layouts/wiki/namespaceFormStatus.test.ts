import { describe, expect, it } from 'vitest'
import type { KnowledgeSpace } from '@/features/wiki/utils/types'
import { getNamespaceFormStatus } from '@/features/wiki/utils'

describe('知识空间创建表单状态', () => {
  const spaces: KnowledgeSpace[] = [
    { id: 'ks1', name: '研发技术栈', description: '后端架构', docCount: 10, color: 'bg-indigo-500' },
    { id: 'ks2', name: '产品与设计', description: 'PRD', docCount: 5, color: 'bg-rose-500' }
  ]

  it('空名称时不允许创建', () => {
    const status = getNamespaceFormStatus(spaces, '   ')
    expect(status.trimmedName).toBe('')
    expect(status.isNameUnique).toBe(true)
    expect(status.showNameError).toBe(false)
    expect(status.canCreateSpace).toBe(false)
  })

  it('重复名称会提示错误', () => {
    const status = getNamespaceFormStatus(spaces, '  研发技术栈 ')
    expect(status.trimmedName).toBe('研发技术栈')
    expect(status.isNameUnique).toBe(false)
    expect(status.showNameError).toBe(true)
    expect(status.canCreateSpace).toBe(false)
  })

  it('唯一名称可以创建', () => {
    const status = getNamespaceFormStatus(spaces, '企业行政')
    expect(status.trimmedName).toBe('企业行政')
    expect(status.isNameUnique).toBe(true)
    expect(status.showNameError).toBe(false)
    expect(status.canCreateSpace).toBe(true)
  })
})

