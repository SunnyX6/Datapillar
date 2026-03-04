import { describe, expect, it } from 'vitest'
import type { KnowledgeSpace } from '@/features/wiki/utils/types'
import {
  formatBytes,
  getNextSpaceColor,
  getNextSpaceId,
  isSpaceNameUnique,
  normalizeSpaceName,
  normalizeDocumentStatus,
  SPACE_COLOR_PALETTE
} from '@/features/wiki/utils'

describe('Knowledge space tool function', () => {
  const spaces: KnowledgeSpace[] = [
    { id: 'ks1', name: 'R&D technology stack', description: 'Backend architecture', docCount: 10, color: 'bg-indigo-500' },
    { id: 'ks2', name: 'Products and Design', description: 'PRD', docCount: 5, color: 'bg-rose-500' }
  ]

  it('The name will be normalized by removing leading and trailing spaces and lowercase letters.', () => {
    expect(normalizeSpaceName('  FooBar  ')).toBe('foobar')
  })

  it('Will verify name uniqueness and ignore leading and trailing spaces', () => {
    expect(isSpaceNameUnique(spaces, '  R&D technology stack ')).toBe(false)
    expect(isSpaceNameUnique(spaces, 'Corporate Administration')).toBe(true)
  })

  it('Will generate non-conflicting space numbers', () => {
    expect(getNextSpaceId(spaces)).toBe('ks3')
    const withGap: KnowledgeSpace[] = [...spaces, { id: 'ks4', name: 'Corporate Administration', description: '', docCount: 0, color: 'bg-emerald-500' }]
    expect(getNextSpaceId(withGap)).toBe('ks3')
  })

  it('Will prioritize unused colors and poll when exhausted', () => {
    const nextColor = getNextSpaceColor(spaces, SPACE_COLOR_PALETTE)
    expect(SPACE_COLOR_PALETTE).toContain(nextColor)
    const fullPaletteSpaces = SPACE_COLOR_PALETTE.map((color, index) => ({
      id: `ks${index + 1}`,
      name: `space-${index + 1}`,
      description: '',
      docCount: 0,
      color
    }))
    const cycledColor = getNextSpaceColor(fullPaletteSpaces, SPACE_COLOR_PALETTE)
    expect(SPACE_COLOR_PALETTE).toContain(cycledColor)
  })

  it('Will format the file size', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(500)).toBe('500 B')
    expect(formatBytes(1024)).toBe('1.00 KB')
    expect(formatBytes(1024 * 1024)).toBe('1.00 MB')
  })

  it('Will normalize document status', () => {
    expect(normalizeDocumentStatus('indexed')).toBe('indexed')
    expect(normalizeDocumentStatus('processing')).toBe('processing')
    expect(normalizeDocumentStatus('error')).toBe('error')
    expect(normalizeDocumentStatus('unknown')).toBe('processing')
  })
})
