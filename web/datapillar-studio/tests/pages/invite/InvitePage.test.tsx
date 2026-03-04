// @vitest-environment jsdom
import { act, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { InvitePage } from '@/pages/invite'

let searchParams = new URLSearchParams()
const navigateMock = vi.fn()
const registerInvitationMock = vi.fn()
const getInvitationByCodeMock = vi.fn()
const toastErrorMock = vi.fn()
const logoutMock = vi.fn()
const loginMock = vi.fn()

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key
  })
}))

vi.mock('react-router-dom', () => ({
  useSearchParams: () => [searchParams, vi.fn()],
  useNavigate: () => navigateMock
}))

vi.mock('@/services/studioInvitationService', () => ({
  getInvitationByCode: (inviteCode: string) => getInvitationByCodeMock(inviteCode),
  registerInvitation: (payload: unknown) => registerInvitationMock(payload)
}))

vi.mock('@/state/authStore', () => ({
  useAuthStore: {
    getState: () => ({
      logout: logoutMock,
      login: loginMock
    })
  }
}))

vi.mock('sonner', () => ({
  toast: {
    error: (message: string) => toastErrorMock(message)
  }
}))

vi.mock('@/layouts/responsive', () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div data-testid="app-layout">{children}</div>,
  SplitGrid: ({ left, right }: { left: ReactNode; right: ReactNode }) => (
    <div data-testid="split-grid">
      <div>{left}</div>
      <div>{right}</div>
    </div>
  ),
  useLayout: () => ({
    ref: { current: null },
    scale: 1,
    ready: true,
    width: 400,
    height: 620,
    immediateScale: 1
  })
}))

vi.mock('@/components', () => ({
  ThemeToggle: () => <div data-testid="theme-toggle" />,
  LanguageToggle: () => <div data-testid="language-toggle" />
}))

vi.mock('@/components/ui', () => ({
  Button: ({ children, ...props }: ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button {...props}>
      {children}
    </button>
  )
}))

vi.mock('@/features/auth/ui/DemoCanvas', () => ({
  DemoCanvas: () => <div data-testid="demo-canvas" />
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

describe('InvitePage', () => {
  beforeEach(() => {
    ;(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.clearAllMocks()
    registerInvitationMock.mockResolvedValue(undefined)
    logoutMock.mockResolvedValue(undefined)
    loginMock.mockResolvedValue({
      userId: 1001,
      menus: [
        {
          id: 1,
          name: 'Home page',
          path: '/home',
          location: 'TOP',
          permissionCode: 'READ'
        }
      ]
    })
    getInvitationByCodeMock.mockResolvedValue({
      inviteCode: 'inv-123',
      tenantName: 'Data Engineering Core',
      roleId: 301,
      roleName: 'Data Analyst',
      inviterName: 'Sarah Chen',
      expiresAt: '2026-03-05T23:59:59+08:00',
      status: 0
    })
  })

  it('When the parameters are complete, the invitation registration form should be displayed and registration submitted.', async () => {
    searchParams = new URLSearchParams('inviteCode=inv-123')
    const { container, root } = render(<InvitePage />)

    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(getInvitationByCodeMock).toHaveBeenCalledWith('inv-123')
    expect(container.textContent).toContain('invite you to join Data Analyst role')
    expect(container.textContent).toContain('Sarah Chen')

    const emailInput = container.querySelector('input[type="email"]') as HTMLInputElement | null
    const usernameInput = container.querySelector('input[placeholder="Please enter username"]') as HTMLInputElement | null
    expect(emailInput?.value).toBe('')
    expect(emailInput?.disabled).toBe(false)
    expect(emailInput?.placeholder).toBe('Please enter your work email')
    expect(usernameInput?.value).toBe('')
    expect(usernameInput?.disabled).toBe(false)
    expect(container.querySelector('input[placeholder="Please enter your real name"]')).toBeNull()

    const submitButton = Array.from(container.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Accept the invitation and join')
    ) as HTMLButtonElement | undefined
    const passwordInput = container.querySelector('input[placeholder="Create login password"]') as HTMLInputElement | null
    const form = container.querySelector('form')

    expect(submitButton).toBeTruthy()
    expect(submitButton?.disabled).toBe(false)
    expect(form).toBeTruthy()

    act(() => {
      if (emailInput) {
        emailInput.value = 'member@company.com'
        emailInput.dispatchEvent(new Event('input', { bubbles: true }))
      }
      if (usernameInput) {
        usernameInput.value = 'member_user'
        usernameInput.dispatchEvent(new Event('input', { bubbles: true }))
      }
      if (passwordInput) {
        passwordInput.value = '123456'
        passwordInput.dispatchEvent(new Event('input', { bubbles: true }))
      }
    })

    await act(async () => {
      form?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    })

    expect(registerInvitationMock).toHaveBeenCalledWith({
      inviteCode: 'inv-123',
      username: 'member_user',
      email: 'member@company.com',
      password: '123456'
    })
    expect(logoutMock).toHaveBeenCalledTimes(1)
    expect(loginMock).toHaveBeenCalledWith('member_user', '123456', false)
    expect(navigateMock).toHaveBeenCalledWith('/', { replace: true })

    cleanup(root, container)
  })

  it('When the invitation code is missing, an error message should be displayed and the submit button should be disabled.', () => {
    searchParams = new URLSearchParams('')
    const { container, root } = render(<InvitePage />)

    expect(container.textContent).toContain('The invitation link is missing the invitation code')
    expect(getInvitationByCodeMock).not.toHaveBeenCalled()

    const submitButton = Array.from(container.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Accept the invitation and join')
    ) as HTMLButtonElement | undefined
    expect(submitButton?.disabled).toBe(true)

    cleanup(root, container)
  })

  it('Should pass when registration failstoastPrompt error', async () => {
    searchParams = new URLSearchParams('inviteCode=inv-err-001')
    getInvitationByCodeMock.mockResolvedValueOnce({
      inviteCode: 'inv-err-001',
      tenantName: 'Data Engineering Core',
      roleId: 301,
      roleName: 'Data Analyst',
      inviterName: 'Sarah Chen',
      expiresAt: '2026-03-05T23:59:59+08:00',
      status: 0
    })
    registerInvitationMock.mockRejectedValueOnce(new Error('The invitation code has expired'))
    const { container, root } = render(<InvitePage />)

    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    const emailInput = container.querySelector('input[type="email"]') as HTMLInputElement | null
    const usernameInput = container.querySelector('input[placeholder="Please enter username"]') as HTMLInputElement | null
    const passwordInput = container.querySelector('input[placeholder="Create login password"]') as HTMLInputElement | null
    const form = container.querySelector('form')

    act(() => {
      if (emailInput) {
        emailInput.value = 'member@company.com'
        emailInput.dispatchEvent(new Event('input', { bubbles: true }))
      }
      if (usernameInput) {
        usernameInput.value = 'member_user'
        usernameInput.dispatchEvent(new Event('input', { bubbles: true }))
      }
      if (passwordInput) {
        passwordInput.value = '123456'
        passwordInput.dispatchEvent(new Event('input', { bubbles: true }))
      }
    })

    await act(async () => {
      form?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    })

    expect(toastErrorMock).toHaveBeenCalledWith('The invitation code has expired')
    expect(navigateMock).not.toHaveBeenCalled()

    cleanup(root, container)
  })
})
