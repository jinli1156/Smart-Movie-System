import { expect, test, type Page } from '@playwright/test'

const token = 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig'
const frame = (event: string, data: unknown) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`
const firstId = '11111111-1111-4111-8111-111111111111'
const secondId = '22222222-2222-4222-8222-222222222222'
const oldTurn = { id: 1, question: '上次问的电影', answer: '已保存的电影推荐', source: 'model', status: 'complete', suggestions: [], createdAt: '2026-09-05T12:00:00', toolResults: [{ ok: true, source: 'search_showtimes', data: { items: [{ id: 101, movieTitle: '历史电影', cinemaName: '测试影院', startTime: '2026-09-05T20:00:00', basePrice: 40, availableSeats: 5 }] } }] }

async function prepare(page: Page, seed = true) {
  await page.addInitScript(value => {
    if (sessionStorage.getItem('history-test-guest')) return
    localStorage.setItem('token', value)
    localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'viewer', nickname: '测试用户', role: 'USER' }))
  }, token)
  const sessions: any[] = seed ? [{ id: firstId, title: '上次问的电影', updatedAt: '2026-09-05T12:00:00' }] : []
  const turns: Record<string, any[]> = seed ? { [firstId]: [oldTurn] } : {}
  const requests: any[] = []
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    const json = (data: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
    if (path === '/api/assistant/capabilities') return json({ configured: true, model: 'test', streaming: true, vision: false })
    if (path === '/api/assistant/conversations') return json({ items: sessions, hasMore: false, page: 1 })
    if (path.startsWith('/api/assistant/conversations/')) {
      const id = path.split('/').pop()!
      if (route.request().method() === 'DELETE') {
        sessions.splice(sessions.findIndex(s => s.id === id), 1); delete turns[id]; return json(null)
      }
      return json({ conversation: sessions.find(s => s.id === id), turns: turns[id] || [], hasMore: false })
    }
    if (path === '/api/assistant/chat/stream') {
      const body = route.request().postDataJSON(); requests.push(body)
      const id = body.conversationId || secondId
      if (!turns[id]) { sessions.unshift({ id, title: body.message, updatedAt: '2026-09-05T13:00:00' }); turns[id] = [] }
      turns[id].push({ ...oldTurn, id: turns[id].length + 2, question: body.message, answer: '新的已保存回答', toolResults: [] })
      return route.fulfill({ contentType: 'text/event-stream', body: frame('conversation', { id }) + frame('done', { answer: '新的已保存回答', source: 'model', suggestions: [], toolResults: [] }) })
    }
    return json([])
  })
  return { sessions, turns, requests }
}

test('restores conversation and query cards after reload, then continues same conversation', async ({ page }) => {
  const state = await prepare(page)
  await page.goto('/assistant')
  await expect(page.locator('.bubble').last()).toHaveText('已保存的电影推荐')
  await expect(page.getByText('历史查询快照，当前价格、余票和优惠请重新查询。')).toBeVisible()
  await expect(page.locator('.primary-results').getByRole('link', { name: /去选座/ })).toHaveAttribute('href', '/showtime/101/seats')
  await page.reload()
  await expect(page.locator('.bubble').last()).toHaveText('已保存的电影推荐')
  await page.getByLabel('向影票观提问').fill('再查一次余票')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.bubble').last()).toHaveText('新的已保存回答')
  expect(state.requests[0].conversationId).toBe(firstId)
  expect(state.requests[0].clientMessageId).toMatch(/^[\da-f-]{36}$/)
  await page.reload()
  await expect(page.locator('.message')).toHaveCount(4)
})

test('new conversation preserves previous history and deletion removes only selected conversation', async ({ page }) => {
  const state = await prepare(page)
  await page.goto('/assistant')
  await expect(page.locator('.bubble').last()).toHaveText('已保存的电影推荐')
  await page.getByRole('button', { name: '新对话', exact: true }).click()
  await expect(page.locator('.message')).toHaveCount(0)
  await page.getByLabel('向影票观提问').fill('新的话题')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.locator('.bubble').last()).toHaveText('新的已保存回答')
  expect(state.requests[0].conversationId).toBeUndefined()
  if (await page.getByRole('button', { name: '历史会话', exact: true }).isVisible()) await page.getByRole('button', { name: '历史会话', exact: true }).click()
  await page.getByRole('button', { name: /上次问的电影.*2026/ }).click()
  await expect(page.locator('.bubble').last()).toHaveText('已保存的电影推荐')
  await page.getByRole('button', { name: '删除会话：上次问的电影', exact: true }).click()
  await page.getByRole('button', { name: '确认删除', exact: true }).click()
  await expect(page.locator('.message')).toHaveCount(0)
  expect(state.sessions.map(s => s.id)).toEqual([secondId])
  await page.reload()
  await expect(page.locator('.bubble').last()).toHaveText('新的已保存回答')
})

test('logout hides persisted content and guest reload does not restore private history', async ({ page }) => {
  await prepare(page)
  await page.setViewportSize({ width: 1440, height: 1200 })
  await page.goto('/assistant')
  await expect(page.locator('.bubble').last()).toHaveText('已保存的电影推荐')
  await page.evaluate(() => sessionStorage.setItem('history-test-guest', '1'))
  await page.getByText('退出登录', { exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.goto('/assistant')
  await expect(page.locator('.conversation-history')).toHaveCount(0)
  await expect(page.locator('.message')).toHaveCount(0)
})

test('pending or interrupted replies are restored visibly without pretending completion', async ({ page }) => {
  const state = await prepare(page)
  state.turns[firstId] = [{ ...oldTurn, status: 'pending', source: 'unavailable', answer: '', toolResults: [] }]
  await page.goto('/assistant')
  await expect(page.locator('.bubble').last()).toContainText('此回答尚未完成')
  await expect(page.locator('.message-label').last()).toContainText('回答未完成')
})

test('mobile history fits viewport and can reopen saved conversation', async ({ page }) => {
  await prepare(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/assistant')
  await expect(page.locator('.bubble').last()).toHaveText('已保存的电影推荐')
  if (await page.getByRole('button', { name: '历史会话', exact: true }).isVisible()) await page.getByRole('button', { name: '历史会话', exact: true }).click()
  await expect(page.getByRole('button', { name: /上次问的电影.*2026/ })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: '../.local/assistant-history-mobile.png', fullPage: true })
  await page.getByRole('button', { name: /上次问的电影.*2026/ }).click()
  await expect(page.locator('.conversation-sidebar')).not.toBeVisible()
  await expect(page.locator('.bubble').last()).toHaveText('已保存的电影推荐')
  await page.getByRole('button', { name: '历史会话', exact: true }).click()
  await page.getByRole('button', { name: '新对话', exact: true }).click()
  await expect(page.locator('.conversation-sidebar')).not.toBeVisible()
  await expect(page.getByLabel('向影票观提问')).toBeFocused()
})


test('desktop history stays to the left while the composer remains on screen', async ({ page }) => {
  const state = await prepare(page)
  for (let index = 0; index < 30; index++) state.sessions.push({ id: `extra-${index}`, title: `历史电影对话 ${index}`, updatedAt: '2026-09-05T12:00:00' })
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/assistant')
  await expect(page.locator('.bubble').last()).toHaveText('已保存的电影推荐')
  const sidebar = await page.locator('.conversation-sidebar').boundingBox()
  const main = await page.locator('.assistant-main').boundingBox()
  expect(sidebar!.x + sidebar!.width).toBeLessThanOrEqual(main!.x)
  const composer = await page.locator('.composer').boundingBox()
  expect(composer!.y + composer!.height).toBeLessThanOrEqual(900)
  await page.locator('.conversation-list').evaluate(el => { el.scrollTop = el.scrollHeight })
  await expect(page.getByRole('button', { name: '新对话', exact: true })).toBeVisible()
  await page.screenshot({ path: '../.local/assistant-history-desktop.png', fullPage: true })
})
