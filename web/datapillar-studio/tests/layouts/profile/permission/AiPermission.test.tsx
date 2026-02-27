// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { act } from 'react-dom/test-utils'
import { createRoot } from 'react-dom/client'
import { AiPermission } from '@/features/profile/ui/permission/AiPermission'
import type {
  RoleDefinition,
  UserItem,
} from '@/features/profile/utils/permissionTypes'

const role: RoleDefinition = {
  id: 'role_dev',
  type: 'USER',
  name: '研发工程师',
  description: '用于测试',
  permissions: [],
  aiModelPermissions: [
    { aiModelId: 1, access: 'READ' },
    { aiModelId: 2, access: 'READ' },
    { aiModelId: 3, access: 'DISABLE' },
  ],
}

const user: UserItem = {
  id: 'u-1',
  name: '测试用户',
  email: 'test@datapillar.io',
  roleId: 'role_dev',
  status: '已激活',
  lastActive: '刚刚',
  aiModelPermissions: [{ aiModelId: 2, access: 'READ' }],
}

const models = [
  {
    aiModelId: 1,
    providerModelId: 'model_gemini_3_pro',
    name: 'Gemini 3 Pro',
    providerName: 'Google',
    modelType: 'chat',
    modelStatus: 'ACTIVE',
    access: 'DISABLE' as const,
  },
  {
    aiModelId: 2,
    providerModelId: 'model_claude_3_5_sonnet',
    name: 'Claude 3.5 Sonnet',
    providerName: 'Anthropic',
    modelType: 'chat',
    modelStatus: 'ACTIVE',
    access: 'READ' as const,
  },
  {
    aiModelId: 3,
    providerModelId: 'model_llama_3_1_405b',
    name: 'Llama 3.1 405B',
    providerName: 'Meta',
    modelType: 'chat',
    modelStatus: 'ACTIVE',
    access: 'DISABLE' as const,
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

describe('AiPermission', () => {
  it('用户模式显示独立权限提示', () => {
    const onUpdate = vi.fn()
    const { container, root } = render(
      <AiPermission
        mode="user"
        role={role}
        user={user}
        models={models}
        onUpdateModelAccess={onUpdate}
      />,
    )

    expect(container.textContent).toContain('独立权限配置模式')
    expect(container.textContent).toContain('您正在为该用户单独配置 AI 模型权限')
    expect(
      container.querySelector('[data-testid="ai-access-1-ADMIN"]'),
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
        models={models}
        onUpdateModelAccess={onUpdate}
      />,
    )

    const button = container.querySelector(
      '[data-testid="ai-access-3-READ"]',
    )
    expect(button).not.toBeNull()

    act(() => {
      button?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onUpdate).toHaveBeenCalledWith('u-1', 3, 'READ')

    unmount(root, container)
  })
})
