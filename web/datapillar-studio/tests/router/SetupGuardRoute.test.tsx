// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { SetupGuard } from '@/router/guards/SetupGuard'
import { useSetupStore } from '@/stores'
import { getSetupStatus } from '@/services/setupService'

vi.mock('@/services/setupService', () => ({
  getSetupStatus: vi.fn()
}))

const mockedGetSetupStatus = vi.mocked(getSetupStatus)

interface RenderResult {
  container: HTMLDivElement
  root: Root
  router: ReturnType<typeof createMemoryRouter>
}

const renderWithRouter = (initialEntry: string): RenderResult => {
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: <SetupGuard />,
        children: [
          {
            path: 'home',
            element: <div>home-page</div>
          },
          {
            path: 'projects',
            element: <div>projects-page</div>
          },
          {
            path: 'setup',
            element: <div>setup-page</div>
          },
          {
            path: 'login',
            element: <div>login-page</div>
          },
          {
            path: 'invite',
            element: <div>invite-page</div>
          },
          {
            path: '500',
            element: <div>server-error-page</div>
          }
        ]
      }
    ],
    {
      initialEntries: [initialEntry]
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

const flushEffects = async () => {
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
  })
}

const cleanup = (root: Root, container: HTMLDivElement) => {
  act(() => {
    root.unmount()
  })
  container.remove()
}

describe('SetupGuard', () => {
  beforeEach(() => {
    ;(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    localStorage.clear()
    vi.clearAllMocks()
    useSetupStore.getState().resetSetupStatus()
    mockedGetSetupStatus.mockResolvedValue({
      schemaReady: true,
      initialized: true,
      currentStep: 'COMPLETED',
      steps: []
    })
  })

  afterEach(() => {
    useSetupStore.getState().resetSetupStatus()
  })

  it('页面切换时不重复请求 setup 状态', async () => {
    const { container, root, router } = renderWithRouter('/home')

    await flushEffects()
    expect(container.textContent).toContain('home-page')
    expect(mockedGetSetupStatus).toHaveBeenCalledTimes(1)

    await act(async () => {
      await router.navigate('/projects')
    })
    await flushEffects()

    expect(container.textContent).toContain('projects-page')
    expect(mockedGetSetupStatus).toHaveBeenCalledTimes(1)

    cleanup(root, container)
  })

})
