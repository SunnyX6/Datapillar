import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import { mockKnowledgeGraphRoutes } from '../../shared/mockKnowledgeGraph'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('数据治理性能', () => {
  test('知识图谱 Web Vitals', async ({ page }) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await mockKnowledgeGraphRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/governance/knowledge')

    await page.getByText('加载知识图谱数据...').waitFor({ state: 'hidden' })

    await expect(page.getByText('Nodes:')).toBeVisible()

    const commandInput = page.getByPlaceholder('Ask AI to map lineage, fix metadata, or scan for anomalies...')
    await expect(commandInput).toBeVisible()
    await commandInput.fill('order lineage check')
    await commandInput.press('Enter')

    await expect(commandInput).toHaveValue('', { timeout: 10000 })
    await page.waitForTimeout(300)

    const metrics = await collectWebVitals(page)

    expect(metrics.lcp).toBeGreaterThan(0)
    expect(metrics.lcp).toBeLessThanOrEqual(budgets.lcp)
    expect(metrics.cls).toBeLessThanOrEqual(budgets.cls)
    expect(metrics.inp).toBeLessThanOrEqual(budgets.inp)
  })
})
