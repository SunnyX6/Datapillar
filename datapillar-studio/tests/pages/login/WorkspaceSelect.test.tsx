// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { act } from 'react-dom/test-utils'
import { createRoot } from 'react-dom/client'
import { WorkspaceSelectPanel } from '@/pages/login/WorkspaceSelect'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key
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

describe('WorkspaceSelectPanel', () => {
  it('展示工作空间列表并支持选择', () => {
    const onSelect = vi.fn()
    const onBack = vi.fn()
    const tenants = [
      {
        tenantId: 1,
        tenantCode: 'platform',
        tenantName: '平台租户',
        status: 1,
        isDefault: 1
      },
      {
        tenantId: 2,
        tenantCode: 'acme',
        tenantName: 'ACME',
        status: 1,
        isDefault: 0
      }
    ]
    const { container, root } = render(<WorkspaceSelectPanel tenants={tenants} onBack={onBack} onSelect={onSelect} />)

    expect(container.textContent).toContain('选择工作空间')
    expect(container.textContent).toContain('平台租户')
    expect(container.textContent).toContain('ACME')

    const backButton = container.querySelector('[data-testid="workspace-select-back"]') as HTMLButtonElement | null
    const workspaceButton = container.querySelector('[data-testid="workspace-select-item-1"]') as HTMLButtonElement | null

    act(() => {
      backButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      workspaceButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(onBack).toHaveBeenCalledTimes(1)
    expect(onSelect).toHaveBeenCalledTimes(1)
    expect(onSelect).toHaveBeenCalledWith(1)

    unmount(root, container)
  })
})
