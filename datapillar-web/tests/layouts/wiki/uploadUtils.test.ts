import { describe, expect, it } from 'vitest'
import { getFileNameFromUrl, inferDocumentTypeFromName } from '@/layouts/wiki/utils'

describe('文档上传工具函数', () => {
  it('可以从文件名推断文档类型', () => {
    expect(inferDocumentTypeFromName('report.pdf')).toBe('pdf')
    expect(inferDocumentTypeFromName('spec.DOCX')).toBe('docx')
    expect(inferDocumentTypeFromName('note.md')).toBe('md')
    expect(inferDocumentTypeFromName('readme.txt')).toBe('txt')
    expect(inferDocumentTypeFromName('unknown.bin')).toBe('txt')
  })

  it('可以从 URL 中解析文件名', () => {
    expect(getFileNameFromUrl('https://wiki.corp.internal/doc/guide.pdf')).toBe('guide.pdf')
    expect(getFileNameFromUrl('https://wiki.corp.internal/doc/')).toBe('wiki.corp.internal')
  })

  it('对非法 URL 返回原始字符串', () => {
    expect(getFileNameFromUrl('  not-a-url  ')).toBe('not-a-url')
  })
})
