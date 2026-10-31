import { expect, test } from '@playwright/test'

test('编辑影厅添加多条过道，保存后预览、重开和移除保持一致', async ({ page }) => {
  let created: { seatLayout: string } | undefined
  const hall = { id: 1, cinemaId: 1, cinemaName: '测试影院', name: '过道测试厅', totalRows: 2, totalCols: 6,
    seatLayout: '[[1,1,1,1,1,1],[1,1,1,1,1,1]]', status: 1 }
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-admin-token')
    localStorage.setItem('userInfo', JSON.stringify({ id: 1, username: 'admin', role: 'ADMIN' }))
  })
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = []
    if (path === '/api/hall/page') data = { records: [hall], total: 1 }
    if (path === '/api/cinema/all') data = [{ id: 1, name: '测试影院' }]
    if (path === '/api/hall/1' && route.request().method() === 'PUT') {
      Object.assign(hall, route.request().postDataJSON())
      data = null
    }
    if (path === '/api/hall' && route.request().method() === 'POST') {
      created = route.request().postDataJSON()
      data = null
    }
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
  })
  await page.goto('/hall')
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '编辑影厅' })
  await expect(dialog.locator('.hall-aisle')).toHaveCount(2)
  const select = dialog.locator('.el-form-item').filter({ hasText: '添加过道' }).locator('.el-select')
  await select.click()
  await page.getByRole('option', { name: '第 3 列后', exact: true }).click()
  await page.getByRole('option', { name: '第 2 列后', exact: true }).click()
  await page.getByRole('option', { name: '第 4 列后', exact: true }).click()
  await page.getByRole('option', { name: '最左侧', exact: true }).click()
  await page.getByRole('option', { name: '最右侧', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(dialog.locator('.hall-aisle')).toHaveCount(8)
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(JSON.parse(hall.seatLayout).aisleAfterCols).toEqual([0, 2, 4, 6])
  expect(JSON.parse(hall.seatLayout).seats.flat()).toHaveLength(12)
  await page.getByRole('button', { name: '预览', exact: true }).click()
  const preview = page.getByRole('dialog', { name: '座位布局预览' })
  await expect(preview.locator('.hall-aisle')).toHaveCount(8)
  await expect(preview.locator('[title="最左侧过道"]')).toHaveCount(2)
  await expect(preview.locator('[title="最右侧过道"]')).toHaveCount(2)
  await page.screenshot({ path: test.info().outputPath('hall-aisles.png'), fullPage: true })
  await preview.getByRole('button', { name: 'Close this dialog' }).click()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await expect(dialog.locator('.hall-aisle')).toHaveCount(8)
  await select.click()
  await page.getByRole('option', { name: '第 2 列后', exact: true }).click()
  await page.getByRole('option', { name: '第 4 列后', exact: true }).click()
  await page.getByRole('option', { name: '最左侧', exact: true }).click()
  await page.getByRole('option', { name: '最右侧', exact: true }).click()
  await page.keyboard.press('Escape')
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(JSON.parse(hall.seatLayout).aisleAfterCols).toEqual([])
  await page.getByRole('button', { name: '新增影厅', exact: true }).click()
  const add = page.getByRole('dialog', { name: '新增影厅' })
  await expect(add.locator('.hall-aisle')).toHaveCount(0)
  await add.locator('.el-form-item').filter({ hasText: '所属影院' }).locator('.el-select').click()
  await page.getByRole('option', { name: '测试影院', exact: true }).click()
  await add.getByPlaceholder('请输入影厅名称，如 1号厅、IMAX厅').fill('新增带过道厅')
  await add.locator('.el-form-item').filter({ hasText: '添加过道' }).locator('.el-select').click()
  await page.getByRole('option', { name: '第 4 列后', exact: true }).click()
  await page.getByRole('option', { name: '最左侧', exact: true }).click()
  await page.getByRole('option', { name: '最右侧', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(add.locator('.hall-aisle')).toHaveCount(30)
  const cols = add.locator('.el-form-item').filter({ hasText: '座位列数' }).getByRole('spinbutton')
  await cols.fill('8')
  await cols.press('Tab')
  await expect(add.locator('.seat-cell')).toHaveCount(80)
  await expect(add.locator('[title="最右侧过道"]')).toHaveCount(10)
  await add.getByRole('button', { name: '确定', exact: true }).click()
  await expect(add).not.toBeVisible()
  expect(JSON.parse(created!.seatLayout).aisleAfterCols).toEqual([0, 4, 8])
  expect(JSON.parse(created!.seatLayout).seats.flat()).toHaveLength(80)
})
