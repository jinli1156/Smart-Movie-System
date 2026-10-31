import { expect, test, type Page, type Route } from '@playwright/test'

const showtime = {
  id: 101,
  movieId: 11,
  movieTitle: 'E2E 测试电影',
  cinemaId: 21,
  cinemaName: 'E2E 中心影院',
  hallId: 31,
  hallName: 'IMAX 测试厅',
  startTime: '2099-09-03T19:30:00',
  endTime: '2099-09-03T21:30:00',
  basePrice: 48,
  totalSeats: 16,
  availableSeats: 16,
  status: 0,
  statusText: '可售'
}

const json = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 200, message: '操作成功', data })
})

async function mockBackend(page: Page) {
  let paid = false
  let refunded = false

  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path === '/api/auth/captcha') {
      return json(route, { captchaId: 'purchase-test', expiresIn: 120,
        image: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=' })
    }
    if (path === '/api/auth/login') {
      return json(route, {
        token: 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig',
        userInfo: { id: 7, username: 'user1', nickname: 'E2E 用户', role: 'USER' }
      })
    }
    if (path === '/api/showtime/101' && request.method() === 'GET') {
      return json(route, showtime)
    }
    if (path === '/api/showtime/101/seats') {
      return json(route, {
        totalRows: 4,
        totalCols: 4,
        seatLayout: '',
        occupiedSeats: ['1排1座'],
        availableSeats: 15,
        totalSeats: 16
      })
    }
    if (path === '/api/showtime/101/seat-recommendation') {
      return json(route, {
        recommendations: [{
          title: '中央最佳视野', icon: 'stars', match: 98,
          seatLabel: '2排2座 · 2排3座', seats: ['2排2座', '2排3座'], count: 2,
          unitPrice: 48, reason: '连续中央座位', tags: ['中央', '连续'],
          scores: { visionCenter: 100, goldenDistance: 95, continuity: 100, accessConvenience: 80, preference: 98, realtimeAvailability: 100 },
          singleSeatPenalty: 0
        }],
        interpretedPreference: 'CENTER',
        preferenceSummary: '中央视野',
        analysisSource: 'algorithm',
        availabilityToken: 'seat-version-1',
        generatedAt: new Date().toISOString()
      })
    }
    if (path === '/api/order' && request.method() === 'POST') {
      const body = request.postDataJSON()
      expect(body.showtimeId).toBe(101)
      expect(body.seats).toEqual(['2排2座'])
      return json(route, 'ORDER-E2E-001')
    }
    if (path === '/api/order/ORDER-E2E-001/pay') {
      paid = true
      return json(route, null)
    }
    if (path === '/api/order/ORDER-E2E-001/refund') {
      refunded = true
      return json(route, null)
    }
    if (path === '/api/order/ORDER-E2E-001') {
      return json(route, {
        id: 1,
        orderNo: 'ORDER-E2E-001',
        userId: 7,
        showtimeId: 101,
        movieId: 11,
        movieTitle: showtime.movieTitle,
        cinemaName: showtime.cinemaName,
        hallName: showtime.hallName,
        showtime: showtime.startTime,
        seats: '2排2座',
        seatCount: 1,
        totalPrice: 48,
        originalPrice: 48,
        discountAmount: 0,
        status: refunded ? 3 : (paid ? 1 : 0),
        statusText: refunded ? '已退款' : (paid ? '已支付' : '待支付'),
        ticketCode: paid ? '87654321' : undefined,
        isPicked: 0,
        payTime: paid ? new Date().toISOString() : undefined,
        createTime: new Date().toISOString()
      })
    }
    if (path === '/api/coupon/my') {
      return json(route, [])
    }
    if (path.includes('/now-showing') || path.includes('/coming-soon') || path.includes('/recommend/')) {
      return json(route, [])
    }
    if (path.includes('/page')) {
      return json(route, { records: [], total: 0, size: 10, current: 1, pages: 0 })
    }
    return json(route, [])
  })
}

test('用户可在浏览器完成登录、选座、下单、支付、查看取票码和退票', async ({ page }) => {
  await mockBackend(page)

  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill('user1')
  await page.getByPlaceholder('请输入密码').fill('admin123')
  await page.getByLabel('验证码', { exact: true }).fill('AB23')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/home$/)
  await expect.poll(() => page.evaluate(() => localStorage.getItem('token')))
    .toBe('e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig')

  await page.goto('/showtime/101/seats')
  await expect(page.getByText('E2E 测试电影').first()).toBeVisible()
  await page.locator('.seat').nth(5).click()
  await expect(page.getByText('已选 1 个座位')).toBeVisible()
  await page.getByRole('button', { name: '确认选座' }).click()
  await page.getByRole('button', { name: '我已阅读并同意' }).click()

  await expect(page).toHaveURL(/\/order\/confirm/)
  await expect(page.getByText('2排2座')).toBeVisible()
  await page.getByRole('button', { name: '提交订单' }).click()

  await expect(page).toHaveURL(/\/order\/pay\/ORDER-E2E-001$/)
  await page.getByRole('button', { name: /立即支付/ }).click()
  await page.getByRole('button', { name: '确认支付' }).click()

  await expect(page).toHaveURL(/\/order\/ORDER-E2E-001$/)
  await expect(page.getByText('已支付', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '查看取票码' }).click()
  await expect(page.getByText('87654321')).toBeVisible()
  await page.locator('.el-dialog__headerbtn').click()

  await page.getByRole('button', { name: '申请退票' }).click()
  await page.getByRole('button', { name: '确认退票' }).click()
  await expect(page.getByText('已退款', { exact: true })).toBeVisible()
})
