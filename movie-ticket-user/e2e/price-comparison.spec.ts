import { expect, test, type Page, type Route } from '@playwright/test'

const date = new Date(Date.now() + 86400000 + 8 * 3600000).toISOString().slice(0, 10)
const movie = { id: 2, title: '比价测试电影', genre: '剧情', director: '测试导演', actors: '测试演员', duration: 100, rating: 8, status: 1, description: '用于验证真实比价入口', releaseDate: '2026-01-01' }
const json = (route: Route, data: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
const plan = (points = true) => ({ couponId: 9, couponName: '满82减10', couponDiscount: 10, pointsUsed: points ? 100 : 0, pointsDiscount: points ? 1 : 0, discountAmount: points ? 11 : 10, totalPrice: points ? 71 : 72 })
const comparison = (points = true, personal = true, people = 2) => ({ movieId: 2, movieTitle: movie.title, date, people,
  personalized: personal, usePoints: points, useCoupons: true, availablePoints: personal ? 200 : 0,
  comparedCount: 2, matchedCount: 2, queriedAt: `${date}T10:00:00`, notice: '站内同片同日比价；查询不占券、不扣积分、不锁座，支付时重新校验。',
  offers: [{ showtimeId: 102, cinemaId: 3, cinemaName: '江南影城', hallName: 'IMAX 厅', startTime: `${date}T19:30:00`, availableSeats: 20, basePrice: 41, originalPrice: 82,
    bestPlan: personal ? plan(points) : { ...plan(false), couponId: undefined, couponName: '不使用优惠券', couponDiscount: 0, discountAmount: 0, totalPrice: 82 }, reason: '当前优惠后最低总实付' }] })

async function setup(page: Page, loggedIn = true) {
  if (loggedIn) await page.addInitScript(() => {
    localStorage.setItem('token', 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig')
    localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'user1', nickname: '比价用户', role: 'USER' }))
  })
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/movie/2') return json(route, movie)
    if (url.pathname === '/api/pricing/compare') return json(route, comparison(url.searchParams.get('usePoints') === 'true', loggedIn, Number(url.searchParams.get('people'))))
    if (url.pathname === '/api/user/points') return json(route, 200)
    if (url.pathname === '/api/coupon/my') return json(route, [{ id: 9, name: '满82减10', amount: 10, minSpend: 82, status: 0, available: true, validFrom: '2026-01-01T00:00:00', expireTime: '2099-01-01T00:00:00' }])
    if (url.pathname === '/api/order/PRICE-ORDER') return json(route, { id: 1, orderNo: 'PRICE-ORDER', showtimeId: 102, movieTitle: movie.title, cinemaName: '江南影城', hallName: 'IMAX 厅', showtime: `${date}T19:30:00`, seats: '2排2座,2排3座', seatCount: 2, originalPrice: 82, totalPrice: 82, status: 0, createTime: new Date().toISOString() })
    if (url.pathname === '/api/pricing/orders/PRICE-ORDER/quote') return json(route, { orderNo: 'PRICE-ORDER', originalPrice: 82, availablePoints: 200, bestPlan: plan(url.searchParams.get('usePoints') === 'true'), plans: [], notice: '仅试算' })
    if (url.pathname === '/api/showtime/102') return json(route, { id: 102, movieId: 2, movieTitle: movie.title, cinemaName: '江南影城', hallName: 'IMAX 厅', startTime: `${date}T19:30:00`, basePrice: 41, availableSeats: 20 })
    if (url.pathname === '/api/showtime/102/seats') return json(route, { totalRows: 4, totalCols: 4, occupiedSeats: [], totalSeats: 16, availableSeats: 16 })
    if (url.pathname === '/api/showtime/102/seat-recommendation') return json(route, { recommendations: [], availabilityToken: 'test', analysisSource: 'algorithm' })
    if (url.pathname.endsWith('/insight')) return json(route, {})
    return json(route, [])
  })
}

test('详情自动识别电影，修改人数积分重新比价，选座携带优惠偏好', async ({ page }) => {
  await setup(page)
  await page.goto('/movie/2')
  const panel = page.getByRole('region', { name: 'AI 智能比价' })
  await expect(panel.getByText('已识别《比价测试电影》', { exact: false })).toBeVisible()
  const request = page.waitForRequest(r => r.url().includes('/pricing/compare') && r.url().includes('people=2'))
  await panel.getByLabel('比价人数').selectOption('2')
  expect(new URL((await request).url()).searchParams.get('movieId')).toBe('2')
  await panel.getByLabel('使用积分', { exact: true }).uncheck()
  await expect(panel.locator('.offer-bottom strong')).toHaveText('¥72.00')
  await expect(panel.getByRole('link', { name: '前往选座' })).toHaveAttribute('href', /people=2.*pricingPoints=false.*pricingCoupons=true/)
  await panel.getByRole('link', { name: '前往选座' }).click()
  await expect(page).toHaveURL(/showtime\/102\/seats\?people=2/)
})

