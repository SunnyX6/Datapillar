import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('Workflow build performance', () => {
  test('workflow canvas Web Vitals', async ({ page }) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/workflow')

    await expect(page.getByRole('heading', { name: 'Waiting for tasks', level: 3 })).toBeVisible()

    const historyButton = page.getByRole('button', { name: 'History session' })
    await historyButton.click()
    await expect(page.getByText('Real-time data warehouse construction tasks')).toBeVisible()
    await historyButton.click()

    await page.getByRole('button', { name: 'new session' }).click()

    const input = page.getByPlaceholder('Describe your data workflow needs...')
    await input.fill('Build order details synchronization process')

    await page.waitForTimeout(300)

    const metrics = await collectWebVitals(page)

    expect(metrics.lcp).toBeGreaterThan(0)
    expect(metrics.lcp).toBeLessThanOrEqual(budgets.lcp)
    expect(metrics.cls).toBeLessThanOrEqual(budgets.cls)
    expect(metrics.inp).toBeLessThanOrEqual(budgets.inp)
  })
})
