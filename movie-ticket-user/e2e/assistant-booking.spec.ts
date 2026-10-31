import { expect, test, type Page, type Route } from '@playwright/test'

const token = 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig'
const json = (route: Route, data: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
const frame = (event: string, data: unknown) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`
const seatResult = { ok: true, source: 'recommend_seats', data: { showtimeId: 101, people: 2, preferenceSummary: '中央视野', recommendations: [{ seats: ['2排2座', '2排3座'], reason: '中央连续座位' }], notice: '未锁座，进入选座页重新校验。' } }
const quoteResult = { ok: true, source: 'quote_price', data: { showtimeId: 101, originalPrice: 80, plans: [{ couponName: '满80减10', couponDiscount: 10, pointsUsed: 500, pointsDiscount: 5, totalPrice: 65 }], notice: '仅试算，不消耗优惠券或积分。' } }

const noAisleLayout = JSON.stringify({ seats: Array.from({ length: 4 }, () => [1, 1, 1, 1]), aisleAfterCols: [] })
async function prepare(page: Page, signedIn = true, occupied = ['1排1座'], layout = noAisleLayout) {
  if (signedIn) await page.addInitScript(value => {
    localStorage.setItem('token', value)
    localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'user1', nickname: '测试用户', role: 'USER' }))
  }, token)
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/assistant/conversations') return json(route, { items: [], hasMore: false, page: 1 })
    if (path === '/api/assistant/capabilities') return json(route, { configured: true, model: 'test', streaming: true, vision: false })
    if (path === '/api/auth/login') return json(route, { token, userInfo: { id: 7, username: 'user1', role: 'USER' } })
    if (path === '/api/showtime/101') return json(route, { id: 101, movieId: 11, movieTitle: '测试科幻片', cinemaName: '测试影院', hallName: 'IMAX厅', startTime: '2099-09-05T20:00:00', endTime: '2099-09-05T22:00:00', basePrice: 40, status: 0 })
    if (path === '/api/showtime/101/seats') return json(route, { totalRows: 4, totalCols: 4, seatLayout: layout, occupiedSeats: occupied, availableSeats: 15, totalSeats: 16 })
    if (path === '/api/showtime/101/seat-recommendation') return json(route, { recommendations: [], interpretedPreference: 'CENTER', preferenceSummary: '中央视野', analysisSource: 'algorithm', availabilityToken: '1' })
    if (path === '/api/assistant/chat/stream') return route.fulfill({ contentType: 'text/event-stream', body: frame('done', { answer: '推荐中央连座，优惠后预计65元。', source: 'model', suggestions: [], toolResults: [seatResult, quoteResult] }) })
    return json(route, [])
  })
}

test('assistant shows both seat proposal and itemized quote, then preselects fresh seats without ordering', async ({ page }) => {
  await prepare(page)
  const writes: string[] = []
  page.on('request', request => { if (request.method() === 'POST' && /\/api\/(order|coupon)/.test(request.url())) writes.push(request.url()) })
  await page.goto('/assistant')
  await page.getByLabel('向影票观提问').fill('两个人坐中间，并试算优惠')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.primary-results')).toContainText('预计 ¥65.00')
  await expect(page.locator('.primary-results')).toContainText('原价 ¥80.00')
  await expect(page.locator('.primary-results')).toContainText('500 积分抵 ¥5.00')
  await page.screenshot({ path: '../.local/assistant-booking.png', fullPage: true })
  await page.getByRole('link', { name: '使用此座位方案' }).click()
  await expect(page.locator('.checkout-bar')).toContainText('已选 2 个座位')
  await expect(page.locator('.seat.selected')).toHaveCount(2)
  await expect(page).toHaveURL(/\/showtime\/101\/seats$/)
  expect(writes).toEqual([])
})

for (const fixture of [
  { name: 'across aisle', seats: '2排2座,2排3座', occupied: [], layout: '' },
  { name: 'occupied', seats: '2排2座,2排3座', occupied: ['2排3座'], layout: '' },
  { name: 'disabled', seats: '2排2座,2排3座', occupied: [], layout: '[[1,1,1,1],[1,0,1,1],[1,1,1,1],[1,1,1,1]]' },
  { name: 'out of bounds', seats: '99排2座', occupied: [], layout: '' },
  { name: 'duplicate', seats: '2排2座,2排2座', occupied: [], layout: '' },
  { name: 'malformed', seats: 'not-a-seat', occupied: [], layout: '' }
]) test(`assistant proposal rejects ${fixture.name} seats as a whole`, async ({ page }) => {
  await prepare(page, true, fixture.occupied, fixture.layout)
  await page.goto(`/showtime/101/seats?assistantSeats=${encodeURIComponent(fixture.seats)}`)
  await expect(page.getByText('助手方案已失效或包含不可选座位，请重新选择')).toBeVisible()
  await expect(page.locator('.seat.selected')).toHaveCount(0)
  await expect(page.locator('.checkout-bar')).toHaveCount(0)
})

test('guest login preserves assistant seat proposal', async ({ page }) => {
  await prepare(page, false)
  await page.goto(`/showtime/101/seats?assistantSeats=${encodeURIComponent('2排2座,2排3座')}`)
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await page.getByPlaceholder('请输入用户名').fill('user1')
  await page.getByPlaceholder('请输入密码').fill('password123')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.locator('.checkout-bar')).toContainText('已选 2 个座位')
})

test('complex query result shows rating duration hall and real price together', async ({ page }) => {
  await prepare(page)
  await page.route('**/api/assistant/chat/stream', route => route.fulfill({ contentType: 'text/event-stream', body: frame('done', { answer: '符合条件的场次如下。', source: 'model', suggestions: [], toolResults: [{ ok: true, source: 'search_showtimes', data: { items: [{ id: 101, movieTitle: '测试科幻片', movieGenre: '科幻', movieRating: 8.5, movieDuration: 110, cinemaName: '南宁测试影院', hallName: 'IMAX厅', startTime: '2099-09-05T20:30:00', basePrice: 40, availableSeats: 5 }] } }] }) }))
  await page.goto('/assistant')
  await page.getByRole('button', { name: /多条件找场次/ }).click()
  for (const text of ['科幻', '110分钟', 'IMAX厅', '¥40']) await expect(page.locator('.primary-results')).toContainText(text)
  await expect(page.locator('.poster-rating')).toHaveText('8.5')
})
