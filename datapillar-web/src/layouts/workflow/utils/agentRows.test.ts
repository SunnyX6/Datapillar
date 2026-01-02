import { describe, expect, it } from 'vitest'
import type { AgentActivity } from '@/stores'
import { upsertAgentActivityByAgent } from './agentRows'

const buildActivity = (overrides: Partial<AgentActivity>): AgentActivity => ({
  id: 'row-1',
  type: 'thought',
  state: 'thinking',
  level: 'info',
  agent: 'agent-a',
  message: 'm1',
  timestamp: 1,
  ...overrides
})

describe('upsertAgentActivityByAgent', () => {
  it('appends a new agent row when agent does not exist', () => {
    const rows: AgentActivity[] = [buildActivity({ id: 'row-a', agent: 'agent-a' })]
    const next = buildActivity({ id: 'row-b', agent: 'agent-b', message: 'b1' })
    const result = upsertAgentActivityByAgent(rows, next, 200)
    expect(result).toHaveLength(2)
    expect(result[1].agent).toBe('agent-b')
    expect(result[1].message).toBe('b1')
  })

  it('replaces the existing row for the same agent (keeps stable id)', () => {
    const rows: AgentActivity[] = [
      buildActivity({ id: 'row-a', agent: 'agent-a', message: 'old', timestamp: 1 }),
      buildActivity({ id: 'row-b', agent: 'agent-b', message: 'b1', timestamp: 2 })
    ]

    const next = buildActivity({ id: 'row-a-new', agent: 'agent-a', message: 'new', timestamp: 10, state: 'done' })
    const result = upsertAgentActivityByAgent(rows, next, 200)

    expect(result).toHaveLength(2)
    expect(result[0].agent).toBe('agent-a')
    expect(result[0].message).toBe('new')
    expect(result[0].state).toBe('done')
    expect(result[0].id).toBe('row-a')
    expect(result[1]).toEqual(rows[1])
  })

  it('enforces maxRows on append', () => {
    const rows: AgentActivity[] = [
      buildActivity({ id: 'row-a', agent: 'agent-a' }),
      buildActivity({ id: 'row-b', agent: 'agent-b' })
    ]
    const next = buildActivity({ id: 'row-c', agent: 'agent-c' })
    const result = upsertAgentActivityByAgent(rows, next, 2)
    expect(result).toHaveLength(2)
    expect(result[0].agent).toBe('agent-b')
    expect(result[1].agent).toBe('agent-c')
  })
})

