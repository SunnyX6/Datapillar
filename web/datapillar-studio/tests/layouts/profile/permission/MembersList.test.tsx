// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MembersList } from '@/features/profile/ui/permission/MembersList'
import type { RoleDefinition, UserItem } from '@/features/profile/utils/permissionTypes'

function render(ui: JSX.Element) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(ui)
  })
  return { container, root }
}

function unmount(root: Root, container: HTMLDivElement) {
  act(() => {
    root.unmount()
  })
  container.remove()
}

const role: RoleDefinition = {
  id: '1',
  type: 'USER',
  name: 'data development',
  description: 'test role',
  memberCount: 2,
  permissions: [],
}

const users: UserItem[] = [
  {
    id: '100',
    name: 'test',
    email: 'test@qq.com',
    roleId: '1',
    status: 'Activated',
    lastActive: '2026-02-25 11:27:04',
  },
  {
    id: '101',
    name: 'sunny',
    email: 'sunny@example.com',
    roleId: '1',
    status: 'Activated',
    lastActive: '2026-02-24 15:30:00',
  },
]

describe('MembersList', () => {
  beforeEach(() => {
    ;(
      globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }
    ).IS_REACT_ACT_ENVIRONMENT = true
  })

  it('Display batch operation bar after clicking member row', () => {
    const { container, root } = render(
      <MembersList
        role={role}
        selectedRoleId={role.id}
        users={users}
        onUpdateUserModelAccess={() => {}}
        showToolbar={false}
      />,
    )

    const targetRow = Array.from(container.querySelectorAll('tr')).find((row) =>
      row.textContent?.includes('test@qq.com'),
    )
    expect(targetRow).not.toBeUndefined()

    act(() => {
      targetRow?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const batchDeleteButton = document.querySelector<HTMLButtonElement>(
      'button[aria-label="Delete members in batches"]',
    )
    expect(batchDeleteButton).not.toBeNull()
    expect(document.body.textContent).toContain('Selected')

    unmount(root, container)
  })

  it('The batch delete button triggers deletion and clears the selected status', async () => {
    const onDeleteUsers = vi.fn().mockResolvedValue(['100'])
    const { container, root } = render(
      <MembersList
        role={role}
        selectedRoleId={role.id}
        users={users}
        onUpdateUserModelAccess={() => {}}
        onDeleteUsers={onDeleteUsers}
        showToolbar={false}
      />,
    )

    const targetRow = Array.from(container.querySelectorAll('tr')).find((row) =>
      row.textContent?.includes('test@qq.com'),
    )
    expect(targetRow).not.toBeUndefined()
    act(() => {
      targetRow?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const batchDeleteButton = document.querySelector<HTMLButtonElement>(
      'button[aria-label="Delete members in batches"]',
    )
    expect(batchDeleteButton).not.toBeNull()

    await act(async () => {
      batchDeleteButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(onDeleteUsers).toHaveBeenCalledWith(['100'])
    expect(
      document.querySelector<HTMLButtonElement>('button[aria-label="Delete members in batches"]'),
    ).toBeNull()

    unmount(root, container)
  })

  it('Platform super management members do not display the configure permission button', () => {
    const usersWithPlatformAdmin: UserItem[] = [
      ...users,
      {
        id: '102',
        name: 'platform-admin',
        email: 'platform-admin@example.com',
        roleId: '1',
        level: 0,
        status: 'Activated',
        lastActive: '2026-02-25 18:00:00',
      },
    ]

    const { container, root } = render(
      <MembersList
        role={role}
        selectedRoleId={role.id}
        users={usersWithPlatformAdmin}
        onUpdateUserModelAccess={() => {}}
        showToolbar={false}
      />,
    )

    const platformAdminRow = Array.from(container.querySelectorAll('tr')).find((row) =>
      row.textContent?.includes('platform-admin@example.com'),
    )
    expect(platformAdminRow).not.toBeUndefined()
    expect(platformAdminRow?.textContent).toContain('Platform over management')
    expect(platformAdminRow?.textContent).not.toContain('Configure permissions')

    unmount(root, container)
  })
})
