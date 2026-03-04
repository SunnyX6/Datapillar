import { describe, expect, it } from 'vitest'
import { buildTicketTitle, isTicketMentioned, normalizeTags } from '@/features/collaboration/utils'
import type { Ticket, UserProfile } from '@/features/collaboration/utils/types'

describe('normalizeTags', () => {
  it('Returns an empty array when the string is empty', () => {
    expect(normalizeTags('')).toEqual([])
  })

  it('Remove whitespace and deduplicate', () => {
    expect(normalizeTags(' core , Risk control,core,  Compliance ')).toEqual(['core', 'Risk control', 'Compliance'])
  })

  it('Support Chinese comma split', () => {
    expect(normalizeTags('data，governance，Permissions')).toEqual(['data', 'governance', 'Permissions'])
  })
})

describe('buildTicketTitle', () => {
  it('Only the type header is returned when the target is empty', () => {
    expect(buildTicketTitle('Data permissions', '')).toBe('Data permissions')
  })

  it('Splice title when target exists', () => {
    expect(buildTicketTitle('Data permissions', 'prod.fact_orders')).toBe('Data permissions: prod.fact_orders')
  })
})

describe('isTicketMentioned', () => {
  const currentUser: UserProfile = { name: 'me（Administrator）', avatar: 'me', role: 'data architect' }

  const baseEvent = { id: 'e1', user: currentUser, action: 'comment', comment: 'hello', time: 'just now' }

  const baseTicket: Ticket = {
    id: 'T-1',
    title: 'test',
    type: 'DATA_ACCESS',
    status: 'PENDING',
    createdAt: 'just now',
    updatedAt: 'just now',
    requester: currentUser,
    assignee: currentUser,
    details: { target: 'x', description: 'x', priority: 'LOW', tags: [] },
    timeline: [baseEvent]
  }

  it('hit @me return when true', () => {
    expect(
      isTicketMentioned(
        { ...baseTicket, timeline: [{ ...baseEvent, comment: 'please @me Take a look' }] },
        currentUser
      )
    ).toBe(true)
  })

  it('Returns when mention is not hit false', () => {
    expect(isTicketMentioned(baseTicket, currentUser)).toBe(false)
  })
})
