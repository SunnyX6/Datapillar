import { describe, expect, it } from 'vitest'
import type { AgentActivity } from '@/stores'
import { upsertAgentActivityByAgent } from '@/layouts/workflow/utils/agentRows'

const buildActivity = (overrides: Partial<AgentActivity>): AgentActivity => ({
  id: 'analyst:llm',
  agent_cn: '需求分析师',
  agent_en: 'analyst',
  summary: '进行中',
  event: 'llm',
  event_name: 'llm',
  status: 'running',
  timestamp: 1,
  ...overrides
})

describe('upsertAgentActivityByAgent', () => {
  it('新增 agent 时追加行', () => {
    const rows: AgentActivity[] = [buildActivity({ id: 'analyst:llm', agent_en: 'analyst' })]
    const next = buildActivity({
      id: 'architect:llm',
      agent_cn: '架构师',
      agent_en: 'architect',
      summary: '开始设计'
    })
    const result = upsertAgentActivityByAgent(rows, next, 200)
    expect(result).toHaveLength(2)
    expect(result[1].id).toBe('architect:llm')
    expect(result[1].summary).toBe('开始设计')
  })

  it('同一 agent 时覆盖行且保持 id', () => {
    const rows: AgentActivity[] = [
      buildActivity({
        id: 'analyst:llm',
        agent_en: 'analyst',
        summary: '旧内容',
        timestamp: 1
      }),
      buildActivity({
        id: 'architect:llm',
        agent_cn: '架构师',
        agent_en: 'architect',
        summary: 'b1',
        timestamp: 2
      })
    ]

    const next = buildActivity({
      id: 'analyst:tool',
      agent_en: 'analyst',
      event: 'tool',
      event_name: 'get_knowledge_nav',
      summary: '新内容',
      timestamp: 10,
      status: 'done'
    })
    const result = upsertAgentActivityByAgent(rows, next, 200)

    expect(result).toHaveLength(2)
    expect(result[0].id).toBe('analyst:llm')
    expect(result[0].summary).toBe('新内容')
    expect(result[0].status).toBe('done')
    expect(result[1]).toEqual(rows[1])
  })

  it('追加时遵守最大行数限制', () => {
    const rows: AgentActivity[] = [
      buildActivity({ id: 'analyst:llm', agent_en: 'analyst' }),
      buildActivity({ id: 'catalog:llm', agent_cn: '元数据检索', agent_en: 'catalog' })
    ]
    const next = buildActivity({ id: 'architect:llm', agent_cn: '架构师', agent_en: 'architect' })
    const result = upsertAgentActivityByAgent(rows, next, 2)
    expect(result).toHaveLength(2)
    expect(result[0].id).toBe('catalog:llm')
    expect(result[1].id).toBe('architect:llm')
  })
})
