import type { Page, Route } from '@playwright/test'

type ApiResponse<T> = {
  code: number
  data?: T
}

type MockLoginResult = {
  userId: number
  tenantId: number
  username: string
  email?: string
  roles: Array<{ id: number; name: string; type: string }>
  menus: Array<{ id: number; name: string; path: string }>
}

const buildApiResponse = <T,>(data: T): ApiResponse<T> => ({
  code: 0,
  data
})

const buildLoginResult = (username: string): MockLoginResult => ({
  userId: 10001,
  tenantId: 0,
  username,
  email: `${username}@datapillar.ai`,
  roles: [{ id: 1, name: '管理员', type: 'ADMIN' }],
  menus: []
})

export const mockAuthRoutes = async (page: Page) => {
  let lastUsername = process.env.PLAYWRIGHT_USERNAME ?? 'mock-user'

  const handler = async (route: Route) => {
    const request = route.request()
    const { pathname } = new URL(request.url())

    if (pathname.endsWith('/login') || pathname.endsWith('/login/sso')) {
      try {
        const payload = request.postDataJSON() as { loginAlias?: string }
        if (payload?.loginAlias) {
          lastUsername = payload.loginAlias
        }
      } catch {
        // 忽略解析失败，使用默认用户名
      }

      const user = buildLoginResult(lastUsername)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildApiResponse(user))
      })
      return
    }

    if (pathname.endsWith('/validate')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildApiResponse({
          remainingSeconds: 3600,
          username: lastUsername,
          userId: 10001,
          tenantId: 0
        }))
      })
      return
    }

    if (pathname.endsWith('/refresh')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0 })
      })
      return
    }

    if (pathname.endsWith('/logout')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0 })
      })
      return
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0 })
    })
  }

  await page.route('**/api/auth/**', handler)
  await page.route('**/api/login', handler)
  await page.route('**/api/login/**', handler)
}
