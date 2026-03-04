import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('Collaboration module performance', () => {
  test('Collaboration page Web Vitals', async ({ page }) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/collaboration')

    await expect(page.getByText('collaborative space (COLLABORATION)')).toBeVisible()

    const searchInput = page.getByPlaceholder('Search tickets...')
    await searchInput.fill('T-1029')

    const ticketTitle = page.getByRole('heading', { name: 'ETL change：Adjustment of user attribution logic', level: 3 })
    await expect(ticketTitle).toBeVisible()
    await ticketTitle.click()

    const diffButton = page.getByRole('button', { name: 'View code differences' })
    await diffButton.click()
    await page.getByRole('button', { name: 'Collapse difference overview' }).click()

    const commentInput = page.getByPlaceholder('Add a comment or note...')
    await commentInput.fill('Front-end verification completed')
    await page.getByRole('button', { name: 'Comment' }).click()

    await page.waitForTimeout(300)

    const metrics = await collectWebVitals(page)

    expect(metrics.lcp).toBeGreaterThan(0)
    expect(metrics.lcp).toBeLessThanOrEqual(budgets.lcp)
    expect(metrics.cls).toBeLessThanOrEqual(budgets.cls)
    expect(metrics.inp).toBeLessThanOrEqual(budgets.inp)
  })
})
