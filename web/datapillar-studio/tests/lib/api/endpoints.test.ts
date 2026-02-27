import { describe, expect, it } from 'vitest'
import { API_BASE, API_PATH } from '@/api'

describe('API endpoints', () => {
  it('AI 工作流应使用 /api/ai/biz/etl 前缀', () => {
    expect(API_BASE.aiWorkflow).toBe('/api/ai/biz/etl')
    expect(API_PATH.workflow.chat).toBe('/chat')
    expect(API_PATH.workflow.sse).toBe('/sse')
    expect(API_PATH.workflow.abort).toBe('/abort')
  })

  it('项目接口应对齐 users/me 路径', () => {
    expect(API_PATH.project.list).toBe('/users/me/projects')
    expect(API_PATH.project.create).toBe('/users/me/projects')
    expect(API_PATH.project.detail(7)).toBe('/users/me/projects/7')
  })

  it('工作流接口应对齐新控制器路径', () => {
    expect(API_PATH.workflow.list).toBe('/workflows')
    expect(API_PATH.workflow.runs(9)).toBe('/workflows/9/runs')
    expect(API_PATH.workflow.dagVersions(9)).toBe('/workflows/9/dag/versions')
  })

  it('租户管理接口应对齐 current tenant 路径', () => {
    expect(API_PATH.tenantAdmin.users).toBe('/tenant/current/members')
    expect(API_PATH.tenantAdmin.roles).toBe('/tenant/current/roles')
    expect(API_PATH.tenantAdmin.role).toBe('/tenant/current/roles')
    expect(API_PATH.tenantAdmin.roleDetail(3)).toBe('/tenant/current/roles/3')
    expect(API_PATH.tenantAdmin.roleMembers(3)).toBe('/tenant/current/roles/3/members')
    expect(API_PATH.tenantAdmin.invitations).toBe('/tenant/current/invitations')
    expect(API_PATH.tenantAdmin.featureAudits).toBe('/tenant/current/features/audits')
    expect(API_PATH.tenantAdmin.ssoIdentities).toBe('/tenant/current/sso/identities')
  })
})
