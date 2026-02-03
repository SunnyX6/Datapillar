import type { Page } from '@playwright/test'
import { expect } from '@playwright/test'
import { getRequiredEnv } from './env'

export const login = async (page: Page) => {
  const username = getRequiredEnv('PLAYWRIGHT_USERNAME')
  const password = getRequiredEnv('PLAYWRIGHT_PASSWORD')

  await page.goto('/')
  await expect(page.locator('form')).toBeVisible()

  const usernameInput = page.locator('input[type="text"]').first()
  const passwordInput = page.locator('input[type="password"]').first()

  await usernameInput.fill(username)
  await passwordInput.fill(password)

  const submitButton = page.locator('form button[type="submit"]')
  await submitButton.click()

  const workspaceTitle = page.getByText('选择工作空间')
  const result = await Promise.race([
    page.waitForURL('**/home', { timeout: 15000 }).then(() => 'home').catch(() => null),
    workspaceTitle.waitFor({ timeout: 15000 }).then(() => 'select').catch(() => null)
  ])

  if (!result) {
    throw new Error('登录未完成，未跳转主页且未出现租户选择')
  }

  if (result === 'select') {
    const firstWorkspace = page.locator('[data-testid^="workspace-select-item-"]').first()
    await firstWorkspace.click()
    await page.waitForURL('**/home', { timeout: 15000 })
  }
}
