import { expect, test, type Page, type Route } from '@playwright/test'

const frame = (event: string, data: unknown) => `event: ${event}\r\ndata: ${JSON.stringify(data)}\r\n\r\n`
const result = { ok: true, source: 'search_movies', queriedAt: '2026-09-05T12:30:00', data: { items: [{ id: 1, title: '测试科幻片', genre: '科幻', rating: 9, status: 1 }], total: 1, page: 1 } }
const done = (answer: string, toolResults: unknown[] = []) => frame('done', { answer, toolResults, suggestions: ['查一下这部电影的场次'], source: 'model' })
async function prepare(page: Page) {
  await page.route('**/api/assistant/conversations', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data: { items: [], hasMore: false, page: 1 } }) }))
  await page.route('**/api/assistant/capabilities', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data: { configured: true, model: 'deepseek-v4-flash', streaming: true, vision: false } }) }))
}
const stream = (route: Route, body: string) => route.fulfill({ contentType: 'text/event-stream', body })

test('dedicated page shows final poster results, safe links and multi-turn history', async ({ page }) => {
  await prepare(page)
  const requests: any[] = []
  await page.route('**/api/assistant/chat/stream', route => {
    requests.push(route.request().postDataJSON())
    return stream(route, frame('status', { message: '查询中' }) + frame('tool_result', result) + frame('delta', { text: '推荐测试科幻片。' }) + done('推荐测试科幻片。', [result]))
  })
  await page.goto('/assistant')
  await expect(page.getByRole('heading', { name: '今天，想看点什么？' })).toBeVisible()
  await expect(page.getByRole('button', { name: '打开影票观' })).toHaveCount(0)
  await page.getByLabel('向影票观提问').fill('推荐科幻电影')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.bubble').last()).toHaveText('推荐测试科幻片。')
  await expect(page.getByRole('link', { name: '了解影片' })).toHaveAttribute('href', '/movie/1')
  await expect(page.locator('.query-results')).toHaveCount(0)
  await expect(page.getByAltText('测试科幻片海报')).toBeVisible()
  await page.getByRole('button', { name: '查一下这部电影的场次' }).click()
  await expect.poll(() => requests.length).toBe(2)
  expect(requests[1].history).toEqual([{ role: 'user', content: '推荐科幻电影' }, { role: 'assistant', content: '推荐测试科幻片。' }])
  expect(requests[1]).not.toHaveProperty('userId')
})

test('anonymous order query offers login and never shows invented orders', async ({ page }) => {
  await prepare(page)
  const denied = { ok: false, code: 'LOGIN_REQUIRED', message: '请先登录，再查询自己的订单。' }
  await page.route('**/api/assistant/chat/stream', route => stream(route, frame('tool_result', denied) + done('请先登录。', [denied])))
  await page.goto('/assistant')
  await page.getByRole('button', { name: /我的订单/ }).click()
  await expect(page.getByRole('link', { name: '去登录', exact: true })).toBeVisible()
  await expect(page.locator('.result-card')).toHaveCount(0)
})

test('truncated stream hides partial answer and offers retry draft', async ({ page }) => {
  await prepare(page)
  await page.route('**/api/assistant/chat/stream', route => stream(route, frame('delta', { text: '部分回答' })))
  await page.goto('/assistant')
  await page.getByLabel('向影票观提问').fill('测试中断')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.bubble').last()).toContainText('连接中断，回答未完成')
  await expect(page.getByLabel('向影票观提问')).toHaveValue('测试中断')
  await expect(page.locator('.bubble').last()).not.toContainText('部分回答')
})

test('new conversation cancels pending work without restoring private draft', async ({ page }) => {
  await prepare(page)
  let release: () => void = () => {}
  const pending = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/assistant/chat/stream', async route => { await pending; await stream(route, done('旧订单信息')).catch(() => {}) })
  await page.goto('/assistant')
  await page.getByLabel('向影票观提问').fill('我的私人订单')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByRole('button', { name: '停止回答' })).toBeVisible()
  await page.getByRole('button', { name: '新对话' }).click()
  release()
  await expect(page.getByLabel('向影票观提问')).toHaveValue('')
  await expect(page.locator('.message')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '今天，想看点什么？' })).toBeVisible()
})

