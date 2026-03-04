// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { act } from 'react-dom/test-utils'
import { createRoot } from 'react-dom/client'
import { RoleList } from '@/features/profile/ui/permission/RoleList'
import type { RoleItem } from '@/features/profile/utils/permissionTypes'

const roles: RoleItem[] = [
  {
    id: 'role_dev',
    type: 'USER',
    name: 'R&D Engineer',
    description: 'for testing',
    permissions: [
      {
        objectId: 101,
        objectName: 'metadata directory',
        objectPath: '/governance/metadata',
        objectType: 'MENU',
        location: 'governance',
        sort: 1,
        categoryName: 'data assets',
        level: 'READ',
        tenantLevel: 'ADMIN',
        children: [],
      },
    ],
    userCount: 1,
  },
  {
    id: 'role_data',
    type: 'USER',
    name: 'data analyst',
    description: 'for deleting tests',
    permissions: [
      {
        objectId: 101,
        objectName: 'metadata directory',
        objectPath: '/governance/metadata',
        objectType: 'MENU',
        location: 'governance',
        sort: 1,
        categoryName: 'data assets',
        level: 'READ',
        tenantLevel: 'ADMIN',
        children: [],
      },
    ],
    userCount: 0,
  },
]

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

describe('RoleList', () => {
  it('When adding a new role, you can select the role type and submit it', () => {
    const onCreateRole = vi.fn()
    const { container, root } = render(
      <RoleList
        roles={roles}
        selectedRoleId="role_dev"
        onSelectRole={() => {}}
        onCreateRole={onCreateRole}
      />,
    )

    const openButton = container.querySelector('button[aria-label="Add new role"]')
    expect(openButton).not.toBeNull()

    act(() => {
      openButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const nameInput = document.querySelector<HTMLInputElement>(
      'input[placeholder="Please enter a role name"]',
    )
    expect(nameInput).not.toBeNull()

    act(() => {
      const setter = Object.getOwnPropertyDescriptor(
        HTMLInputElement.prototype,
        'value',
      )?.set
      setter?.call(nameInput, 'data manager')
      nameInput?.dispatchEvent(new Event('input', { bubbles: true }))
    })

    const adminTypeButton = Array.from(
      document.querySelectorAll('button'),
    ).find((button) => button.textContent?.includes('ADMIN'))
    expect(adminTypeButton).not.toBeUndefined()

    act(() => {
      adminTypeButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const createButton = Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent?.includes('Create a role'),
    )
    expect(createButton).not.toBeUndefined()

    act(() => {
      createButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onCreateRole).toHaveBeenCalledWith({
      name: 'data manager',
      description: undefined,
      type: 'ADMIN',
    })

    unmount(root, container)
  })

  it('Disable deletion when role has members，Allow deletion when there are no members', () => {
    const onDeleteRole = vi.fn().mockResolvedValue(true)
    const { container, root } = render(
      <RoleList
        roles={roles}
        selectedRoleId="role_dev"
        onSelectRole={() => {}}
        onCreateRole={vi.fn()}
        onDeleteRole={onDeleteRole}
      />,
    )

    const disabledDeleteButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Delete role-R&D Engineer"]',
    )
    expect(disabledDeleteButton?.disabled).toBe(true)

    const enabledDeleteButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Delete role-data analyst"]',
    )
    expect(enabledDeleteButton?.disabled).toBe(false)

    act(() => {
      enabledDeleteButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const confirmDeleteButton = Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent?.includes('Confirm deletion'),
    )
    expect(confirmDeleteButton).not.toBeUndefined()

    act(() => {
      confirmDeleteButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onDeleteRole).toHaveBeenCalledWith('role_data')

    unmount(root, container)
  })

  it('Click the edit icon to submit character updates', () => {
    const onUpdateRole = vi.fn().mockResolvedValue(true)
    const { container, root } = render(
      <RoleList
        roles={roles}
        selectedRoleId="role_dev"
        onSelectRole={() => {}}
        onCreateRole={vi.fn()}
        onUpdateRole={onUpdateRole}
      />,
    )

    const editButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Edit role-data analyst"]',
    )
    expect(editButton?.disabled).toBe(false)

    act(() => {
      editButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const nameInput = document.querySelector<HTMLInputElement>(
      'input[placeholder="Please enter a role name"]',
    )
    expect(nameInput).not.toBeNull()

    act(() => {
      const setter = Object.getOwnPropertyDescriptor(
        HTMLInputElement.prototype,
        'value',
      )?.set
      setter?.call(nameInput, 'Data Governance Analyst')
      nameInput?.dispatchEvent(new Event('input', { bubbles: true }))
    })

    const saveButton = Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent?.includes('Save changes'),
    )
    expect(saveButton).not.toBeUndefined()

    act(() => {
      saveButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onUpdateRole).toHaveBeenCalledWith('role_data', {
      name: 'Data Governance Analyst',
      description: 'for deleting tests',
      type: 'USER',
    })

    unmount(root, container)
  })

  it('Edit and delete buttons use universal Tooltip rather than native title', () => {
    const { container, root } = render(
      <RoleList
        roles={roles}
        selectedRoleId="role_dev"
        onSelectRole={() => {}}
        onCreateRole={vi.fn()}
      />,
    )

    const editButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Edit role-R&D Engineer"]',
    )
    const disabledDeleteButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="Delete role-R&D Engineer"]',
    )

    expect(editButton?.getAttribute('title')).toBeNull()
    expect(disabledDeleteButton?.getAttribute('title')).toBeNull()

    act(() => {
      editButton?.parentElement?.dispatchEvent(
        new MouseEvent('mouseover', { bubbles: true }),
      )
    })
    expect(document.body.textContent).toContain('Edit role')

    act(() => {
      disabledDeleteButton?.parentElement?.dispatchEvent(
        new MouseEvent('mouseover', { bubbles: true }),
      )
    })
    expect(document.body.textContent).toContain('There are members under the role，cannot be deleted')

    unmount(root, container)
  })
})
