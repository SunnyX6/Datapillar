import type { Page } from '@playwright/test'

type ApiResponse<T> = {
  status: number
  code: string
  message: string
  data: T
  timestamp: string
}

type MockUser = {
  userId: number
  username: string
  email?: string
  roles: string[]
  permissions: string[]
  menus: Array<{ id: number; name: string; path: string }>
}

const buildApiResponse = <T,>(data: T): ApiResponse<T> => ({
  status: 200,
  code: 'OK',
  message: '成功',
  data,
  timestamp: new Date().toISOString()
})

const buildMockUser = (username: string): MockUser => ({
  userId: 10001,
  username,
  email: `${username}@datapillar.ai`,
  roles: ['admin'],
  permissions: [],
  menus: []
})

export const mockAuthRoutes = async (page: Page) => {
  let lastUsername = process.env.PLAYWRIGHT_USERNAME ?? 'mock-user'

  await page.route('**/api/auth/**', async (route) => {
    const request = route.request()
    const { pathname } = new URL(request.url())

    if (pathname.endsWith('/login')) {
      try {
        const payload = request.postDataJSON() as { username?: string }
        if (payload?.username) {
          lastUsername = payload.username
        }
      } catch {
        // 忽略解析失败，使用默认用户名
      }

      const user = buildMockUser(lastUsername)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildApiResponse(user))
      })
      return
    }

    if (pathname.endsWith('/token-info')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildApiResponse({
          valid: true,
          remainingSeconds: 3600,
          username: lastUsername,
          userId: 10001
        }))
      })
      return
    }

    if (pathname.endsWith('/refresh')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildApiResponse(null))
      })
      return
    }

    if (pathname.endsWith('/logout')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildApiResponse(null))
      })
      return
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildApiResponse(null))
    })
  })
}
