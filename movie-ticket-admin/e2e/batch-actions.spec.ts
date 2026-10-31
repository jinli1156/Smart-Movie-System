import { expect, test } from '@playwright/test'

const pages = [
  { path: 'movie', endpoint: 'movie/page', row: { title: '测试电影', duration: 100, rating: 8, status: 0 } },
  { path: 'cinema', endpoint: 'cinema/page', row: { name: '测试影院', status: 1 } },
  { path: 'hall', endpoint: 'hall/page', row: { name: '测试影厅', cinemaName: '测试影院', totalRows: 5, totalCols: 5, status: 1 } },
  { path: 'showtime', endpoint: 'showtime/page', row: { movieTitle: '测试排片', startTime: '2026-09-11T10:00:00', endTime: '2026-09-11T12:00:00', status: 0 } },
  { path: 'coupon-code', endpoint: 'admin/coupon-codes', row: { code: 'TEST', name: '测试兑换码', status: 1, redeemedCount: 1, totalQuantity: 10, amount: 10, minAmount: 30, validDays: 7 } }
]

test.beforeEach(async ({ page }) => {
  await page.route(/https:\/\/fonts\.(googleapis|gstatic)\.com\//, route => route.abort())
  await page.addInitScript(() => {
    localStorage.setItem('token', 'test-admin-token')
    localStorage.setItem('userInfo', JSON.stringify({ id: 1, username: 'admin', role: 'ADMIN' }))
  })
})

for (const item of pages) {
  test(`${item.path}: 当前页全选、清空和取消批量删除`, async ({ page }) => {
    const mutations: string[] = []
    await page.route(url => url.pathname.startsWith('/api/'), route => {
      const req = route.request()
      const path = new URL(req.url()).pathname
      if (req.method() !== 'GET') mutations.push(path)
      const data = path === `/api/${item.endpoint}` ? { records: [1, 2].map(id => ({ ...item.row, id })), total: 2 } : []
      return route.fulfill({ json: { code: 200, data } })
    })
    await page.goto(`/${item.path}`)
    const bar = page.getByLabel('批量操作', { exact: true })
    await expect(bar.getByRole('button', { name: '批量删除', exact: true })).toBeDisabled()
    await expect(page.locator('.el-table__row')).toHaveCount(2)
    await expect(page.locator('.el-loading-mask')).toHaveCount(0)
    const appearance = (button: HTMLElement) => {
      const style = getComputedStyle(button)
      return { color: style.color, background: style.backgroundColor, shadow: style.boxShadow, height: style.height, font: style.fontSize, cursor: style.cursor }
    }
    const deleteButton = bar.getByRole('button', { name: '批量删除', exact: true })
    const referenceButton = bar.getByRole('button').nth(1)
    expect(await deleteButton.evaluate(appearance)).toEqual(await referenceButton.evaluate(appearance))
    await referenceButton.hover()
    const hoveredAppearance = await referenceButton.evaluate(appearance)
    await deleteButton.hover()
    expect(await deleteButton.evaluate(appearance)).toEqual(hoveredAppearance)
    await page.mouse.move(0, 0)
    if (item.path === 'cinema') await bar.screenshot({ path: '../output/batch-actions/disabled.png' })
    const table = page.locator('.el-table')
    const bounds = await table.boundingBox()
    const listRequests: string[] = []
    page.on('request', request => { if (new URL(request.url()).pathname === `/api/${item.endpoint}`) listRequests.push(request.url()) })
    await page.locator('.el-table__header-wrapper .el-checkbox').first().click()
    await expect(bar).toContainText('已选 2 项')
    await expect(bar.getByRole('button', { name: '批量删除', exact: true })).toBeEnabled()
    if (item.path === 'cinema') await bar.screenshot({ path: '../output/batch-actions/enabled.png' })
    await expect(page.locator('.el-loading-mask')).toHaveCount(0)
    expect(await table.boundingBox()).toEqual(bounds)
    await expect(page.locator('.el-table__header-wrapper .el-checkbox__inner').first()).toHaveCSS('transition-duration', '0s')
    await expect(bar.getByRole('button', { name: '批量删除', exact: true })).toHaveCSS('transition-duration', '0s')
    await bar.getByRole('button', { name: '取消选择' }).click()
    await expect(bar).toContainText('已选 0 项')
    await page.locator('.el-table__header-wrapper .el-checkbox').first().click()
    await bar.getByRole('button', { name: '批量删除', exact: true }).click()
    if (item.path === 'coupon-code') {
      await expect(page.locator('.batch-result')).toContainText('没有可操作的记录，跳过 2 项')
    } else {
      await expect(page.locator('.el-message-box')).toBeVisible()
      await expect(page.locator('.el-message-box')).toContainText('删除后不可恢复')
      await expect(page.locator('.el-message-box').getByRole('button', { name: '确认删除', exact: true })).toBeVisible()
      expect(mutations).toEqual([])
      await expect(page.locator('.el-loading-mask')).toHaveCount(0)
      await expect(bar.getByRole('status')).toHaveCount(0)
      await page.getByRole('button', { name: '取消', exact: true }).last().click()
      await expect(bar).toContainText('已选 2 项')
    }
    expect(mutations).toEqual([])
    expect(listRequests).toEqual([])
  })
}

test('批量修改处理部分失败，跳过相同状态且清空选择', async ({ page }) => {
  const mutations: number[] = []
  const records = [1, 2, 3].map(id => ({ id, title: `测试电影${id}`, duration: 100, status: id === 3 ? 1 : 0 }))
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const url = new URL(route.request().url())
    if (route.request().method() === 'PUT') {
      const id = Number(url.pathname.split('/').at(-2))
      mutations.push(id)
      if (id === 2) return route.fulfill({ json: { code: 400, message: '测试拒绝修改' } })
      records[0].status = 1
    }
    return route.fulfill({ json: { code: 200, data: url.pathname === '/api/movie/page' ? { records, total: 3 } : [] } })
  })
  await page.goto('/movie')
  await page.locator('.el-table__header-wrapper .el-checkbox').first().click()
  await page.getByRole('button', { name: '批量设为热映', exact: true }).click()
  await expect(page.locator('.el-message-box')).toContainText('已选 3 项')
  await page.getByRole('button', { name: '确定执行', exact: true }).click()
  await expect(page.locator('.batch-result')).toContainText('成功 1 项，失败 1 项，跳过 1 项')
  await expect(page.locator('.batch-result')).toContainText('失败 ID：2')
  await expect(page.getByLabel('批量操作', { exact: true })).toContainText('已选 0 项')
  expect(mutations).toEqual([1, 2])
})