test('访客仅显示原价，查询失败可重试，空结果不伪造推荐', async ({ page }) => {
  await setup(page, false)
  let fail = true
  await page.route('**/api/pricing/compare*', route => fail ? route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '比价服务暂不可用' }) }) : json(route, { ...comparison(false, false), offers: [], comparedCount: 0, matchedCount: 0 }))
  await page.goto('/movie/2')
  const panel = page.getByRole('region', { name: 'AI 智能比价' })
  await expect(panel.getByRole('alert')).toContainText('比价服务暂不可用')
  await expect(panel.getByLabel('使用积分', { exact: true })).toBeDisabled()
  await expect(panel.getByRole('link', { name: '登录后匹配我的优惠' })).toBeVisible()
  fail = false
  await panel.getByRole('button', { name: '重试', exact: true }).click()
  await expect(panel.getByText('当日暂无满足人数要求的可购场次，请更换日期或人数。')).toBeVisible()
  await expect(panel.locator('.offer')).toHaveCount(0)
})

test('支付页应用后端优惠方案，提交真实优惠券和积分而不提交价格', async ({ page }) => {
  await setup(page)
  await page.route('**/api/order/PRICE-ORDER/pay', route => json(route, null))
  await page.goto('/order/pay/PRICE-ORDER?pricingPoints=true&pricingCoupons=true')
  await expect(page.getByRole('button', { name: '立即支付 ¥71.00' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'AI 智能比价' })).toHaveCount(0)
  await expect(page.getByLabel('当前订单优惠方案')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '一键应用最省方案' })).toHaveCount(0)
  const payment = page.waitForRequest(r => r.url().endsWith('/order/PRICE-ORDER/pay'))
  await page.getByRole('button', { name: /立即支付/ }).click()
  await page.getByRole('button', { name: '确认支付', exact: true }).click()
  const body = (await payment).postDataJSON()
  expect(body.couponId).toBe(9)
  expect(body.points).toBe(100)
  expect(body).not.toHaveProperty('totalPrice')
})

test('确认订单时按已选日期和票数比价，重新选座前不创建订单', async ({ page }) => {
  await setup(page)
  let orders = 0
  page.on('request', request => { if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/order') orders++ })
  const request = page.waitForRequest(r => r.url().includes('/pricing/compare'))
  await page.goto('/order/confirm?showtimeId=102&seats=2排2座,2排3座')
  const params = new URL((await request).url()).searchParams
  expect(params.get('showtimeId')).toBe('102')
  expect(params.get('date')).toBe(date)
  expect(params.get('people')).toBe('2')
  const panel = page.getByRole('region', { name: 'AI 智能比价' })
  await expect(panel.getByLabel('比价人数')).toBeDisabled()
  await expect(panel.getByLabel('比价人数')).toHaveValue('2')
  await expect(panel).toContainText('当前尚未提交订单')
  await expect(panel).not.toContainText('当前待支付订单仍保留')
  await panel.getByRole('link', { name: '查看当前场次' }).click()
  await expect(page).toHaveURL(/showtime\/102\/seats\?people=2/)
  expect(orders).toBe(0)
})

test('手机确认页比价卡片无横向溢出并保留金额明细', async ({ page }) => {
  await setup(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/order/confirm?showtimeId=102&seats=2排2座,2排3座')
  const panel = page.getByRole('region', { name: 'AI 智能比价' })
  await expect(panel.locator('.offer-bottom strong')).toHaveText('¥71.00')
  await panel.scrollIntoViewIfNeeded()
  const dimensions = await panel.evaluate(el => ({ scroll: el.scrollWidth, client: el.clientWidth }))
  expect(dimensions.scroll).toBeLessThanOrEqual(dimensions.client + 1)
  await panel.screenshot({ path: '../output/price-comparison-mobile.png' })
})

test('AI 助手展示真实比价卡片、优惠明细和选座入口', async ({ page }) => {
  await setup(page)
  await page.route('**/api/assistant/conversations*', route => json(route, { items: [], hasMore: false }))
  await page.route('**/api/assistant/capabilities', route => json(route, { configured: true, model: 'test', streaming: true, vision: false }))
  await page.route('**/api/assistant/chat/stream', route => route.fulfill({ contentType: 'text/event-stream', body: `event: done\ndata: ${JSON.stringify({ answer: '推荐江南影城，用券和积分后预计实付71元。', source: 'model', suggestions: [], toolResults: [{ ok: true, source: 'compare_prices', data: comparison() }] })}\n\n` }))
  await page.goto('/assistant')
  await page.getByLabel('向影票观提问').fill('帮我比价这部电影，两个人，用优惠券和积分')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  const cards = page.locator('.primary-results')
  await expect(cards).toContainText('最低实付推荐')
  await expect(cards).toContainText('预计 ¥71.00')
  await expect(cards).toContainText('100 积分抵 ¥1.00')
  await expect(cards.getByRole('link', { name: '前往选座' })).toHaveAttribute('href', /102\/seats\?people=2.*pricingPoints=true.*pricingCoupons=true/)
})
