import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('工作流构建性能', () => {
  test('工作流画布 Web Vitals', async ({ page }) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/workflow')

    await expect(page.getByRole('heading', { name: '等待任务', level: 3 })).toBeVisible()

    const historyButton = page.getByRole('button', { name: '历史会话' })
    await historyButton.click()
    await expect(page.getByText('实时数仓构建任务')).toBeVisible()
    await historyButton.click()

    await page.getByRole('button', { name: '新会话' }).click()

    const input = page.getByPlaceholder('描述你的数据工作流需求...')
    await input.fill('构建订单明细同步流程')

    await page.waitForTimeout(300)

    const metrics = await collectWebVitals(page)

    expect(metrics.lcp).toBeGreaterThan(0)
    expect(metrics.lcp).toBeLessThanOrEqual(budgets.lcp)
    expect(metrics.cls).toBeLessThanOrEqual(budgets.cls)
    expect(metrics.inp).toBeLessThanOrEqual(budgets.inp)
  })
})