test('model text is rendered as text and cannot execute HTML', async ({ page }) => {
  await prepare(page)
  await page.route('**/api/assistant/chat/stream', route => stream(route, done('<img src=x onerror="window.hacked=true">')))
  await page.goto('/assistant')
  await page.getByRole('button', { name: /发现好电影/ }).click()
  await expect(page.locator('.bubble').last()).toContainText('<img')
  await expect(page.locator('.bubble img')).toHaveCount(0)
})

test('desktop and mobile layouts fit viewport', async ({ page }) => {
  await prepare(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/assistant')
  await expect(page.getByRole('heading', { name: '今天，想看点什么？' })).toBeVisible()
  await expect(page.getByText('DeepSeek · deepseek-v4-flash', { exact: true })).toBeVisible()
  await page.screenshot({ path: '../.local/assistant-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByLabel('向影票观提问')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: '../.local/assistant-mobile.png', fullPage: true })
  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }, { width: 320, height: 568 }]) {
    await page.setViewportSize(viewport)
    const composer = await page.locator('.composer').boundingBox()
    expect(composer).not.toBeNull()
    expect(composer!.y).toBeGreaterThanOrEqual(0)
    expect(composer!.y + composer!.height).toBeLessThanOrEqual(viewport.height)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    const guideToggle = page.getByRole('button', { name: /使用指南与账号说明/ })
    if (await guideToggle.isVisible()) await guideToggle.click()
    await expect(page.getByRole('heading', { name: '你的观影，一站问清' })).toBeVisible()
    if (await guideToggle.isVisible()) await guideToggle.click()
    if (viewport.width >= 1200) {
      const guide = await page.locator('.assistant-guide').boundingBox()
      const chat = await page.locator('.chat-panel').boundingBox()
      expect(guide!.x).toBeGreaterThanOrEqual(chat!.x + chat!.width - 1)
    }
  }

})

test('budget recommendation shows one matching card and collapses unrelated intermediate results', async ({ page }) => {
  await prepare(page)
  const affordable = { ok: true, source: 'search_showtimes', data: { items: [{ id: 99, movieTitle: '预算内电影', cinemaName: '测试影城', startTime: '2026-09-05 18:00:00', basePrice: 38, availableSeats: 12 }], total: 4, page: 1, size: 1, maxPrice: 40, priceExclusive: true } }
  await page.route('**/api/assistant/chat/stream', route => stream(route,
    frame('delta', { text: '让我逐部查电影' }) + frame('answer_reset', {}) + frame('tool_result', result) + frame('tool_result', affordable) + done('推荐《预算内电影》，测试影城18:00场，38元，符合低于40元的预算。', [result, affordable])))
  await page.goto('/assistant')
  await page.getByLabel('向影票观提问').fill('推荐一部票价40元以下的电影')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.primary-results .result-card')).toHaveCount(1)
  await expect(page.getByRole('link', { name: '去选座' })).toHaveAttribute('href', '/showtime/99/seats')
  await expect(page.locator('details.query-results')).toHaveCount(0)
  await expect(page.locator('.bubble').last()).not.toContainText('让我')
  await expect(page.getByText('测试科幻片', { exact: true })).not.toBeVisible()
  await page.screenshot({ path: '../.local/assistant-budget.png', fullPage: true })
})

test('no affordable showtimes does not display previous over-budget candidates or suggest paging empty results', async ({ page }) => {
  await prepare(page)
  const empty = { ok: true, source: 'search_showtimes', data: { items: [], total: 0, page: 1, size: 1, maxPrice: 40 } }
  await page.route('**/api/assistant/chat/stream', route => stream(route, done('今天没有低于40元的可售场次。要换个日期吗？', [result, empty])))
  await page.goto('/assistant')
  await page.getByLabel('向影票观提问').fill('推荐一部40元以下电影')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.bubble').last()).toContainText('没有低于40元')
  await expect(page.locator('.primary-results .result-card')).toHaveCount(0)
  await expect(page.locator('.query-results')).toHaveCount(0)
})


