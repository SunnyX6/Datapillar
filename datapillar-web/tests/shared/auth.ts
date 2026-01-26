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
  await Promise.all([
    page.waitForURL('**/home', { timeout: 15000 }),
    submitButton.click()
  ])
}
