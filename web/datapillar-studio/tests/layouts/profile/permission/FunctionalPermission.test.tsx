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
    objectName: 'metadata directory',
    objectPath: '/governance/metadata',
    objectType: 'MENU',
    location: 'governance',
    sort: 1,
    categoryName: 'data assets',
    level: 'READ',
    tenantLevel: 'ADMIN',
    children: [
      {
        objectId: 102,
        parentId: 101,
        objectName: 'Field standards',
        objectPath: '/governance/metadata/fields',
        objectType: 'MENU',
        location: 'governance',
        sort: 1,
        categoryName: 'data assets',
        level: 'READ',
        tenantLevel: 'ADMIN',
        children: [],
      },
    ],
  },
  {
    objectId: 202,
    parentId: undefined,
    objectName: 'Workflow orchestration',
    objectPath: '/workflow',
    objectType: 'MENU',
    location: 'workflow',
    sort: 2,
    categoryName: 'Development and release',
    level: 'DISABLE',
    tenantLevel: 'READ',
    children: [],
  },
]

const role: RoleDefinition = {
  id: 'role_dev',
  type: 'USER',
  name: 'R&D Engineer',
  description: 'for testing',
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
  it('Role mode rendering permission items', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <FunctionalPermission role={role} onUpdatePermission={onUpdate} />,
    )

    expect(container.textContent).toContain('metadata directory')
    expect(container.textContent).toContain('/governance/metadata')

    unmount(root, container)
  })

  it('Callback role permission update when the permission button is clicked', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <FunctionalPermission role={role} onUpdatePermission={onUpdate} />,
    )

    const buttons = Array.from(container.querySelectorAll('button'))
    const firstPermissionDisableButton = buttons.find(
      (button) => button.textContent?.trim() === 'prohibited',
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

  it('Read-only mode disables permission modification', () => {
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
      (button) => button.textContent?.trim() === 'prohibited',
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

  it('The parent and child nodes are expanded and collapsed and the columns are aligned correctly.', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <FunctionalPermission role={role} onUpdatePermission={onUpdate} />,
    )

    const toggleButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Collapse child nodes"]',
    )
    expect(toggleButton).not.toBeNull()
    expect(container.textContent).toContain('Field standards')

    act(() => {
      toggleButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    expect(container.textContent).not.toContain('Field standards')

    const expandButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Expand child nodes"]',
    )
    expect(expandButton).not.toBeNull()
    act(() => {
      expandButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    expect(container.textContent).toContain('Field standards')

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
