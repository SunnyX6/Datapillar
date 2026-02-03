// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { act } from 'react-dom/test-utils'
import { createRoot } from 'react-dom/client'
import { FunctionalPermission } from '@/layouts/profile/permission/FunctionalPermission'
import type { RoleDefinition, UserItem } from '@/layouts/profile/permission/Permission'

const basePermissions: RoleDefinition['permissions'] = [
  {
    id: 'asset.catalog',
    name: '元数据目录',
    category: '数据资产',
    description: '浏览数据目录与资产信息',
    level: 'READ'
  },
  {
    id: 'build.workflow',
    name: '工作流编排',
    category: '开发与发布',
    description: '编排与调度数据工作流',
    level: 'NONE'
  }
]

const role: RoleDefinition = {
  id: 'role_dev',
  name: '研发工程师',
  description: '用于测试',
  permissions: basePermissions
}

const user: UserItem = {
  id: 'u-1',
  name: '测试用户',
  email: 'test@datapillar.io',
  roleId: 'role_dev',
  status: '已激活',
  lastActive: '刚刚',
  customPermissions: [{ id: 'asset.catalog', level: 'WRITE' }]
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

const unmount = (root: ReturnType<typeof createRoot>, container: HTMLDivElement) => {
  act(() => {
    root.unmount()
  })
  container.remove()
}

describe('FunctionalPermission', () => {
  it('角色模式渲染权限项', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <FunctionalPermission mode="role" role={role} onUpdatePermission={onUpdate} />
    )

    expect(container.textContent).toContain('元数据目录')
    expect(container.textContent).toContain('浏览数据目录与资产信息')

    unmount(root, container)
  })

  it('用户模式显示继承与覆盖提示', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <FunctionalPermission mode="user" role={role} user={user} onUpdatePermission={onUpdate} />
    )

    expect(container.textContent).toContain('独立权限配置模式')
    expect(container.textContent).toContain('覆盖继承')
    expect(container.textContent).toContain('继承自角色')

    unmount(root, container)
  })
})
