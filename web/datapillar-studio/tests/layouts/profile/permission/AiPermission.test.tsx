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
  name: 'R&D Engineer',
  description: 'for testing',
  permissions: [],
  aiModelPermissions: [
    { aiModelId: 1, access: 'READ' },
    { aiModelId: 2, access: 'READ' },
    { aiModelId: 3, access: 'DISABLE' },
  ],
}

const user: UserItem = {
  id: 'u-1',
  name: 'test user',
  email: 'test@datapillar.io',
  roleId: 'role_dev',
  status: 'Activated',
  lastActive: 'just now',
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
  it('Display independent permission prompt in user mode', () => {
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

    expect(container.textContent).toContain('Independent permission configuration mode')
    expect(container.textContent).toContain('You are configuring this user individually AI Model permissions')
    expect(
      container.querySelector('[data-testid="ai-access-1-ADMIN"]'),
    ).not.toBeNull()

    unmount(root, container)
  })

  it('Pass user dimension parameters when clicking the permission button in user mode', () => {
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
