// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { act } from 'react-dom/test-utils'
import { createRoot } from 'react-dom/client'
import { FunctionalPermission } from '@/features/profile/ui/permission/FunctionalPermission'
import type { RoleDefinition } from '@/features/profile/utils/permissionTypes'

const basePermissions: RoleDefinition['permissions'] = [
  {
    objectId: 101,
    parentId: undefined,
    objectName: '元数据目录',
    objectPath: '/governance/metadata',
    objectType: 'MENU',
    location: 'governance',
    sort: 1,
    categoryName: '数据资产',
    level: 'READ',
    tenantLevel: 'ADMIN',
    children: [
      {
        objectId: 102,
        parentId: 101,
        objectName: '字段标准',
        objectPath: '/governance/metadata/fields',
        objectType: 'MENU',
        location: 'governance',
        sort: 1,
        categoryName: '数据资产',
        level: 'READ',
        tenantLevel: 'ADMIN',
        children: [],
      },
    ],
  },
  {
    objectId: 202,
    parentId: undefined,
    objectName: '工作流编排',
    objectPath: '/workflow',
    objectType: 'MENU',
    location: 'workflow',
    sort: 2,
    categoryName: '开发与发布',
    level: 'DISABLE',
    tenantLevel: 'READ',
    children: [],
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

    const buttons = Array.from(container.querySelectorAll('button'))
    const firstPermissionDisableButton = buttons.find(
      (button) => button.textContent?.trim() === '禁止',
    )
    expect(firstPermissionDisableButton).not.toBeNull()
    act(() => {
      firstPermissionDisableButton?.dispatchEvent(
        new MouseEvent('click', { bubbles: true }),
      )
    })

    expect(onUpdate).toHaveBeenCalledWith(101, 'DISABLE')

    unmount(root, container)
  })

  it('只读模式禁用权限修改', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <FunctionalPermission
        role={role}
        onUpdatePermission={onUpdate}
        readonly
      />,
    )

    const buttons = Array.from(container.querySelectorAll('button'))
    const firstPermissionButton = buttons.find(
      (button) => button.textContent?.trim() === '禁止',
    )
    expect(firstPermissionButton).not.toBeNull()
    expect(firstPermissionButton?.hasAttribute('disabled')).toBe(true)

    act(() => {
      firstPermissionButton?.dispatchEvent(
        new MouseEvent('click', { bubbles: true }),
      )
    })

    expect(onUpdate).not.toHaveBeenCalled()

    unmount(root, container)
  })

  it('父子节点展开收起与列对齐正确', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <FunctionalPermission role={role} onUpdatePermission={onUpdate} />,
    )

    const toggleButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="收起子节点"]',
    )
    expect(toggleButton).not.toBeNull()
    expect(container.textContent).toContain('字段标准')

    act(() => {
      toggleButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    expect(container.textContent).not.toContain('字段标准')

    const expandButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="展开子节点"]',
    )
    expect(expandButton).not.toBeNull()
    act(() => {
      expandButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    expect(container.textContent).toContain('字段标准')

    const parentRow = container.querySelector<HTMLElement>(
      '[data-testid="permission-row-101"]',
    )
    const childMain = container.querySelector<HTMLElement>(
      '[data-testid="permission-main-102"]',
    )
    const parentActions = container.querySelector<HTMLElement>(
      '[data-testid="permission-actions-101"]',
    )

    expect(parentRow).not.toBeNull()
    expect(parentRow?.style.paddingLeft ?? '').toBe('')
    expect(childMain).not.toBeNull()
    expect(childMain?.style.paddingLeft).toBe('20px')
    expect(parentActions).not.toBeNull()
    expect(parentActions?.style.paddingLeft ?? '').toBe('')

    unmount(root, container)
  })
})
