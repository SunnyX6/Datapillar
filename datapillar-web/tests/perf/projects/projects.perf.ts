import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('项目模块性能', () => {
  test('项目概览 Web Vitals', async ({ page }) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/projects')

    await expect(page.getByText('项目概览')).toBeVisible()

    await page.mouse.wheel(0, 500)

    await page.getByRole('button', { name: '创建项目' }).first().click()
    await expect(page.getByRole('heading', { name: '创建新项目', level: 2 })).toBeVisible()

    const projectName = `Perf_Project_${Date.now()}`
    await page.getByPlaceholder('e.g. User_Behavior_Analytics_v2').fill(projectName)
    await page.getByPlaceholder('简要描述该项目的用途...').fill('前端性能自测项目')

    await page.getByRole('button', { name: /实时流计算/ }).click()
    await page.getByRole('button', { name: /STAGING/ }).click()

    const createButton = page.getByRole('button', { name: '创建项目' }).last()
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