test('intermediate events remain hidden until final poster grid arrives', async ({ page }) => {
  await prepare(page)
  await page.route('**/test-posters/*', async route => {
    const { readFile } = await import('node:fs/promises')
    const body = await readFile('../movie-ticket-backend/uploads/poster-latest-1291561.jpg')
    await route.fulfill({ contentType: 'image/jpeg', body })
  })
  await page.addInitScript(() => {
    const originalFetch = window.fetch.bind(window)
    window.fetch = (input, init) => {
      if (!String(input).includes('/api/assistant/chat/stream')) return originalFetch(input, init)
      const encode = (event: string, data: unknown) => new TextEncoder().encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`)
      return Promise.resolve(new Response(new ReadableStream({
        start(controller) {
          controller.enqueue(encode('status', { message: '正在查询本系统数据…' }))
          controller.enqueue(encode('delta', { text: '让我先查询影片，再分析偏好。' }))
          controller.enqueue(encode('tool_result', { ok: false, code: 'LOGIN_REQUIRED', message: '中间查询状态' }))
          ;(window as any).finishAssistant = () => {
            controller.enqueue(encode('done', { answer: '这几部电影都值得加入你的片单。', source: 'model', suggestions: [], toolResults: [{ ok: true, source: 'recommend_movies', data: { movies: [
              { id: 1, title: '千与千寻', poster: '/test-posters/1.jpg', genre: '动画 · 奇幻', rating: 9.4, duration: 125, reason: '温柔而充满想象力的成长旅程。' },
              { id: 2, title: '奇幻之旅', poster: '/test-posters/2.jpg', genre: '冒险', rating: 8.6, duration: 110 },
              { id: 3, title: '夏日故事', poster: '/test-posters/missing.jpg', genre: '剧情', status: 2 }
            ] } }] }))
            controller.close()
          }
        }
      }), { headers: { 'Content-Type': 'text/event-stream' } }))
    }
  })
  await page.route('**/test-posters/missing.jpg', route => route.fulfill({ status: 404 }))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/assistant')
  await page.getByLabel('向影票观提问').fill('推荐几部电影')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('正在准备回答…')).toBeVisible()
  await expect(page.locator('.message.assistant .bubble')).toHaveCount(0)
  await expect(page.locator('.query-results')).toHaveCount(0)
  await expect(page.getByText('让我先查询影片，再分析偏好。')).toHaveCount(0)
  await expect(page.getByRole('link', { name: '去登录', exact: true })).toHaveCount(0)
  await page.evaluate(() => (window as any).finishAssistant())
  await expect(page.locator('.movie-result')).toHaveCount(3)
  await expect(page.getByText('这几部电影都值得加入你的片单。')).toBeVisible()
  await expect(page.getByAltText('千与千寻海报')).toHaveAttribute('src', '/test-posters/1.jpg')
  await expect(page.getByAltText('夏日故事海报')).toHaveAttribute('src', '/poster-placeholder.svg')
  const cards = page.locator('.movie-result')
  const first = await cards.nth(0).boundingBox()
  const third = await cards.nth(2).boundingBox()
  expect(third!.y).toBe(first!.y)
  expect(third!.x).toBeGreaterThan(first!.x)
  await page.screenshot({ path: '../.local/assistant-poster-grid-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await cards.first().scrollIntoViewIfNeeded()
  const mobileFirst = await cards.nth(0).boundingBox()
  const mobileSecond = await cards.nth(1).boundingBox()
  const mobileThird = await cards.nth(2).boundingBox()
  expect(mobileFirst!.y).toBe(mobileSecond!.y)
  expect(mobileThird!.y).toBeGreaterThan(mobileSecond!.y)
  await page.screenshot({ path: '../.local/assistant-poster-grid-mobile.png', fullPage: true })
})
