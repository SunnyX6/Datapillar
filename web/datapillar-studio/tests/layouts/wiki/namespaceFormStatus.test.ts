import { describe, expect, it } from 'vitest'
import type { KnowledgeSpace } from '@/features/wiki/utils/types'
import { getNamespaceFormStatus } from '@/features/wiki/utils'

describe('Knowledge space creation form status', () => {
  const spaces: KnowledgeSpace[] = [
    { id: 'ks1', name: 'R&D technology stack', description: 'Backend architecture', docCount: 10, color: 'bg-indigo-500' },
    { id: 'ks2', name: 'Products and Design', description: 'PRD', docCount: 5, color: 'bg-rose-500' }
  ]

  it('Creation is not allowed with empty name', () => {
    const status = getNamespaceFormStatus(spaces, '   ')
    expect(status.trimmedName).toBe('')
    expect(status.isNameUnique).toBe(true)
    expect(status.showNameError).toBe(false)
    expect(status.canCreateSpace).toBe(false)
  })

  it('Duplicate names will prompt an error', () => {
    const status = getNamespaceFormStatus(spaces, '  R&D technology stack ')
    expect(status.trimmedName).toBe('R&D technology stack')
    expect(status.isNameUnique).toBe(false)
    expect(status.showNameError).toBe(true)
    expect(status.canCreateSpace).toBe(false)
  })

  it('Unique names can be created', () => {
    const status = getNamespaceFormStatus(spaces, 'Corporate Administration')
    expect(status.trimmedName).toBe('Corporate Administration')
    expect(status.isNameUnique).toBe(true)
    expect(status.showNameError).toBe(false)
    expect(status.canCreateSpace).toBe(true)
  })
})
