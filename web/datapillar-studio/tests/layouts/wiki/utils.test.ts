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

describe('知识空间工具函数', () => {
  const spaces: KnowledgeSpace[] = [
    { id: 'ks1', name: '研发技术栈', description: '后端架构', docCount: 10, color: 'bg-indigo-500' },
    { id: 'ks2', name: '产品与设计', description: 'PRD', docCount: 5, color: 'bg-rose-500' }
  ]

  it('会对名称做去首尾空格与小写归一化', () => {
    expect(normalizeSpaceName('  FooBar  ')).toBe('foobar')
  })

  it('会校验名称唯一性并忽略首尾空格', () => {
    expect(isSpaceNameUnique(spaces, '  研发技术栈 ')).toBe(false)
    expect(isSpaceNameUnique(spaces, '企业行政')).toBe(true)
  })

  it('会生成不冲突的空间编号', () => {
    expect(getNextSpaceId(spaces)).toBe('ks3')
    const withGap: KnowledgeSpace[] = [...spaces, { id: 'ks4', name: '企业行政', description: '', docCount: 0, color: 'bg-emerald-500' }]
    expect(getNextSpaceId(withGap)).toBe('ks3')
  })

  it('会优先选择未使用的颜色并在用尽时轮询', () => {
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

  it('会格式化文件大小', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(500)).toBe('500 B')
    expect(formatBytes(1024)).toBe('1.00 KB')
    expect(formatBytes(1024 * 1024)).toBe('1.00 MB')
  })

  it('会规范化文档状态', () => {
    expect(normalizeDocumentStatus('indexed')).toBe('indexed')
    expect(normalizeDocumentStatus('processing')).toBe('processing')
    expect(normalizeDocumentStatus('error')).toBe('error')
    expect(normalizeDocumentStatus('unknown')).toBe('processing')
  })
})
