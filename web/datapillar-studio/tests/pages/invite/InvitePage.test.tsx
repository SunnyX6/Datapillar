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
          name: '首页',
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

  it('参数完整时应展示邀请注册表单并提交注册', async () => {
    searchParams = new URLSearchParams('inviteCode=inv-123')
    const { container, root } = render(<InvitePage />)

    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(getInvitationByCodeMock).toHaveBeenCalledWith('inv-123')
    expect(container.textContent).toContain('邀请你加入 Data Analyst 角色')
    expect(container.textContent).toContain('Sarah Chen')

    const emailInput = container.querySelector('input[type="email"]') as HTMLInputElement | null
    const usernameInput = container.querySelector('input[placeholder="请输入用户名"]') as HTMLInputElement | null
    expect(emailInput?.value).toBe('')
    expect(emailInput?.disabled).toBe(false)
    expect(emailInput?.placeholder).toBe('请输入工作邮箱')
    expect(usernameInput?.value).toBe('')
    expect(usernameInput?.disabled).toBe(false)
    expect(container.querySelector('input[placeholder="请输入真实姓名"]')).toBeNull()

    const submitButton = Array.from(container.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('接受邀请并加入')
    ) as HTMLButtonElement | undefined
    const passwordInput = container.querySelector('input[placeholder="创建登录密码"]') as HTMLInputElement | null
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

  it('缺少邀请码时应显示错误提示并禁用提交按钮', () => {
    searchParams = new URLSearchParams('')
    const { container, root } = render(<InvitePage />)

    expect(container.textContent).toContain('邀请链接缺少邀请码')
    expect(getInvitationByCodeMock).not.toHaveBeenCalled()

    const submitButton = Array.from(container.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('接受邀请并加入')
    ) as HTMLButtonElement | undefined
    expect(submitButton?.disabled).toBe(true)

    cleanup(root, container)
  })

  it('注册失败时应通过toast提示错误', async () => {
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
    registerInvitationMock.mockRejectedValueOnce(new Error('邀请码已过期'))
    const { container, root } = render(<InvitePage />)

    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    const emailInput = container.querySelector('input[type="email"]') as HTMLInputElement | null
    const usernameInput = container.querySelector('input[placeholder="请输入用户名"]') as HTMLInputElement | null
    const passwordInput = container.querySelector('input[placeholder="创建登录密码"]') as HTMLInputElement | null
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

    expect(toastErrorMock).toHaveBeenCalledWith('邀请码已过期')
    expect(navigateMock).not.toHaveBeenCalled()

    cleanup(root, container)
  })
})
