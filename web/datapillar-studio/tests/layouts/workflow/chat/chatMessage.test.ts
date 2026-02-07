import { describe, expect, it } from 'vitest'
import type { AgentActivity } from '@/stores'
import { getProcessRowMessage, getProcessRowTitle, getProcessStatusLabel } from '@/layouts/workflow/chat/ChatMessage'

const buildActivity = (overrides: Partial<AgentActivity>): AgentActivity => ({
  id: 'analyst:llm',
  agent_cn: '需求分析师',
  agent_en: 'analyst',
  summary: '需要补充信息才能继续',
  event: 'interrupt',
  event_name: 'interrupt',
  status: 'waiting',
  timestamp: 1,
  ...overrides
})

describe('getProcessRowMessage', () => {
  it('仅返回 event_name，不展示 summary', () => {
    const activity = buildActivity({ summary: '不要展示' })
    expect(getProcessRowMessage(activity)).toBe('interrupt')
  })
})

describe('getProcessRowTitle', () => {
  it('只显示 agent，不拼接 event', () => {
    const activity = buildActivity({
      agent_cn: '元数据专员',
      event: 'tool',
      event_name: 'invoke list_catalogs'
    })
    expect(getProcessRowTitle(activity)).toBe('元数据专员')
  })
})

describe('getProcessStatusLabel', () => {
  it('仅使用外层 status', () => {
    expect(getProcessStatusLabel('running')).toBe('进行中')
  })

  it('缺失外层 status 时返回空字符串', () => {
    expect(getProcessStatusLabel(undefined)).toBe('')
  })
})
