import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'

test.describe('IDE module', () => {
  test('enter SQL editor and select Catalog/Schema', async ({ page }) => {
    await login(page)
    await page.goto('/ide')

    await expect(page.getByText('One Ide Studio')).toBeVisible()

    await page.getByText('SQL Query', { exact: true }).click()
    await page.waitForURL('**/ide/sql')

    const pickerButton = page.getByRole('button', { name: 'Choose Catalog' })
    await pickerButton.click()

    const catalogColumn = page.getByText('Catalog', { exact: true }).locator('..').locator('..')
    const catalogList = catalogColumn.locator(':scope > div').nth(1)
    const firstCatalog = catalogList.locator(':scope > div').first()

    await expect(firstCatalog).toBeVisible()
    const catalogName = (await firstCatalog.locator('span').first().textContent())?.trim() ?? ''
    expect(catalogName).not.toBe('')
    await firstCatalog.hover()

    const schemaColumn = page.getByText('Schema', { exact: true }).locator('..').locator('..')
    const schemaList = schemaColumn.locator(':scope > div').nth(1)
    const firstSchema = schemaList.locator('button').first()

    await expect(firstSchema).toBeVisible()
    const schemaName = (await firstSchema.locator('span').first().textContent())?.trim() ?? ''
    expect(schemaName).not.toBe('')
    await firstSchema.click()

    await expect(page.getByRole('button', { name: `${catalogName}.${schemaName}` })).toBeVisible()
  })
})
