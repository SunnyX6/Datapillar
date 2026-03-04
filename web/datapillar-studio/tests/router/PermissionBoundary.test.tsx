// @vitest-environment jsdom
import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router-dom'
import { PermissionBoundary } from '@/router/guards/PermissionBoundary'
import { useAuthStore } from '@/state'
import type { Menu, User } from '@/services/types/auth'
import { resetLastAllowedRoute } from '@/router/access/routeSource'

interface RenderResult {
  container: HTMLDivElement
  root: Root
}

function buildUser(menus: Menu[]): User {
  return {
    userId: 1,
    username: 'sunny',
    roles: [],
    menus,
  }
}

function ForbiddenStateProbe() {
  const location = useLocation()
  return (
    <div>
      <p data-testid="forbidden-path">{location.pathname}</p>
      <p data-testid="forbidden-search">{location.search}</p>
    </div>
  )
}

function renderWithEntry(initialEntry: string): RenderResult {
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: <PermissionBoundary />,
        children: [
          {
            path: 'home',
            element: <div>home-page</div>,
          },
          {
            path: 'projects',
            element: <div>projects-page</div>,
          },
        ],
      },
      {
        path: '/403',
        element: <ForbiddenStateProbe />,
      },
    ],
    {
      initialEntries: [initialEntry],
    }
  )

  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)

  act(() => {
    root.render(<RouterProvider router={router} />)
  })

  return { container, root }
}

function cleanup(root: Root, container: HTMLDivElement) {
  act(() => {
    root.unmount()
  })
  container.remove()
}

describe('PermissionBoundary', () => {
  beforeEach(() => {
    ;(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    localStorage.clear()
    resetLastAllowedRoute()
    useAuthStore.setState({
      loading: false,
      isAuthenticated: true,
      user: buildUser([]),
    })
  })

  afterEach(() => {
    resetLastAllowedRoute()
    useAuthStore.setState({
      loading: false,
      isAuthenticated: false,
      user: null,
    })
  })

  it('Should jump when there is no permission /403 and carry restricted paths', async () => {
    const { container, root } = renderWithEntry('/projects')

    await act(async () => {
      await Promise.resolve()
    })

    const forbiddenPath = container.querySelector('[data-testid="forbidden-path"]')?.textContent
    const forbiddenSearch = container.querySelector('[data-testid="forbidden-search"]')?.textContent ?? ''

    expect(forbiddenPath).toBe('/403')
    expect(forbiddenSearch).toContain('reason=permission-denied')
    expect(forbiddenSearch).toContain('from=%2Flogin')
    expect(forbiddenSearch).toContain('denied=%2Fprojects')

    cleanup(root, container)
  })

  it('The target page should continue to be rendered when permission is granted', async () => {
    useAuthStore.setState({
      user: buildUser([
        {
          id: 2,
          name: 'Project',
          path: '/projects',
          location: 'TOP',
          permissionCode: 'READ',
        },
      ]),
    })

    const { container, root } = renderWithEntry('/projects')

    await act(async () => {
      await Promise.resolve()
    })

    expect(container.textContent).toContain('projects-page')
    expect(container.textContent).not.toContain('/403')

    cleanup(root, container)
  })

  it('When the target page does not have permission but there is an accessible entry, it should automatically fall back to the first accessible page.', async () => {
    useAuthStore.setState({
      user: buildUser([
        {
          id: 1,
          name: 'Home page',
          path: '/home',
          location: 'TOP',
          permissionCode: 'READ',
        },
      ]),
    })

    const { container, root } = renderWithEntry('/projects')

    await act(async () => {
      await Promise.resolve()
    })

    expect(container.textContent).toContain('home-page')
    expect(container.textContent).not.toContain('/403')

    cleanup(root, container)
  })
})
