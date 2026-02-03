import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import {
  MOCK_CATALOG_NAME,
  MOCK_SCHEMA_NAME,
  MOCK_TABLE_NAME,
  mockOneMetaRoutes
} from '../../shared/mockOneMeta'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('数据治理性能', () => {
  test('元数据中心 Web Vitals', async ({ page }) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await mockOneMetaRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/governance/metadata')

    const expandButton = page.getByRole('button', { name: '展开元数据侧栏' })
    const collapseButton = page.getByRole('button', { name: '收起元数据侧栏' })
    await page.waitForSelector(
      'button[aria-label="展开元数据侧栏"], button[aria-label="收起元数据侧栏"]',
      { state: 'attached' }
    )
    if (await expandButton.isVisible()) {
      await expandButton.click()
      await expect(collapseButton).toBeVisible()
    } else {
      await expect(collapseButton).toBeVisible()
    }

    const sidebar = page.locator('aside').filter({ has: collapseButton })
    await expect(sidebar).toBeVisible()
    await expect(sidebar.getByText('元数据中心')).toBeVisible()

    const catalogLabel = sidebar.getByText(MOCK_CATALOG_NAME, { exact: true }).first()
    await expect(catalogLabel).toBeVisible()
    const catalogRow = catalogLabel.locator('..')
    await catalogRow.locator(':scope > div').first().click()

    const schemaLabel = sidebar.getByText(MOCK_SCHEMA_NAME, { exact: true }).first()
    await expect(schemaLabel).toBeVisible()
    const schemaRow = schemaLabel.locator('..')
    await schemaRow.locator(':scope > div').first().click()

    const tableLabel = sidebar.getByText(MOCK_TABLE_NAME, { exact: true }).first()
    await expect(tableLabel).toBeVisible()
    await tableLabel.click()

    await expect(page.getByRole('heading', { name: MOCK_TABLE_NAME, level: 2 })).toBeVisible()

    await page.getByRole('button', { name: 'Columns' }).click()
    await expect(page.getByText('Column Name')).toBeVisible()

    await page.getByRole('button', { name: 'Quality' }).click()
    await page.mouse.wheel(0, 600)
    await page.waitForTimeout(300)

    const metrics = await collectWebVitals(page)

    expect(metrics.lcp).toBeGreaterThan(0)
    expect(metrics.lcp).toBeLessThanOrEqual(budgets.lcp)
    expect(metrics.cls).toBeLessThanOrEqual(budgets.cls)
    expect(metrics.inp).toBeLessThanOrEqual(budgets.inp)
  })
})