test('批量删除仅删除所选记录并刷新列表', async ({ page }) => {
  let records = [1, 2, 3].map(id => ({ id, title: `测试电影${id}`, duration: 100, status: 0 }))
  const deleted: number[] = []
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    if (route.request().method() === 'DELETE') {
      const id = Number(path.split('/').at(-1))
      deleted.push(id)
      records = records.filter(row => row.id !== id)
    }
    return route.fulfill({ json: { code: 200, data: path === '/api/movie/page' ? { records, total: records.length } : [] } })
  })
  await page.goto('/movie')
  await page.locator('.el-table__body-wrapper .el-table__row .el-checkbox').nth(0).click()
  await page.locator('.el-table__body-wrapper .el-table__row .el-checkbox').nth(1).click()
  await page.getByRole('button', { name: '批量删除', exact: true }).click()
  await expect(page.locator('.el-message-box')).toBeVisible()
  await expect(page.locator('.el-message-box')).toContainText('删除后不可恢复')
  expect(deleted).toEqual([])
  await page.getByRole('button', { name: '确认删除', exact: true }).click()
  await expect(page.locator('.batch-result')).toContainText('成功 2 项，失败 0 项')
  await expect(page.locator('.el-table__body-wrapper .el-table__row')).toHaveCount(1)
  expect(deleted).toEqual([1, 2])
})
