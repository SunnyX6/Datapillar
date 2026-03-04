// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePermissionMembers } from '@/features/profile/hooks/usePermissionMembers'
import { usePermissionCacheStore } from '@/features/profile/state'
import type { PermissionTab } from '@/features/profile/utils/permissionTypes'
import { getTenantRoleMembers } from '@/services/studioTenantRoleService'

vi.mock('@/services/studioTenantRoleService', () => ({
  getTenantRoleMembers: vi.fn(),
}))

const reactActEnvironment = globalThis as typeof globalThis & {
  IS_REACT_ACT_ENVIRONMENT?: boolean
}

function HookHarness(props: {
  tenantId?: number
  roleId: string
  activeTab: PermissionTab
}) {
  usePermissionMembers(props)
  return null
}

function createHarness() {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)

  const render = (props: { tenantId?: number; roleId: string; activeTab: PermissionTab }) => {
    act(() => {
      root.render(<HookHarness {...props} />)
    })
  }

  const unmount = () => {
    act(() => {
      root.unmount()
    })
    container.remove()
  }

  return { render, unmount }
}

async function flushAsyncEffects() {
  await act(async () => {
    await Promise.resolve()
  })
  await act(async () => {
    await Promise.resolve()
  })
}

describe('usePermissionMembers', () => {
  const getTenantRoleMembersMock = vi.mocked(getTenantRoleMembers)

  beforeEach(() => {
    reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true
    vi.clearAllMocks()
    usePermissionCacheStore.getState().reset()
    getTenantRoleMembersMock.mockImplementation(async (_tenantId, roleId) => ({
      roleId,
      roleName: `role-${roleId}`,
      roleType: 'USER',
      roleLevel: 100,
      roleStatus: 1,
      memberCount: 1,
      members: [
        {
          userId: 1000 + roleId,
          username: `user-${roleId}`,
          nickname: null,
          email: `user-${roleId}@datapillar.ai`,
          phone: null,
          memberStatus: 1,
          joinedAt: '2026-02-25T10:00:00',
          assignedAt: '2026-02-25T10:00:00',
        },
      ],
    }))
  })

  it('only in members tab Request member interface on activation', async () => {
    const harness = createHarness()

    harness.render({ tenantId: 1, roleId: '1', activeTab: 'functional' })
    await flushAsyncEffects()

    harness.render({ tenantId: 1, roleId: '2', activeTab: 'functional' })
    await flushAsyncEffects()

    expect(getTenantRoleMembersMock).toHaveBeenCalledTimes(0)

    harness.render({ tenantId: 1, roleId: '2', activeTab: 'members' })
    await flushAsyncEffects()

    expect(getTenantRoleMembersMock).toHaveBeenCalledTimes(1)
    expect(getTenantRoleMembersMock).toHaveBeenLastCalledWith(1, 2)

    harness.unmount()
  })

  it('No repeated requests when the same role hits the cache，Request only after switching to new role', async () => {
    const harness = createHarness()

    harness.render({ tenantId: 1, roleId: '1', activeTab: 'members' })
    await flushAsyncEffects()
    expect(getTenantRoleMembersMock).toHaveBeenCalledTimes(1)

    harness.render({ tenantId: 1, roleId: '2', activeTab: 'members' })
    await flushAsyncEffects()
    expect(getTenantRoleMembersMock).toHaveBeenCalledTimes(2)

    harness.render({ tenantId: 1, roleId: '1', activeTab: 'members' })
    await flushAsyncEffects()
    expect(getTenantRoleMembersMock).toHaveBeenCalledTimes(2)

    harness.unmount()
  })
})
