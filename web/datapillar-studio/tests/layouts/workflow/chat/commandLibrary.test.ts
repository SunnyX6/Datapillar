import { describe, expect, it } from 'vitest'
import { CHAT_COMMAND_OPTIONS } from '@/features/workflow/ui/chat/commandLibrary'

describe('command library options', () => {
  it('contains /clear with /compact', () => {
    const ids = CHAT_COMMAND_OPTIONS.map((item) => item.id)
    expect(ids).toContain('clear')
    expect(ids).toContain('compact')
  })

  it('command label to / Beginning', () => {
    CHAT_COMMAND_OPTIONS.forEach((item) => {
      expect(item.label.startsWith('/')).toBe(true)
    })
  })
})
