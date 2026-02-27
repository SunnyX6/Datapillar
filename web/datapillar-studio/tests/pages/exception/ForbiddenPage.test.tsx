// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { ForbiddenPage } from '@/pages/exception'

interface RenderResult {
  container: HTMLDivElement
  root: Root
  router: ReturnType<typeof createMemoryRouter>
}

function renderWithRouter(
  initialEntries: string[],
  initialIndex: number
): RenderResult {
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: <div>home-page</div>,
      },
      {
        path: '/login',
        element: <div>login-page</div>,
      },
      {
        path: '/source',
        element: <div>source-page</div>,
      },
      {
        path: '/403',
        element: <ForbiddenPage />,
      },
    ],
    {
      initialEntries,
      initialIndex,
    }
  )

  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)

  act(() => {
    root.render(<RouterProvider router={router} />)
  })

  return { container, root, router }
}

function cleanup(root: Root, container: HTMLDivElement) {
  act(() => {
    root.unmount()
  })
  container.remove()
}

describe('ForbiddenPage', () => {
  beforeEach(() => {
    ;(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  })

  it('点击返回上一页后回到来源页面', async () => {
    const { container, root } = renderWithRouter(['/source', '/403?from=%2Fsource'], 1)
    const backButton = container.querySelector('[data-testid="forbidden-back-button"]') as HTMLButtonElement | null

    await act(async () => {
      backButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await Promise.resolve()
    })

    expect(container.textContent).toContain('source-page')
    cleanup(root, container)
  })

  it('点击回到首页后跳转到首页', async () => {
    const { container, root } = renderWithRouter(['/source', '/403?from=%2Fsource'], 1)
    const homeButton = container.querySelector('[data-testid="forbidden-home-button"]') as HTMLButtonElement | null

    await act(async () => {
      homeButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await Promise.resolve()
    })

    expect(container.textContent).toContain('home-page')
    cleanup(root, container)
  })

  it('无可访问入口场景点击返回按钮应跳转登录页', async () => {
    const { container, root } = renderWithRouter(
      [
        '/403?reason=no-accessible-entry&from=%2Flogin',
      ],
      0
    )
    const backButton = container.querySelector('[data-testid="forbidden-back-button"]') as HTMLButtonElement | null

    await act(async () => {
      backButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await Promise.resolve()
    })

    expect(container.textContent).toContain('login-page')
    cleanup(root, container)
  })
})
