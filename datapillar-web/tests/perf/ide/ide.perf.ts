import { writeFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'
import { getPerfBudgets } from '../../shared/env'
import { mockAuthRoutes } from '../../shared/mockAuth'
import { MOCK_CATALOG_NAME, MOCK_SCHEMA_NAME, mockOneMetaRoutes } from '../../shared/mockOneMeta'
import { collectWebVitals, initWebVitals } from '../../shared/webVitals'

test.describe('IDE 模块性能', () => {
  test('SQL 编辑器 Web Vitals', async ({ page }, testInfo) => {
    const budgets = getPerfBudgets()

    await mockAuthRoutes(page)
    await mockOneMetaRoutes(page)
    await login(page)
    await initWebVitals(page)
    await page.goto('/ide/sql')

    const pickerButton = page.getByRole('button', { name: '选择 Catalog' })
    await expect(pickerButton).toBeVisible()

    await pickerButton.click()

    const catalogColumn = page.getByText('Catalog', { exact: true }).locator('..').locator('..')
    const catalogList = catalogColumn.locator(':scope > div').nth(1)
    const firstCatalog = catalogList.locator(':scope > div').first()

    await expect(firstCatalog).toBeVisible()
    await firstCatalog.hover()

    const schemaColumn = page.getByText('Schema', { exact: true }).locator('..').locator('..')
    const schemaList = schemaColumn.locator(':scope > div').nth(1)
    const firstSchema = schemaList.locator('button').first()

    await expect(firstSchema).toBeVisible()
    await firstSchema.click()

    await expect(page.getByRole('button', { name: `${MOCK_CATALOG_NAME}.${MOCK_SCHEMA_NAME}` })).toBeVisible()

    await page.locator('.monaco-editor').click()
    await page.keyboard.type('select 1;')

    const expandBottomPanel = page.getByRole('button', { name: '展开底部面板' })
    const collapseBottomPanel = page.getByRole('button', { name: '收起底部面板' })
    await expect(expandBottomPanel.or(collapseBottomPanel)).toBeVisible()
    if (await expandBottomPanel.isVisible()) {
      await expandBottomPanel.click()
      await expect(collapseBottomPanel).toBeVisible()
    }

    await page.getByRole('button', { name: 'Messages' }).click()
    await page.getByRole('button', { name: 'Results' }).click()

    await page.waitForTimeout(300)

    const metrics = await collectWebVitals(page)

    if (metrics.cls > budgets.cls) {
      const report = {
        lcp: metrics.lcp,
        cls: metrics.cls,
        inp: metrics.inp,
        layoutShifts: metrics.clsEntries
      }
      const reportPath = testInfo.outputPath('cls-report.json')
      await writeFile(reportPath, JSON.stringify(report, null, 2), 'utf-8')
      await testInfo.attach('CLS诊断', {
        contentType: 'application/json',
        path: reportPath
      })
    }

    expect(metrics.lcp).toBeGreaterThan(0)
    expect(metrics.lcp).toBeLessThanOrEqual(budgets.lcp)
    expect(metrics.cls).toBeLessThanOrEqual(budgets.cls)
    expect(metrics.inp).toBeLessThanOrEqual(budgets.inp)
  })
})
