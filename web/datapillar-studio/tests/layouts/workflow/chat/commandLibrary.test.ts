import { describe, expect, it } from 'vitest'
import { CHAT_COMMAND_OPTIONS } from '@/features/workflow/ui/chat/commandLibrary'

describe('command library options', () => {
  it('包含 /clear 与 /compact', () => {
    const ids = CHAT_COMMAND_OPTIONS.map((item) => item.id)
    expect(ids).toContain('clear')
    expect(ids).toContain('compact')
  })

  it('命令 label 以 / 开头', () => {
    CHAT_COMMAND_OPTIONS.forEach((item) => {
      expect(item.label.startsWith('/')).toBe(true)
    })
  })
})
