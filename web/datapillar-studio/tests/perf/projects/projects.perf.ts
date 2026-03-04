import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('Project module performance', () => {
  test('Project overview Web Vitals', async ({ page }) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/projects')

    await expect(page.getByText('Project overview')).toBeVisible()

    await page.mouse.wheel(0, 500)

    await page.getByRole('button', { name: 'Create project' }).first().click()
    await expect(page.getByRole('heading', { name: 'Create new project', level: 2 })).toBeVisible()

    const projectName = `Perf_Project_${Date.now()}`
    await page.getByPlaceholder('e.g. User_Behavior_Analytics_v2').fill(projectName)
    await page.getByPlaceholder('Briefly describe the purpose of the project...').fill('Front-end performance self-test project')

    await page.getByRole('button', { name: /real-time streaming computing/ }).click()
    await page.getByRole('button', { name: /STAGING/ }).click()

    const createButton = page.getByRole('button', { name: 'Create project' }).last()
    await expect(createButton).toBeEnabled()
    await createButton.click()

    await expect(page.getByText(projectName)).toBeVisible()
    await page.waitForTimeout(300)

    const metrics = await collectWebVitals(page)

    expect(metrics.lcp).toBeGreaterThan(0)
    expect(metrics.lcp).toBeLessThanOrEqual(budgets.lcp)
    expect(metrics.cls).toBeLessThanOrEqual(budgets.cls)
    expect(metrics.inp).toBeLessThanOrEqual(budgets.inp)
  })
})
