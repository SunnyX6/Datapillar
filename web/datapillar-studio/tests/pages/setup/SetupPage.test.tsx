// @vitest-environment jsdom
import { act, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { InputGroup, SetupPage } from '@/pages/setup'
import { getSetupStatus } from '@/services/setupService'
import { useSetupStore } from '@/state'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key
    })
  }
})

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
    width: 400,
    height: 700,
    immediateScale: 1,
    ready: true
  })
}))

vi.mock('@/components', () => ({
  BrandLogo: () => <div data-testid="brand-logo" />,
  Button: ({ children, ...props }: ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button {...props}>
      {children}
    </button>
  ),
  ThemeToggle: () => <div data-testid="theme-toggle" />,
  LanguageToggle: () => <div data-testid="language-toggle" />
}))

vi.mock('@/features/auth/ui/DemoCanvas', () => ({
  DemoCanvas: () => <div data-testid="demo-canvas" />
}))

vi.mock('@/services/setupService', () => ({
  getSetupStatus: vi.fn(),
  initializeSetup: vi.fn()
}))

const mockedGetSetupStatus = vi.mocked(getSetupStatus)

interface RenderResult {
  container: HTMLDivElement
  root: Root
  router: ReturnType<typeof createMemoryRouter>
}

function renderWithRouter(initialEntry: string): RenderResult {
  const router = createMemoryRouter([
    {
      path: '/setup',
      element: <SetupPage />
    },
    {
      path: '/login',
      element: <div>login-page</div>
    }
  ], {
    initialEntries: [initialEntry]
  })

  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)

  act(() => {
    root.render(<RouterProvider router={router} />)
  })

  return { container, root, router }
}

async function flushEffects() {
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
  })
}

function cleanup(root: Root, container: HTMLDivElement) {
  act(() => {
    root.unmount()
  })
  container.remove()
}

describe('SetupPage', () => {
  beforeEach(() => {
    ;(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    useSetupStore.getState().resetSetupStatus()
    vi.clearAllMocks()
  })

  afterEach(() => {
    useSetupStore.getState().resetSetupStatus()
  })

  it('初始化已完成时应同步 guard 状态并跳转登录页，避免 status 请求死循环', async () => {
    mockedGetSetupStatus.mockResolvedValue({
      schemaReady: true,
      initialized: true,
      currentStep: 'COMPLETED',
      steps: []
    })

    const { container, root, router } = renderWithRouter('/setup')
    await flushEffects()

    expect(router.state.location.pathname).toBe('/login')
    expect(useSetupStore.getState().initialized).toBe(true)
    expect(mockedGetSetupStatus).toHaveBeenCalledTimes(1)

    cleanup(root, container)
  })

  it('密码眼睛按钮应切换输入框明文/密文', async () => {
    const container = document.createElement('div')
    document.body.appendChild(container)
    const root = createRoot(container)

    await act(async () => {
      root.render(
        <InputGroup
          label="密码"
          type="password"
          value="123456"
          placeholder="请输入密码"
          onChange={vi.fn()}
          required
        />
      )
    })

    expect(container.querySelector('input[type="password"]')).toBeTruthy()

    const eyeButton = container.querySelector('button[aria-label="显示密码"]') as HTMLButtonElement | null
    expect(eyeButton).toBeTruthy()
    await act(async () => {
      eyeButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(container.querySelector('input[type="text"]')).toBeTruthy()
    expect(container.querySelector('button[aria-label="隐藏密码"]')).toBeTruthy()

    cleanup(root, container)
  })
})
