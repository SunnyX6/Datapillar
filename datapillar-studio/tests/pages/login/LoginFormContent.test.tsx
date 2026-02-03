// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { act } from 'react-dom/test-utils'
import { createRoot } from 'react-dom/client'
import { LoginFormContent } from '@/pages/login/LoginForm'

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn()
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key
  })
}))

vi.mock('@/stores', () => ({
  useThemeMode: () => 'light',
  useAuthStore: (selector: (state: { login: () => Promise<unknown>; loading: boolean }) => unknown) =>
    selector({
      login: vi.fn().mockResolvedValue({
        loginStage: 'SUCCESS',
        userId: 10001,
        tenantId: 0,
        username: 'mock-user',
        roles: [],
        menus: []
      }),
      loading: false
    })
}))

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

describe('LoginFormContent', () => {
  it('展示企业SSO快捷登录按钮与三方图标', () => {
    const { container, root } = render(<LoginFormContent onSsoClick={vi.fn()} />)

    const buttons = Array.from(container.querySelectorAll('button'))
    const quickSsoButton = buttons.find((button) => button.textContent?.includes('企业SSO 快捷登录'))

    expect(quickSsoButton).toBeTruthy()
    expect(quickSsoButton?.querySelectorAll('svg').length).toBe(4)

    unmount(root, container)
  })
})
