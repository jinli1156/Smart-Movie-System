import { expect, test, type Route } from '@playwright/test'

const showtime = {
  id: 101,
  movieId: 11,
  movieTitle: '确认页测试电影',
  cinemaId: 21,
  cinemaName: 'CINEMA+ 测试影院',
  hallId: 31,
  hallName: '1号厅',
  startTime: '2099-09-18T19:30:00',
  endTime: '2099-09-18T21:30:00',
  basePrice: 48,
  status: 0
}

const json = (route: Route, data: unknown, status = 200, message = '操作成功') => route.fulfill({
  status,
  contentType: 'application/json',
  body: JSON.stringify({ code: status === 200 ? 200 : status, message, data })
})

test('创建订单失败时保留确认页并显示后端原因', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig')
    localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'user1', nickname: '测试用户', role: 'USER' }))
  })

  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/showtime/101' && request.method() === 'GET') return json(route, showtime)
    if (path === '/api/showtime/101/seats') {
      return json(route, { totalRows: 4, totalCols: 4, occupiedSeats: [], availableSeats: 16, totalSeats: 16 })
    }
    if (path === '/api/order' && request.method() === 'POST') {
      return json(route, null, 400, '该场次已停售，请重新选择场次')
    }
    return json(route, [])
  })

  await page.goto('/order/confirm?showtimeId=101&seats=2%E6%8E%922%E5%BA%A7')
  await expect(page.getByRole('heading', { name: '确认订单信息' })).toBeVisible()
  await page.getByRole('button', { name: '提交订单' }).click()

  await expect(page.getByText('订单提交失败')).toBeVisible()
  await expect(page.locator('.error-banner').getByText('该场次已停售，请重新选择场次')).toBeVisible()
  await expect(page).toHaveURL(/\/order\/confirm/)
})

test('场次时间缺失时显示失效状态而不是抛出 startTime 异常', async ({ page }) => {
  const pageErrors: string[] = []
  page.on('pageerror', error => pageErrors.push(error.message))
  await page.addInitScript(() => {
    localStorage.setItem('token', 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig')
    localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'user1', nickname: '测试用户', role: 'USER' }))
  })
  await page.route('**/api/showtime/101', route => json(route, { ...showtime, startTime: undefined }))

  await page.goto('/order/confirm?showtimeId=101&seats=2%E6%8E%922%E5%BA%A7')

  await expect(page.getByRole('heading', { name: '订单信息加载失败' })).toBeVisible()
  await expect(page.getByText('场次信息不完整，请返回重新选择。')).toBeVisible()
  expect(pageErrors).toEqual([])
})
