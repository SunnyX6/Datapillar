import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import { mockOneMetaRoutes } from '../../shared/mockOneMeta'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('Data governance performance', () => {
  test('Metasemantics Web Vitals', async ({ page }) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await mockOneMetaRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/governance/semantic')

    await expect(page.getByRole('heading', { name: 'One Meta Semantic' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'indicator center' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'canonical root' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Standard specifications' })).toBeVisible()

    await expect(page.getByRole('button', { name: 'data type' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'range' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Classification' })).toBeVisible()

    const ctaButton = page.getByRole('button', { name: 'Turn on semantic enhancement now' })
    await expect(ctaButton).toBeVisible()
    await ctaButton.click()

    await page.mouse.wheel(0, 300)
    await page.waitForTimeout(300)

    const metrics = await collectWebVitals(page)

    expect(metrics.lcp).toBeGreaterThan(0)
    expect(metrics.lcp).toBeLessThanOrEqual(budgets.lcp)
    expect(metrics.cls).toBeLessThanOrEqual(budgets.cls)
    expect(metrics.inp).toBeLessThanOrEqual(budgets.inp)
  })
})
