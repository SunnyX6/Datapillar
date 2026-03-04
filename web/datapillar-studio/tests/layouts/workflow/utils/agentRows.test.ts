import { describe, expect, it } from 'vitest'
import type { AgentActivity } from '@/features/workflow/state'
import { upsertAgentActivityByAgent } from '@/features/workflow/utils/agentRows'

const buildActivity = (overrides: Partial<AgentActivity>): AgentActivity => ({
  id: 'analyst:llm',
  agent_cn: 'Demand Analyst',
  agent_en: 'analyst',
  summary: 'In progress',
  event: 'llm',
  event_name: 'llm',
  status: 'running',
  timestamp: 1,
  ...overrides
})

describe('upsertAgentActivityByAgent', () => {
  it('New agent Append rows', () => {
    const rows: AgentActivity[] = [buildActivity({ id: 'analyst:llm', agent_en: 'analyst' })]
    const next = buildActivity({
      id: 'architect:llm',
      agent_cn: 'Architect',
      agent_en: 'architect',
      summary: 'Start designing'
    })
    const result = upsertAgentActivityByAgent(rows, next, 200)
    expect(result).toHaveLength(2)
    expect(result[1].id).toBe('architect:llm')
    expect(result[1].summary).toBe('Start designing')
  })

  it('same agent when overwriting rows and keeping id', () => {
    const rows: AgentActivity[] = [
      buildActivity({
        id: 'analyst:llm',
        agent_en: 'analyst',
        summary: 'old content',
        timestamp: 1
      }),
      buildActivity({
        id: 'architect:llm',
        agent_cn: 'Architect',
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
      summary: 'new content',
      timestamp: 10,
      status: 'done'
    })
    const result = upsertAgentActivityByAgent(rows, next, 200)

    expect(result).toHaveLength(2)
    expect(result[0].id).toBe('analyst:llm')
    expect(result[0].summary).toBe('new content')
    expect(result[0].status).toBe('done')
    expect(result[1]).toEqual(rows[1])
  })

  it('Observe the maximum number of rows when appending', () => {
    const rows: AgentActivity[] = [
      buildActivity({ id: 'analyst:llm', agent_en: 'analyst' }),
      buildActivity({ id: 'catalog:llm', agent_cn: 'Metadata retrieval', agent_en: 'catalog' })
    ]
    const next = buildActivity({ id: 'architect:llm', agent_cn: 'Architect', agent_en: 'architect' })
    const result = upsertAgentActivityByAgent(rows, next, 2)
    expect(result).toHaveLength(2)
    expect(result[0].id).toBe('catalog:llm')
    expect(result[1].id).toBe('architect:llm')
  })
})
