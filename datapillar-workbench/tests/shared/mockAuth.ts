import type { Page } from '@playwright/test'

type ApiResponse<T> = {
  status: number
  code: string
  message: string
  data: T
  timestamp: string
}

type MockLoginResult = {
  loginStage: 'SUCCESS'
  userId: number
  tenantId: number
  username: string
  email?: string
  roles: Array<{ id: number; name: string; type: string }>
  menus: Array<{ id: number; name: string; path: string }>
}

const buildApiResponse = <T,>(data: T): ApiResponse<T> => ({
  status: 200,
  code: 'OK',
  message: '成功',
  data,
  timestamp: new Date().toISOString()
})

const buildLoginResult = (username: string): MockLoginResult => ({
  loginStage: 'SUCCESS',
  userId: 10001,
  tenantId: 0,
  username,
  email: `${username}@datapillar.ai`,
  roles: [{ id: 1, name: '管理员', type: 'ADMIN' }],
  menus: []
})

export const mockAuthRoutes = async (page: Page) => {
  let lastUsername = process.env.PLAYWRIGHT_USERNAME ?? 'mock-user'

  await page.route('**/api/auth/**', async (route) => {
    const request = route.request()
    const { pathname } = new URL(request.url())

    if (pathname.endsWith('/login') || pathname.endsWith('/login/tenant') || pathname.endsWith('/sso/login')) {
      try {
        const payload = request.postDataJSON() as { username?: string }
        if (payload?.username) {
          lastUsername = payload.username
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

    if (pathname.endsWith('/token-info')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildApiResponse({
          valid: true,
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
