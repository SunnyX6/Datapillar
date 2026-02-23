// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { act } from 'react-dom/test-utils'
import { createRoot } from 'react-dom/client'
import { AiPermission } from '@/layouts/profile/permission/AiPermission'
import type {
  RoleDefinition,
  UserItem,
} from '@/layouts/profile/permission/Permission'

const role: RoleDefinition = {
  id: 'role_dev',
  type: 'USER',
  name: '研发工程师',
  description: '用于测试',
  permissions: [],
  aiModelPermissions: [
    { modelId: 'model_gemini_3_pro', access: 'READ' },
    { modelId: 'model_claude_3_5_sonnet', access: 'READ' },
    { modelId: 'model_llama_3_1_405b', access: 'DISABLE' },
  ],
}

const user: UserItem = {
  id: 'u-1',
  name: '测试用户',
  email: 'test@datapillar.io',
  roleId: 'role_dev',
  status: '已激活',
  lastActive: '刚刚',
  aiModelPermissions: [{ modelId: 'model_claude_3_5_sonnet', access: 'READ' }],
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

describe('AiPermission', () => {
  it('用户模式显示独立权限提示', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <AiPermission
        mode="user"
        role={role}
        user={user}
        onUpdateModelAccess={onUpdate}
      />,
    )

    expect(container.textContent).toContain('独立权限配置模式')
    expect(container.textContent).toContain('覆盖')
    expect(
      container.querySelector('[data-testid="ai-access-model_gemini_3_pro-ADMIN"]'),
    ).not.toBeNull()

    unmount(root, container)
  })

  it('用户模式点击权限按钮时传递用户维度参数', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <AiPermission
        mode="user"
        role={role}
        user={user}
        onUpdateModelAccess={onUpdate}
      />,
    )

    const button = container.querySelector(
      '[data-testid="ai-access-model_llama_3_1_405b-READ"]',
    )
    expect(button).not.toBeNull()

    act(() => {
      button?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onUpdate).toHaveBeenCalledWith('u-1', 'model_llama_3_1_405b', 'READ')

    unmount(root, container)
  })
})
