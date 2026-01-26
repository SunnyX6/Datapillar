import { describe, expect, it } from 'vitest'
import type { AgentActivity } from '@/stores'
import { upsertAgentActivityByAgent } from '@/layouts/workflow/utils/agentRows'

const buildActivity = (overrides: Partial<AgentActivity>): AgentActivity => ({
  id: 'phase:analysis',
  phase: 'analysis',
  status: 'running',
  actor: '需求分析师',
  title: '需求分析',
  detail: '进行中',
  timestamp: 1,
  ...overrides
})

describe('upsertAgentActivityByAgent', () => {
  it('新增活动 id 时追加行', () => {
    const rows: AgentActivity[] = [buildActivity({ id: 'phase:analysis', title: '需求分析' })]
    const next = buildActivity({ id: 'phase:design', phase: 'design', title: '架构设计', detail: '开始设计' })
    const result = upsertAgentActivityByAgent(rows, next, 200)
    expect(result).toHaveLength(2)
    expect(result[1].id).toBe('phase:design')
    expect(result[1].detail).toBe('开始设计')
  })

  it('同一活动 id 时覆盖行且保持 id', () => {
    const rows: AgentActivity[] = [
      buildActivity({ id: 'phase:analysis', title: '需求分析', detail: '旧内容', timestamp: 1 }),
      buildActivity({ id: 'phase:design', phase: 'design', title: '架构设计', detail: 'b1', timestamp: 2 })
    ]

    const next = buildActivity({ id: 'phase:analysis', detail: '新内容', timestamp: 10, status: 'done' })
    const result = upsertAgentActivityByAgent(rows, next, 200)

    expect(result).toHaveLength(2)
    expect(result[0].id).toBe('phase:analysis')
    expect(result[0].detail).toBe('新内容')
    expect(result[0].status).toBe('done')
    expect(result[1]).toEqual(rows[1])
  })

  it('追加时遵守最大行数限制', () => {
    const rows: AgentActivity[] = [
      buildActivity({ id: 'phase:analysis', title: '需求分析' }),
      buildActivity({ id: 'phase:catalog', phase: 'catalog', title: '元数据检索' })
    ]
    const next = buildActivity({ id: 'phase:design', phase: 'design', title: '架构设计' })
    const result = upsertAgentActivityByAgent(rows, next, 2)
    expect(result).toHaveLength(2)
    expect(result[0].id).toBe('phase:catalog')
    expect(result[1].id).toBe('phase:design')
  })
})
