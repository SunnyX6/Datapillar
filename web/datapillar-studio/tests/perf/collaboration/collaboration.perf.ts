import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('协作模块性能', () => {
  test('协作页面 Web Vitals', async ({ page }) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/collaboration')

    await expect(page.getByText('协作空间 (COLLABORATION)')).toBeVisible()

    const searchInput = page.getByPlaceholder('搜索工单...')
    await searchInput.fill('T-1029')

    const ticketTitle = page.getByRole('heading', { name: 'ETL 变更：用户归因逻辑调整', level: 3 })
    await expect(ticketTitle).toBeVisible()
    await ticketTitle.click()

    const diffButton = page.getByRole('button', { name: '查看代码差异' })
    await diffButton.click()
    await page.getByRole('button', { name: '收起差异概览' }).click()

    const commentInput = page.getByPlaceholder('添加评论或备注...')
    await commentInput.fill('已完成前端校验')
    await page.getByRole('button', { name: '评论' }).click()

    await page.waitForTimeout(300)

    const metrics = await collectWebVitals(page)

    expect(metrics.lcp).toBeGreaterThan(0)
    expect(metrics.lcp).toBeLessThanOrEqual(budgets.lcp)
    expect(metrics.cls).toBeLessThanOrEqual(budgets.cls)
    expect(metrics.inp).toBeLessThanOrEqual(budgets.inp)
  })
})
