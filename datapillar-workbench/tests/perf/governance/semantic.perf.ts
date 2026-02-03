import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import { mockOneMetaRoutes } from '../../shared/mockOneMeta'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('数据治理性能', () => {
  test('元语义 Web Vitals', async ({ page }) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await mockOneMetaRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/governance/semantic')

    await expect(page.getByRole('heading', { name: 'One Meta Semantic' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '指标中心' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '规范词根' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '标准规范' })).toBeVisible()

    await expect(page.getByRole('button', { name: '数据类型' })).toBeVisible()
    await expect(page.getByRole('button', { name: '值域' })).toBeVisible()
    await expect(page.getByRole('button', { name: '分级分类' })).toBeVisible()

    const ctaButton = page.getByRole('button', { name: '立即开启语义增强' })
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
