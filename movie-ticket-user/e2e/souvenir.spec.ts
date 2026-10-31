import { expect, test, type Page } from '@playwright/test'

const order = {
  id: 1, orderNo: 'REAL-ORDER-123456', userId: 7, movieId: 11, movieTitle: '星际穿越',
  cinemaName: '响指星空影城', hallName: 'IMAX 厅', showtime: '2026-10-30T19:30:00',
  seats: '5排6座,5排7座', seatCount: 2, totalPrice: 96, status: 1, statusText: '已支付',
  createTime: '2026-10-30T18:00:00', ticketCode: 'SECRET-ENTRY-CODE'
}
const ticket = {
  id: 'test-ticket', style: 'STARFIELD', note: '与朋友收藏这一刻', status: 'SUCCEEDED',
  facts: { movieTitle: order.movieTitle, cinemaName: order.cinemaName, hallName: order.hallName, showtime: order.showtime },
  headline: '将光影寄往星海', caption: '把银幕上的片刻，收藏进无垠宇宙。',
  errorMessage: null, retryable: false, createdAt: '2026-10-30T18:01:00'
}
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j2ioAAAAASUVORK5CYII=', 'base64')

async function setup(page: Page, status = 1, available = true) {
  let saved: typeof ticket | null = null
  const submissions: any[] = []
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.addInitScript(() => {
    localStorage.setItem('token', 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig')
    localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'user1', nickname: '测试用户', role: 'USER' }))
  })
  await page.route(/fonts\.(googleapis|gstatic)\.com/, route => route.abort())
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    const json = (data: unknown) => route.fulfill({ json: { code: 200, data } })
    if (path === '/api/order/REAL-ORDER-123456') return json({ ...order, status })
    if (path === '/api/souvenirs/capabilities') return json({ available, theme: '星空科幻', dailyLimit: 6, message: 'AI 纪念票服务暂未开放，请稍后再来' })
    if (path === '/api/souvenirs/orders/REAL-ORDER-123456') {
      if (route.request().method() === 'POST') {
        submissions.push(route.request().postDataJSON()); saved = { ...ticket, status: 'QUEUED' }; return json(saved)
      }
      return json(saved ? [saved] : [])
    }
    if (path === '/api/souvenirs/test-ticket/file') {
      expect(route.request().headers().authorization).toContain('Bearer ')
      return route.fulfill({ contentType: 'image/png', body: png })
    }
    return json([])
  })
  return { submissions, errors, ready: () => { saved = { ...ticket } }, fail: () => { saved = { ...ticket, status: 'FAILED', errorMessage: '画面已生成，保存未完成，可重试保存', retryable: true } } }
}

test('已支付订单固定展示星空主题，提交任务后关闭重开可继续查看并下载', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/order/REAL-ORDER-123456')
  await page.getByRole('button', { name: 'AI 观影纪念票', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'AI 观影纪念票' })
  await expect(dialog.getByText('星空科幻', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('radio')).toHaveCount(0)
  await expect(dialog.getByText('5排6座,5排7座', { exact: true })).toBeVisible()
  await expect(dialog).not.toContainText('SECRET-ENTRY-CODE')
  await dialog.getByLabel('此刻的心情').fill('与朋友收藏这一刻')
  await dialog.getByRole('button', { name: '生成我的纪念票' }).click()
  await expect(dialog.getByText('已加入创作队列')).toBeVisible()
  expect(state.submissions).toEqual([{ note: '与朋友收藏这一刻', retry: false }])
  await dialog.getByRole('button', { name: 'Close this dialog' }).click()
  state.ready()
  await page.getByRole('button', { name: 'AI 观影纪念票', exact: true }).click()
  await expect(dialog.getByAltText('星际穿越的 AI 观影纪念票')).toBeVisible()
  const download = page.waitForEvent('download')
  await dialog.getByRole('button', { name: '保存纪念票', exact: true }).click()
  expect((await download).suggestedFilename()).toBe('星际穿越-观影纪念票.png')
  await page.evaluate(() => Object.defineProperty(navigator, 'canShare', { configurable: true, value: () => false }))
  const sharedDownload = page.waitForEvent('download')
  await dialog.getByRole('button', { name: '分享', exact: true }).click()
  expect((await sharedDownload).suggestedFilename()).toBe('星际穿越-观影纪念票.png')
  expect(state.submissions).toHaveLength(1)
  expect(state.errors).toEqual([])
})

test('支持系统分享时传递 PNG 文件，不公开订单页面链接', async ({ page }) => {
  const state = await setup(page)
  state.ready()
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'canShare', { configurable: true, value: () => true })
    Object.defineProperty(navigator, 'share', { configurable: true, value: async (data: ShareData) => {
      ;(window as any).__souvenirShare = { title: data.title, type: data.files?.[0].type, url: data.url }
    } })
  })
  await page.goto('/order/REAL-ORDER-123456?souvenir=1')
  const dialog = page.getByRole('dialog', { name: 'AI 观影纪念票' })
  await expect(dialog.getByRole('button', { name: '分享', exact: true })).toBeEnabled()
  await dialog.getByRole('button', { name: '分享', exact: true }).click()
  expect(await page.evaluate(() => (window as any).__souvenirShare)).toEqual({ title: '我的 AI 观影纪念票', type: 'image/png', url: undefined })
})

test('手机票面不横向溢出，服务不可用时保留说明并禁用生成', async ({ page }) => {
  const state = await setup(page, 1, false)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/order/REAL-ORDER-123456?souvenir=1')
  const dialog = page.getByRole('dialog', { name: 'AI 观影纪念票' })
  await expect(dialog.getByText('AI 纪念票服务暂未开放，请稍后再来')).toBeVisible()
  await expect(dialog.getByRole('button', { name: '生成我的纪念票' })).toBeDisabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
  await dialog.screenshot({ path: '../.local/souvenir-mobile.png', animations: 'disabled' })
  expect(state.submissions).toHaveLength(0)
  expect(state.errors).toEqual([])
})

test('生成中自动更新并支持手动恢复失败的保存任务', async ({ page }) => {
  const state = await setup(page)
  state.fail()
  await page.goto('/order/REAL-ORDER-123456?souvenir=1')
  const dialog = page.getByRole('dialog', { name: 'AI 观影纪念票' })
  await expect(dialog.getByRole('alert')).toContainText('保存未完成')
  await dialog.getByRole('button', { name: '重试生成 / 继续保存' }).click()
  state.ready()
  await expect(dialog.getByRole('button', { name: '保存纪念票', exact: true })).toBeEnabled({ timeout: 12_000 })
  expect(state.submissions[0].retry).toBe(true)
  await dialog.screenshot({ path: '../.local/souvenir-desktop.png', animations: 'disabled' })
})

for (const status of [0, 2, 3]) test(`订单状态 ${status} 不显示纪念票入口`, async ({ page }) => {
  await setup(page, status)
  await page.goto('/order/REAL-ORDER-123456?souvenir=1')
  await expect(page.getByRole('heading', { name: '已支付' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'AI 观影纪念票', exact: true })).toHaveCount(0)
  await expect(page.getByRole('dialog')).toHaveCount(0)
})
