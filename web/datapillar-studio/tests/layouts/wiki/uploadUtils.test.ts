import { describe, expect, it } from 'vitest'
import { getFileNameFromUrl, inferDocumentTypeFromName } from '@/features/wiki/utils'

describe('Document upload tool function', () => {
  it('Document type can be inferred from file name', () => {
    expect(inferDocumentTypeFromName('report.pdf')).toBe('pdf')
    expect(inferDocumentTypeFromName('spec.DOCX')).toBe('docx')
    expect(inferDocumentTypeFromName('note.md')).toBe('md')
    expect(inferDocumentTypeFromName('readme.txt')).toBe('txt')
    expect(inferDocumentTypeFromName('unknown.bin')).toBe('txt')
  })

  it('Can be obtained from URL Parse file name in', () => {
    expect(getFileNameFromUrl('https://wiki.corp.internal/doc/guide.pdf')).toBe('guide.pdf')
    expect(getFileNameFromUrl('https://wiki.corp.internal/doc/')).toBe('wiki.corp.internal')
  })

  it('to illegal URL Return original string', () => {
    expect(getFileNameFromUrl('  not-a-url  ')).toBe('not-a-url')
  })
})
