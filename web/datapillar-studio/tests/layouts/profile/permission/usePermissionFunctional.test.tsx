// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePermissionFunctional } from '@/features/profile/hooks/usePermissionFunctional'
import { usePermissionCacheStore } from '@/features/profile/state'
import type { PermissionTab } from '@/features/profile/utils/permissionTypes'
import { getTenantRolePermissions } from '@/services/studioTenantRoleService'

vi.mock('@/services/studioTenantRoleService', () => ({
  getTenantRolePermissions: vi.fn(),
}))

const reactActEnvironment = globalThis as typeof globalThis & {
  IS_REACT_ACT_ENVIRONMENT?: boolean
}

function HookHarness(props: {
  tenantId?: number
  roleId: string
  activeTab: PermissionTab
}) {
  usePermissionFunctional(props)
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

describe('usePermissionFunctional', () => {
  const getTenantRolePermissionsMock = vi.mocked(getTenantRolePermissions)

  beforeEach(() => {
    reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true
    vi.clearAllMocks()
    usePermissionCacheStore.getState().reset()
    getTenantRolePermissionsMock.mockImplementation(async (_tenantId, roleId) => [
      {
        objectId: 100 + roleId,
        parentId: null,
        objectName: `menu-${roleId}`,
        objectPath: `/menu-${roleId}`,
        objectType: 'MENU',
        location: 'sidebar',
        categoryId: 1,
        categoryName: '数据资产',
        sort: 1,
        permissionCode: 'READ',
        tenantPermissionCode: 'ADMIN',
        children: [],
      },
    ])
  })

  it('仅在功能权限 tab 激活时请求权限接口', async () => {
    const harness = createHarness()

    harness.render({ tenantId: 1, roleId: '1', activeTab: 'members' })
    await flushAsyncEffects()

    harness.render({ tenantId: 1, roleId: '2', activeTab: 'members' })
    await flushAsyncEffects()

    expect(getTenantRolePermissionsMock).toHaveBeenCalledTimes(0)

    harness.render({ tenantId: 1, roleId: '2', activeTab: 'functional' })
    await flushAsyncEffects()

    expect(getTenantRolePermissionsMock).toHaveBeenCalledTimes(1)
    expect(getTenantRolePermissionsMock).toHaveBeenLastCalledWith(1, 2, 'ALL')

    harness.unmount()
  })

  it('同角色命中权限缓存时不重复请求，切新角色才请求', async () => {
    const harness = createHarness()

    harness.render({ tenantId: 1, roleId: '1', activeTab: 'functional' })
    await flushAsyncEffects()
    expect(getTenantRolePermissionsMock).toHaveBeenCalledTimes(1)

    harness.render({ tenantId: 1, roleId: '2', activeTab: 'functional' })
    await flushAsyncEffects()
    expect(getTenantRolePermissionsMock).toHaveBeenCalledTimes(2)

    harness.render({ tenantId: 1, roleId: '1', activeTab: 'functional' })
    await flushAsyncEffects()
    expect(getTenantRolePermissionsMock).toHaveBeenCalledTimes(2)

    harness.unmount()
  })
})
