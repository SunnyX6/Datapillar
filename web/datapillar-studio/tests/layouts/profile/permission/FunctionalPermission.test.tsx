// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { act } from 'react-dom/test-utils'
import { createRoot } from 'react-dom/client'
import { FunctionalPermission } from '@/layouts/profile/permission/FunctionalPermission'
import type { RoleDefinition } from '@/layouts/profile/permission/Permission'

const basePermissions: RoleDefinition['permissions'] = [
  {
    objectId: 101,
    objectName: '元数据目录',
    objectPath: '/governance/metadata',
    objectType: 'MENU',
    location: 'governance',
    categoryName: '数据资产',
    level: 'READ',
    tenantLevel: 'ADMIN',
  },
  {
    objectId: 202,
    objectName: '工作流编排',
    objectPath: '/workflow',
    objectType: 'MENU',
    location: 'workflow',
    categoryName: '开发与发布',
    level: 'DISABLE',
    tenantLevel: 'READ',
  },
]

const role: RoleDefinition = {
  id: 'role_dev',
  type: 'USER',
  name: '研发工程师',
  description: '用于测试',
  permissions: basePermissions,
}

const render = (ui: JSX.Element) => {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(ui)
  })
  return { container, root }
}

const unmount = (
  root: ReturnType<typeof createRoot>,
  container: HTMLDivElement,
) => {
  act(() => {
    root.unmount()
  })
  container.remove()
}

describe('FunctionalPermission', () => {
  it('角色模式渲染权限项', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <FunctionalPermission role={role} onUpdatePermission={onUpdate} />,
    )

    expect(container.textContent).toContain('元数据目录')
    expect(container.textContent).toContain('/governance/metadata')

    unmount(root, container)
  })

  it('点击权限按钮时回调角色权限更新', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <FunctionalPermission role={role} onUpdatePermission={onUpdate} />,
    )

    const firstPermissionDisableButton = container.querySelector('button')
    expect(firstPermissionDisableButton).not.toBeNull()
    act(() => {
      firstPermissionDisableButton?.dispatchEvent(
        new MouseEvent('click', { bubbles: true }),
      )
    })

    expect(onUpdate).toHaveBeenCalledWith(101, 'DISABLE')

    unmount(root, container)
  })
})
