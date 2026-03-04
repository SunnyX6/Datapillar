import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'

test.describe('Collaboration module', () => {
  test('Filter tickets and create generic requests', async ({ page }) => {
    await login(page)
    await page.goto('/collaboration')

    await expect(page.getByText('collaborative space (COLLABORATION)')).toBeVisible()

    const searchInput = page.getByPlaceholder('Search tickets...')
    await searchInput.fill('T-1029')
    await expect(page.getByRole('heading', { name: 'ETL change：Adjustment of user attribution logic', level: 3 })).toBeVisible()

    await searchInput.fill('')

    await page.getByRole('button', { name: 'Initiate a collaboration request' }).click()
    await expect(page.getByText('Initiate a new collaboration request')).toBeVisible()

    await page.getByText('More', { exact: true }).click()
    await page.getByText('Service release', { exact: true }).click()
    await expect(page.getByText('Application details：Service release')).toBeVisible()

    await page.getByPlaceholder('Please enter the assets that require collaboration/service').fill('api.customer_profile')
    await page.getByPlaceholder('Please describe the changes/publish/Problem details').fill('Publish customer portraits externally API')

    const submitButton = page.getByRole('button', { name: 'Submit application' })
    await expect(submitButton).toBeEnabled()
    await submitButton.click()

    await expect(page.getByRole('heading', { name: 'Service release: api.customer_profile', level: 1 })).toBeVisible()
  })
})
