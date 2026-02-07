// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { act } from 'react-dom/test-utils'
import { createRoot } from 'react-dom/client'
import { InvitePage } from '@/pages/invite'

let searchParams = new URLSearchParams()

vi.mock('react-router-dom', () => ({
  useSearchParams: () => [searchParams, vi.fn()],
  useNavigate: () => vi.fn()
}))

vi.mock('@/components', () => ({
  BrandLogo: ({ brandName, brandTagline }: { brandName?: string; brandTagline?: string }) => (
    <div data-testid="brand-logo">
      {brandName}
      {brandTagline}
    </div>
  ),
  ThemeToggle: () => <div data-testid="theme-toggle" />,
  LanguageToggle: () => <div data-testid="language-toggle" />
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

describe('InvitePage', () => {
  it('参数完整时展示邀请确认信息', () => {
    searchParams = new URLSearchParams('tenantCode=acme&inviteCode=inv-123')
    const { container, root } = render(<InvitePage />)

    expect(container.textContent).toContain('企业邀请确认')
    expect(container.textContent).toContain('邀请已确认')
    expect(container.textContent).toContain('前往登录')
    expect(container.textContent).toContain('已确认')

    unmount(root, container)
  })

  it('缺少邀请码时展示错误提示', () => {
    searchParams = new URLSearchParams('tenantCode=acme')
    const { container, root } = render(<InvitePage />)

    expect(container.textContent).toContain('邀请信息缺失')
    expect(container.textContent).toContain('缺少 邀请码')

    unmount(root, container)
  })
})
