import { expect, test, type Page } from '@playwright/test'

async function setup(page: Page, loggedIn: boolean) {
  if (loggedIn) await page.addInitScript(() => {
    localStorage.setItem('token', 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig')
    localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'user1', nickname: '测试用户', role: 'USER' }))
  })
  await page.route(/fonts\.(googleapis|gstatic)\.com/, route => route.abort())
  const statuses: string[] = []
  const order = { id: 1, orderNo: 'MEMORY-123456', movieTitle: '星际穿越', cinemaName: '星空影城', hallName: 'IMAX 厅', showtime: '2026-10-30T19:30:00', seats: '5排6座', seatCount: 1, totalPrice: 48, status: 1, statusText: '已支付', createTime: '2026-10-30T18:00:00' }
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const url = new URL(route.request().url())
    const json = (data: unknown) => route.fulfill({ json: { code: 200, data } })
    if (url.pathname === '/api/assistant/capabilities') return json({ configured: true, model: 'deepseek-v4-flash' })
    if (url.pathname === '/api/assistant/conversations') return json({ items: [], hasMore: false })
    if (url.pathname === '/api/order/my') { statuses.push(url.searchParams.get('status') || 'all'); return json({ records: [order], total: 1 }) }
    if (url.pathname === '/api/order/MEMORY-123456') return json(order)
    if (url.pathname === '/api/souvenirs/capabilities') return json({ available: false, theme: '星空科幻', dailyLimit: 6, message: '服务暂未开放' })
    return json([])
  })
  return statuses
}

test('助手右侧纪念票入口直达已支付订单，并继续打开纪念票', async ({ page }) => {
  const statuses = await setup(page, true)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/assistant')
  const card = page.getByRole('region', { name: 'AI 观影纪念票' })
  await expect(card).toBeVisible()
  const cardBox = await card.boundingBox(), chatBox = await page.locator('.chat-panel').boundingBox()
  expect(cardBox!.x).toBeGreaterThanOrEqual(chatBox!.x + chatBox!.width)
  await expect(card).toContainText('非入场凭证')
  await page.screenshot({ path: '../.local/assistant-souvenir-entry.png', fullPage: true, animations: 'disabled' })
  await card.getByRole('link', { name: '选择订单，制作纪念票' }).click()
  await expect(page).toHaveURL(/\/orders\?status=1$/)
  await expect(page.locator('.tab-btn.active')).toHaveText('已支付')
  await expect.poll(() => statuses[0]).toBe('1')
  await page.getByRole('button', { name: 'AI 观影纪念票', exact: true }).click()
  await expect(page.getByRole('dialog', { name: 'AI 观影纪念票' })).toBeVisible()
})

test('手机指南中的入口可见，访客登录后保留已支付订单目标', async ({ page }) => {
  await setup(page, false)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/assistant')
  await page.getByRole('button', { name: /使用指南与账号说明/ }).click()
  const card = page.getByRole('region', { name: 'AI 观影纪念票' })
  await expect(card).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await card.getByRole('link', { name: '登录后制作纪念票' }).click()
  await expect(page).toHaveURL(/\/login\?redirect=/)
  expect(new URL(page.url()).searchParams.get('redirect')).toBe('/orders?status=1')
})
