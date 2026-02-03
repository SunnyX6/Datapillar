import { describe, expect, it } from 'vitest'
import { buildTicketTitle, isTicketMentioned, normalizeTags } from '@/layouts/collaboration/utils'
import type { Ticket, UserProfile } from '@/layouts/collaboration/types'

describe('normalizeTags', () => {
  it('空字符串时返回空数组', () => {
    expect(normalizeTags('')).toEqual([])
  })

  it('去除空白并去重', () => {
    expect(normalizeTags(' 核心 , 风控,核心,  合规 ')).toEqual(['核心', '风控', '合规'])
  })

  it('支持中文逗号拆分', () => {
    expect(normalizeTags('数据，治理，权限')).toEqual(['数据', '治理', '权限'])
  })
})

describe('buildTicketTitle', () => {
  it('目标为空时仅返回类型标题', () => {
    expect(buildTicketTitle('数据权限', '')).toBe('数据权限')
  })

  it('目标存在时拼接标题', () => {
    expect(buildTicketTitle('数据权限', 'prod.fact_orders')).toBe('数据权限: prod.fact_orders')
  })
})

describe('isTicketMentioned', () => {
  const currentUser: UserProfile = { name: '我（管理员）', avatar: '我', role: '数据架构师' }

  const baseEvent = { id: 'e1', user: currentUser, action: 'comment', comment: 'hello', time: '刚刚' }

  const baseTicket: Ticket = {
    id: 'T-1',
    title: 'test',
    type: 'DATA_ACCESS',
    status: 'PENDING',
    createdAt: '刚刚',
    updatedAt: '刚刚',
    requester: currentUser,
    assignee: currentUser,
    details: { target: 'x', description: 'x', priority: 'LOW', tags: [] },
    timeline: [baseEvent]
  }

  it('命中 @我 时返回 true', () => {
    expect(
      isTicketMentioned(
        { ...baseTicket, timeline: [{ ...baseEvent, comment: '请 @我 看一下' }] },
        currentUser
      )
    ).toBe(true)
  })

  it('未命中提及时返回 false', () => {
    expect(isTicketMentioned(baseTicket, currentUser)).toBe(false)
  })
})
