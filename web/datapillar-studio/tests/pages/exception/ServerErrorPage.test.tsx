// @vitest-environment jsdom
import { describe, expect, it, vi, afterEach } from 'vitest'
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { ServerErrorPage } from '@/pages/exception'
import { getStudioServiceHealth } from '@/services/healthService'

vi.mock('@/api/errorCenter', () => ({
  getLastFatalError: vi.fn(() => null)
}))

vi.mock('@/services/healthService', () => ({
  getStudioServiceHealth: vi.fn()
}))

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

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

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('ServerErrorPage', () => {
  it('Initially red service status button', () => {
    const { container, root } = render(<ServerErrorPage />)
    const statusButton = container.querySelector('[data-testid="server-health-button"]') as HTMLButtonElement | null

    expect(statusButton?.textContent).toContain('Check service status')
    expect(statusButton?.className).toContain('text-rose-600')

    unmount(root, container)
  })

  it('After clicking, the health interface returns UP，Button switches to green', async () => {
    const healthMock = vi.mocked(getStudioServiceHealth)
    healthMock.mockResolvedValue({ status: 'UP' })

    const { container, root } = render(<ServerErrorPage />)
    const statusButton = container.querySelector('[data-testid="server-health-button"]') as HTMLButtonElement | null

    await act(async () => {
      statusButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(healthMock).toHaveBeenCalledTimes(1)
    expect(statusButton?.textContent).toContain('Serve health')
    expect(statusButton?.className).toContain('text-emerald-700')

    unmount(root, container)
  })

  it('Health interface exception after clicking，The button remains red abnormally', async () => {
    const healthMock = vi.mocked(getStudioServiceHealth)
    healthMock.mockResolvedValue({ status: 'DOWN' })

    const { container, root } = render(<ServerErrorPage />)
    const statusButton = container.querySelector('[data-testid="server-health-button"]') as HTMLButtonElement | null

    await act(async () => {
      statusButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(statusButton?.textContent).toContain('Service exception')
    expect(statusButton?.className).toContain('text-rose-600')

    unmount(root, container)
  })
})
