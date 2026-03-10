// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { flushSync } from 'react-dom'
import { ExpandToggle } from '@/layouts/navigation/ExpandToggle'

const render = (ui: JSX.Element) => {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  flushSync(() => {
    root.render(ui)
  })
  return { container, root }
}

const unmount = (root: ReturnType<typeof createRoot>, container: HTMLDivElement) => {
  flushSync(() => {
    root.unmount()
  })
  container.remove()
}

describe('ExpandToggle', () => {
  it('renders sidebar collapse button without native tooltip title', () => {
    const onToggle = vi.fn()
    const { container, root } = render(<ExpandToggle variant="sidebar" onToggle={onToggle} />)

    const button = container.querySelector('button[aria-label="Collapse navigation bar"]') as HTMLButtonElement | null
    expect(button).toBeTruthy()
    expect(button?.getAttribute('title')).toBeNull()

    button?.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    expect(onToggle).toHaveBeenCalledTimes(1)
    unmount(root, container)
  })

  it('renders top navigation expand button without native tooltip title', () => {
    const { container, root } = render(<ExpandToggle variant="topnav" onToggle={vi.fn()} />)

    const button = container.querySelector('button[aria-label="Expand navigation bar"]') as HTMLButtonElement | null
    expect(button).toBeTruthy()
    expect(button?.getAttribute('title')).toBeNull()

    unmount(root, container)
  })
})
