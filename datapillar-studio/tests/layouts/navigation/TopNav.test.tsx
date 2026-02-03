// @vitest-environment jsdom
import { describe, expect, it, vi, afterEach, beforeEach } from 'vitest'
import { act } from 'react-dom/test-utils'
import { createRoot } from 'react-dom/client'
import { TopNav } from '@/layouts/navigation/TopNav'
import type { Menu } from '@/types/auth'

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
  useLocation: () => ({ pathname: '/governance/metadata' })
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: {
      on: vi.fn(),
      off: vi.fn()
    }
  })
}))

vi.mock('@/stores', () => ({
  useI18nStore: (selector: (state: { language: string; setLanguage: () => void }) => unknown) =>
    selector({ language: 'en-US', setLanguage: vi.fn() }),
  useSearchStore: (
    selector: (state: {
      searchTerm: string
      setSearchTerm: () => void
      context: string
      setContext: () => void
      isOpen: boolean
      setIsOpen: () => void
      getContextConfig: () => { placeholder: string }
    }) => unknown
  ) =>
    selector({
      searchTerm: '',
      setSearchTerm: vi.fn(),
      context: 'default',
      setContext: vi.fn(),
      isOpen: false,
      setIsOpen: vi.fn(),
      getContextConfig: () => ({ placeholder: 'Search' })
    })
}))

const menus: Menu[] = [
  {
    id: 1,
    name: 'Home',
    path: '/home',
    location: 'TOP'
  },
  {
    id: 2,
    name: 'Governance',
    path: '/governance',
    location: 'TOP',
    children: [
      {
        id: 3,
        name: 'Metadata',
        path: '/governance/metadata',
        location: 'TOP'
      },
      {
        id: 4,
        name: 'Semantic',
        path: '/governance/semantic',
        location: 'TOP'
      }
    ]
  }
]

const user = {
  name: 'TestUser',
  role: 'admin',
  email: 'test@datapillar.ai'
}

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

const findButtonByText = (root: ParentNode, text: string) => {
  const buttons = Array.from(root.querySelectorAll('button'))
  return buttons.find((button) => button.textContent?.includes(text))
}

const findByText = (root: ParentNode, text: string) => {
  const nodes = Array.from(root.querySelectorAll('*'))
  return nodes.find((node) => node.textContent?.includes(text)) ?? null
}

const findPanelRoot = (node: HTMLElement | null) => {
  let current: HTMLElement | null = node
  while (current) {
    if (typeof current.className === 'string' && current.className.includes('fixed')) {
      return current
    }
    current = current.parentElement
  }
  return null
}

describe('TopNav', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('keeps governance dropdown open when moving from trigger to panel', () => {
    const { container, root } = render(
      <TopNav
        isDark={false}
        toggleTheme={vi.fn()}
        user={user}
        menus={menus}
        currentPath="/governance/metadata"
        onNavigate={vi.fn()}
        onLogout={vi.fn()}
        isSidebarCollapsed={false}
        onToggleSidebar={vi.fn()}
        onProfile={vi.fn()}
      />
    )

    const governanceButton = findButtonByText(container, 'top.tabs.governance')
    expect(governanceButton).toBeTruthy()

    const wrapper = governanceButton?.parentElement as HTMLElement
    act(() => {
      wrapper.dispatchEvent(new MouseEvent('mouseover', { bubbles: true, relatedTarget: null }))
    })

    const header = findByText(document.body, 'top.dropdown.governanceHeader') as HTMLElement | null
    expect(header).toBeTruthy()

    act(() => {
      wrapper.dispatchEvent(new MouseEvent('mouseout', { bubbles: true, relatedTarget: null }))
    })

    const panelRoot = findPanelRoot(header)
    expect(panelRoot).toBeTruthy()

    act(() => {
      panelRoot?.dispatchEvent(new MouseEvent('mouseover', { bubbles: true, relatedTarget: null }))
      vi.advanceTimersByTime(200)
    })

    expect(findByText(document.body, 'top.dropdown.governanceHeader')).toBeTruthy()

    act(() => {
      panelRoot?.dispatchEvent(new MouseEvent('mouseout', { bubbles: true, relatedTarget: null }))
      vi.advanceTimersByTime(200)
    })

    expect(findByText(document.body, 'top.dropdown.governanceHeader')).toBeNull()

    unmount(root, container)
  })
})
