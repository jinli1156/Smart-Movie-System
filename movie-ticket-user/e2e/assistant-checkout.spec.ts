import { expect, test, type Page } from '@playwright/test'

const confirmation = {
  draftId: '11111111-1111-4111-8111-111111111111', expiresAt: '2030-01-01T12:05:00', status: 'AWAITING_CONFIRMATION',
  showtimeId: 42, movieTitle: '周末喜剧', cinemaName: '测试影城', hallName: '2号厅', startTime: '2030-01-01 19:30:00', endTime: '2030-01-01 21:10:00', people: 2,
  seats: ['5排5座', '5排6座'], originalPrice: 80, usePoints: false, useCoupons: true,
  bestPlan: { totalPrice: 70, couponId: 9, couponName: '满80减10', couponDiscount: 10, pointsUsed: 0, pointsDiscount: 0 },
  notice: '确认后创建待支付订单，支付由你完成。当前尚未锁座。'
}
const frame = (data: unknown) => `event: done\ndata: ${JSON.stringify(data)}\n\n`
async function setup(page: Page, loggedIn = true) {
  if (loggedIn) await page.addInitScript(() => {
    localStorage.setItem('token', 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig')
    localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'buyer', role: 'USER' }))
  })
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = []
    if (path === '/api/assistant/capabilities') data = { configured: true, model: 'test-model' }
    if (path === '/api/assistant/conversations') data = { items: [], hasMore: false }
    if (path === '/api/order/AI-ORDER') data = { id: 1, orderNo: 'AI-ORDER', showtimeId: 42, movieTitle: confirmation.movieTitle, cinemaName: confirmation.cinemaName, hallName: confirmation.hallName, showtime: confirmation.startTime, seats: confirmation.seats.join(','), seatCount: 2, originalPrice: 80, totalPrice: 80, status: 0, createTime: new Date().toISOString() }
    if (path === '/api/user/points') data = 500
    if (path === '/api/coupon/my') data = [{ id: 9, name: '满80减10', amount: 10, minSpend: 80, status: 0, available: true, validFrom: '2020-01-01T00:00:00', expireTime: '2099-01-01T00:00:00' }]
    if (path === '/api/pricing/orders/AI-ORDER/quote') data = { originalPrice: 80, availablePoints: 500, bestPlan: confirmation.bestPlan, plans: [] }
    return route.fulfill({ json: { code: 200, data } })
  })
  await page.route('**/api/assistant/chat/stream', route => route.fulfill({ contentType: 'text/event-stream', body: frame({ answer: '请确认场次和座位。', source: 'model', suggestions: ['确认下单'], toolResults: [{ ok: true, source: 'prepare_ticket_order', data: confirmation }] }) }))
  await page.goto('/assistant')
}
async function ask(page: Page, question: string) {
  await page.getByLabel('向影票观提问').fill(question)
  await page.getByRole('button', { name: '发送', exact: true }).click()
}

test('确认卡不自动下单，点击后仅创建待支付订单并直达支付页', async ({ page }) => {
  await setup(page)
  let confirms = 0, payments = 0, ordinaryOrders = 0
  page.on('request', req => { if (req.method() === 'POST' && /\/pay$/.test(req.url())) payments++; if (req.method() === 'POST' && /\/api\/order$/.test(req.url())) ordinaryOrders++ })
  await page.route('**/api/assistant/checkout/*/confirm', route => {
    confirms++; expect(route.request().postDataJSON()).toEqual({})
    return route.fulfill({ json: { code: 200, data: { orderNo: 'AI-ORDER', status: 0, usePoints: false, useCoupons: true, couponId: 9 } } })
  })
  await ask(page, '就要第二个，帮我买两张')
  const card = page.getByRole('region', { name: '订单确认卡' })
  await expect(card).toContainText('预计实付 ¥70.00'); await expect(card).toContainText('5排5座 · 5排6座')
  expect(confirms).toBe(0)
  await card.getByRole('button', { name: '确认下单并去支付' }).click()
  await expect(page).toHaveURL(/\/order\/pay\/AI-ORDER\?pricingPoints=false&pricingCoupons=true&pricingCouponId=9/)
  expect(confirms).toBe(1); expect(payments).toBe(0); expect(ordinaryOrders).toBe(0)
  await expect(page.getByRole('button', { name: /立即支付/ })).toBeVisible()
})

