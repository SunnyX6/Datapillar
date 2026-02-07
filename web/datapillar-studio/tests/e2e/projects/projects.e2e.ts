import { expect, test } from '@playwright/test'
import { login } from '../../shared/auth'

test.describe('项目模块', () => {
  test('创建项目并进入离线数仓栈', async ({ page }) => {
    await login(page)
    await page.goto('/projects')

    await expect(page.getByText('项目概览')).toBeVisible()

    await page.getByRole('button', { name: '创建项目' }).click()
    await expect(page.getByRole('heading', { name: '创建新项目', level: 2 })).toBeVisible()

    const projectName = `E2E_Project_${Date.now()}`
    await page.getByPlaceholder('e.g. User_Behavior_Analytics_v2').fill(projectName)

    const createButton = page.getByRole('button', { name: '创建项目' }).last()
    await expect(createButton).toBeEnabled()
    await createButton.click()

    await expect(page.getByText(projectName)).toBeVisible()

    const projectCard = page.getByText(projectName).locator('xpath=ancestor::div[contains(@class, "rounded-2xl")]')
    await projectCard.getByText('离线数仓', { exact: true }).click()

    await expect(page.getByText(projectName)).toBeVisible()
    await expect(page.getByText('Overview')).toBeVisible()
  })

  test('任务管理画布居中且底部默认展开最大高度', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 })
    await login(page)
    await page.goto('/projects')

    await expect(page.getByText('项目概览')).toBeVisible()

    await page.getByRole('button', { name: '创建项目' }).click()
    await expect(page.getByRole('heading', { name: '创建新项目', level: 2 })).toBeVisible()

    const projectName = `E2E_Project_${Date.now()}`
    await page.getByPlaceholder('e.g. User_Behavior_Analytics_v2').fill(projectName)

    const createButton = page.getByRole('button', { name: '创建项目' }).last()
    await expect(createButton).toBeEnabled()
    await createButton.click()

    await expect(page.getByText(projectName)).toBeVisible()

    const projectCard = page.getByText(projectName).locator('xpath=ancestor::div[contains(@class, "rounded-2xl")]')
    await projectCard.getByText('离线数仓', { exact: true }).click()

    await expect(page.getByText('Overview')).toBeVisible()

    await page.getByText('Main_Nightly_ETL_Flow').click()
    await expect(page.getByText('Main_Nightly_ETL_Flow')).toBeVisible()

    const graphSvg = page.locator('svg').filter({ has: page.locator('animateMotion') }).first()
    await expect(graphSvg).toBeVisible()

    const canvasScroll = graphSvg.locator('xpath=ancestor::div[contains(@class, "overflow-auto")][1]')
    const scrollBox = await canvasScroll.boundingBox()
    const graphBox = await graphSvg.boundingBox()

    expect(scrollBox).not.toBeNull()
    expect(graphBox).not.toBeNull()

    if (!scrollBox || !graphBox) {
      throw new Error('画布容器定位失败')
    }

    expect(scrollBox.width).toBeGreaterThan(graphBox.width)
    const expectedLeft = scrollBox.x + (scrollBox.width - graphBox.width) / 2
    expect(Math.abs(graphBox.x - expectedLeft)).toBeLessThan(12)

    const bottomPanel = page
      .getByText('Pipeline Execution History')
      .locator('xpath=ancestor::div[contains(@style, "--bottom-panel-height")]')
    await expect(bottomPanel).toBeVisible()

    const mainPane = bottomPanel.locator('xpath=..')
    const bottomBox = await bottomPanel.boundingBox()
    const mainBox = await mainPane.boundingBox()

    expect(bottomBox).not.toBeNull()
    expect(mainBox).not.toBeNull()

    if (!bottomBox || !mainBox) {
      throw new Error('底部面板定位失败')
    }

    expect(bottomBox.height / mainBox.height).toBeGreaterThan(0.6)

    await page.getByText('ODS_Orders_Sync', { exact: true }).click()

    const panel = page.locator('aside').filter({ hasText: 'TaskRun' })
    await expect(panel).toBeVisible()
    const position = await panel.evaluate((node) => getComputedStyle(node).position)
    expect(position).toBe('fixed')
  })
})
