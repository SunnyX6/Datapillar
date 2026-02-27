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
    name: '研发工程师',
    description: '用于测试',
    permissions: [
      {
        objectId: 101,
        objectName: '元数据目录',
        objectPath: '/governance/metadata',
        objectType: 'MENU',
        location: 'governance',
        sort: 1,
        categoryName: '数据资产',
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
    name: '数据分析师',
    description: '用于删除测试',
    permissions: [
      {
        objectId: 101,
        objectName: '元数据目录',
        objectPath: '/governance/metadata',
        objectType: 'MENU',
        location: 'governance',
        sort: 1,
        categoryName: '数据资产',
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
  it('新增角色时可选择角色类型并提交', () => {
    const onCreateRole = vi.fn()
    const { container, root } = render(
      <RoleList
        roles={roles}
        selectedRoleId="role_dev"
        onSelectRole={() => {}}
        onCreateRole={onCreateRole}
      />,
    )

    const openButton = container.querySelector('button[aria-label="新增角色"]')
    expect(openButton).not.toBeNull()

    act(() => {
      openButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const nameInput = document.querySelector<HTMLInputElement>(
      'input[placeholder="请输入角色名称"]',
    )
    expect(nameInput).not.toBeNull()

    act(() => {
      const setter = Object.getOwnPropertyDescriptor(
        HTMLInputElement.prototype,
        'value',
      )?.set
      setter?.call(nameInput, '数据管理员')
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
      (button) => button.textContent?.includes('创建角色'),
    )
    expect(createButton).not.toBeUndefined()

    act(() => {
      createButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onCreateRole).toHaveBeenCalledWith({
      name: '数据管理员',
      description: undefined,
      type: 'ADMIN',
    })

    unmount(root, container)
  })

  it('角色有成员时禁用删除，无成员时允许删除', () => {
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
      'button[aria-label="删除角色-研发工程师"]',
    )
    expect(disabledDeleteButton?.disabled).toBe(true)

    const enabledDeleteButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="删除角色-数据分析师"]',
    )
    expect(enabledDeleteButton?.disabled).toBe(false)

    act(() => {
      enabledDeleteButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const confirmDeleteButton = Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent?.includes('确认删除'),
    )
    expect(confirmDeleteButton).not.toBeUndefined()

    act(() => {
      confirmDeleteButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onDeleteRole).toHaveBeenCalledWith('role_data')

    unmount(root, container)
  })

  it('点击编辑图标后可提交角色更新', () => {
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
      'button[aria-label="编辑角色-数据分析师"]',
    )
    expect(editButton?.disabled).toBe(false)

    act(() => {
      editButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    const nameInput = document.querySelector<HTMLInputElement>(
      'input[placeholder="请输入角色名称"]',
    )
    expect(nameInput).not.toBeNull()

    act(() => {
      const setter = Object.getOwnPropertyDescriptor(
        HTMLInputElement.prototype,
        'value',
      )?.set
      setter?.call(nameInput, '数据治理分析师')
      nameInput?.dispatchEvent(new Event('input', { bubbles: true }))
    })

    const saveButton = Array.from(document.querySelectorAll('button')).find(
      (button) => button.textContent?.includes('保存修改'),
    )
    expect(saveButton).not.toBeUndefined()

    act(() => {
      saveButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onUpdateRole).toHaveBeenCalledWith('role_data', {
      name: '数据治理分析师',
      description: '用于删除测试',
      type: 'USER',
    })

    unmount(root, container)
  })

  it('编辑和删除按钮使用通用 Tooltip 而不是原生 title', () => {
    const { container, root } = render(
      <RoleList
        roles={roles}
        selectedRoleId="role_dev"
        onSelectRole={() => {}}
        onCreateRole={vi.fn()}
      />,
    )

    const editButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="编辑角色-研发工程师"]',
    )
    const disabledDeleteButton = container.querySelector<HTMLButtonElement>(
      'button[aria-label="删除角色-研发工程师"]',
    )

    expect(editButton?.getAttribute('title')).toBeNull()
    expect(disabledDeleteButton?.getAttribute('title')).toBeNull()

    act(() => {
      editButton?.parentElement?.dispatchEvent(
        new MouseEvent('mouseover', { bubbles: true }),
      )
    })
    expect(document.body.textContent).toContain('编辑角色')

    act(() => {
      disabledDeleteButton?.parentElement?.dispatchEvent(
        new MouseEvent('mouseover', { bubbles: true }),
      )
    })
    expect(document.body.textContent).toContain('角色下存在成员，无法删除')

    unmount(root, container)
  })
})