test('文字确认走确定接口且重复点击只发送一次，等待期间不调用支付', async ({ page }) => {
  await setup(page)
  let confirms = 0, chats = 0
  page.on('request', req => { if (req.url().endsWith('/chat/stream')) chats++ })
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/assistant/checkout/*/confirm', async route => { confirms++; await gate; return route.fulfill({ json: { code: 200, data: { orderNo: 'AI-ORDER', status: 0, usePoints: false, useCoupons: false } } }) })
  await ask(page, '帮我准备订单')
  await expect(page.getByRole('region', { name: '订单确认卡' })).toBeVisible()
  await ask(page, '确认下单')
  await expect.poll(() => confirms).toBe(1)
  await expect(page.getByRole('button', { name: '正在创建订单…' })).toBeDisabled()
  expect(chats).toBe(1)
  release(); await expect(page).toHaveURL(/\/order\/pay\/AI-ORDER/)
})

test('抢座或过期错误保留确认卡，网络重试仍使用相同确认卡', async ({ page }) => {
  await setup(page)
  const ids: string[] = []
  await page.route('**/api/assistant/checkout/*/confirm', route => { ids.push(route.request().url()); return route.fulfill({ status: 409, json: { code: 409, message: '座位已被占用，请重新准备方案' } }) })
  await ask(page, '准备这场电影的订单')
  const button = page.getByRole('button', { name: '确认下单并去支付' })
  await button.click(); await expect(page.getByRole('alert')).toContainText('座位已被占用')
  await button.click(); await expect.poll(() => ids.length).toBe(2)
  expect(ids[0]).toBe(ids[1]); await expect(page).toHaveURL(/\/assistant$/)
  await page.route('**/api/assistant/checkout/prepare', route => route.fulfill({ json: { code: 200, data: { ...confirmation, draftId: 'new-confirmation', seats: ['6排3座', '6排4座'] } } }))
  await page.getByRole('button', { name: '重新准备订单', exact: true }).click()
  await expect(page.getByRole('region', { name: '订单确认卡' }).last()).toContainText('6排3座 · 6排4座')
  expect(ids.length).toBe(2)
})

test('访客确认引导登录，手机确认卡和输入框无横向溢出', async ({ page }) => {
  await setup(page, false)
  let confirms = 0
  page.on('request', req => { if (req.url().endsWith('/confirm')) confirms++ })
  await ask(page, '帮我准备订单')
  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }, { width: 320, height: 568 }]) {
    await page.setViewportSize(viewport)
    await expect(page.getByRole('button', { name: '确认下单并去支付' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    const composer = await page.locator('.composer').boundingBox()
    expect(composer!.y + composer!.height).toBeLessThanOrEqual(viewport.height)
    if (viewport.width === 390) await page.screenshot({ path: '../.local/assistant-upgrade/checkout-mobile.png', fullPage: true })
  }
  await page.getByRole('button', { name: '确认下单并去支付' }).click()
  await expect(page).toHaveURL(/\/login\?redirect=/); expect(confirms).toBe(0)
})

test('完整观影方案可以在聊天内准备订单，并保留该方案的座位和优惠偏好', async ({ page }) => {
  await setup(page)
  const submitted: any[] = []
  await page.route('**/api/assistant/chat/stream', route => route.fulfill({ contentType: 'text/event-stream', body: frame({ answer: '找到两个座位。', source: 'model', suggestions: [], toolResults: [{ ok: true, source: 'plan_movie_outing', data: { people: 2, usePoints: false, useCoupons: true, criteria: { maxTotal: 80 }, plans: [{ ...confirmation, movieId: 2 }] } }] }) }))
  await page.route('**/api/assistant/checkout/prepare', route => { submitted.push(route.request().postDataJSON()); return route.fulfill({ json: { code: 200, data: confirmation } }) })
  await ask(page, '两个人预算80元，找连座')
  await page.getByRole('button', { name: '让影票观下单' }).click()
  await expect(page.getByRole('region', { name: '订单确认卡' })).toBeVisible()
  expect(submitted).toEqual([{ criteria: { maxTotal: 80, showtimeId: 42, people: 2, usePoints: false, useCoupons: true }, seats: confirmation.seats }])
})
