// @vitest-environment jsdom
import { StrictMode, act, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InviteMemberModal } from '@/layouts/profile/permission/InviteMemberModal'
import type { RoleDefinition } from '@/layouts/profile/permission/Permission'
import type { CreateTenantInvitationResponse } from '@/services/studioTenantAdminService'

const createTenantInvitationMock = vi.fn()

vi.mock('@/services/studioTenantAdminService', () => ({
  createTenantInvitation: (tenantId: number, payload: unknown) => createTenantInvitationMock(tenantId, payload)
}))

vi.mock('@/stores/authStore', () => ({
  useAuthStore: (selector: (state: { user: { tenantId: number } }) => unknown) =>
    selector({ user: { tenantId: 10 } })
}))

vi.mock('sonner', () => ({
  toast: {
    error: vi.fn()
  }
}))

vi.mock('@/components/ui', () => ({
  Modal: ({ isOpen, children }: { isOpen: boolean; children: ReactNode }) =>
    isOpen ? <div data-testid="invite-modal">{children}</div> : null,
  Button: ({ children, ...props }: ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button {...props}>{children}</button>
  )
}))

interface RenderResult {
  container: HTMLDivElement
  root: Root
}

function render(ui: JSX.Element): RenderResult {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(ui)
  })
  return { container, root }
}

function cleanup(root: Root, container: HTMLDivElement) {
  act(() => {
    root.unmount()
  })
  container.remove()
}

const role: RoleDefinition = {
  id: '301',
  type: 'USER',
  name: 'Data Analyst',
  description: 'Data role',
  permissions: []
}

describe('InviteMemberModal', () => {
  beforeEach(() => {
    ;(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.clearAllMocks()
    const response: CreateTenantInvitationResponse = {
      invitationId: 981,
      inviteCode: '9F2KQ7M4P8WX',
      inviteUri: '/invite?inviteCode=9F2KQ7M4P8WX',
      expiresAt: '2026-03-05T23:59:59+08:00',
      tenantName: 'Data Engineering Core',
      roleId: 301,
      roleName: 'Data Analyst',
      inviterName: 'Sarah Chen'
    }
    createTenantInvitationMock.mockResolvedValue(response)
  })

  it('严格模式下只触发一次创建邀请，并将返回的 URI 拼成当前站点链接', async () => {
    const { container, root } = render(
      <StrictMode>
        <InviteMemberModal isOpen onClose={() => {}} role={role} roleId="301" />
      </StrictMode>
    )

    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(createTenantInvitationMock).toHaveBeenCalledTimes(1)
    expect(createTenantInvitationMock).toHaveBeenCalledWith(10, {
      roleId: 301,
      expiresAt: expect.any(String)
    })
    expect(container.textContent).toContain('/invite?inviteCode=9F2KQ7M4P8WX')

    cleanup(root, container)
  })
})
