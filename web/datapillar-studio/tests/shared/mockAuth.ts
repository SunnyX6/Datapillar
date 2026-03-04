import type { Page, Route } from '@playwright/test'

type ApiResponse<T> = {
  code: number
  data?: T
}

type MockLoginResult = {
  userId: number
  username: string
  email?: string
  tenants: Array<{
    tenantId: number
    tenantCode: string
    tenantName: string
    status: number
    isDefault: number
  }>
}

const buildApiResponse = <T,>(data: T): ApiResponse<T> => ({
  code: 0,
  data
})

const buildLoginResult = (username: string): MockLoginResult => ({
  userId: 10001,
  username,
  email: `${username}@datapillar.ai`,
  tenants: [
    {
      tenantId: 0,
      tenantCode: 'tenant-default',
      tenantName: 'Default tenant',
      status: 1,
      isDefault: 1
    }
  ]
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
        // Ignore parsing failures，Use default username
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

    if (pathname.endsWith('/users/me/menu')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildApiResponse([]))
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
  await page.route('**/api/studio/**', handler)
}
