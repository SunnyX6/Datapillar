import { describe, expect, it } from 'vitest'
import type { AgentActivity } from '@/features/workflow/state'
import { getProcessRowMessage, getProcessRowTitle, getProcessStatusLabel } from '@/features/workflow/ui/chat/chatMessageUtils'

const buildActivity = (overrides: Partial<AgentActivity>): AgentActivity => ({
  id: 'analyst:llm',
  agent_cn: 'Demand Analyst',
  agent_en: 'analyst',
  summary: 'Additional information is needed to continue',
  event: 'interrupt',
  event_name: 'interrupt',
  status: 'waiting',
  timestamp: 1,
  ...overrides
})

describe('getProcessRowMessage', () => {
  it('Return only event_name，Do not display summary', () => {
    const activity = buildActivity({ summary: 'dont show' })
    expect(getProcessRowMessage(activity)).toBe('interrupt')
  })
})

describe('getProcessRowTitle', () => {
  it('Show only agent，No splicing event', () => {
    const activity = buildActivity({
      agent_cn: 'Metadata Specialist',
      event: 'tool',
      event_name: 'invoke list_catalogs'
    })
    expect(getProcessRowTitle(activity)).toBe('Metadata Specialist')
  })
})

describe('getProcessStatusLabel', () => {
  it('Use outer layer only status', () => {
    expect(getProcessStatusLabel('running')).toBe('In progress')
  })

  it('missing outer layer status Returns an empty string', () => {
    expect(getProcessStatusLabel(undefined)).toBe('')
  })
})
