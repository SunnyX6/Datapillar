import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'

test.describe('协作模块', () => {
  test('筛选工单并创建通用请求', async ({ page }) => {
    await login(page)
    await page.goto('/collaboration')

    await expect(page.getByText('协作空间 (COLLABORATION)')).toBeVisible()

    const searchInput = page.getByPlaceholder('搜索工单...')
    await searchInput.fill('T-1029')
    await expect(page.getByRole('heading', { name: 'ETL 变更：用户归因逻辑调整', level: 3 })).toBeVisible()

    await searchInput.fill('')

    await page.getByRole('button', { name: '发起协作请求' }).click()
    await expect(page.getByText('发起新的协作请求')).toBeVisible()

    await page.getByText('更多', { exact: true }).click()
    await page.getByText('服务发布', { exact: true }).click()
    await expect(page.getByText('申请详情：服务发布')).toBeVisible()

    await page.getByPlaceholder('请输入需要协作的资产/服务').fill('api.customer_profile')
    await page.getByPlaceholder('请描述变更/发布/问题详情').fill('对外发布客户画像 API')

    const submitButton = page.getByRole('button', { name: '提交申请' })
    await expect(submitButton).toBeEnabled()
    await submitButton.click()

    await expect(page.getByRole('heading', { name: '服务发布: api.customer_profile', level: 1 })).toBeVisible()
  })
})
