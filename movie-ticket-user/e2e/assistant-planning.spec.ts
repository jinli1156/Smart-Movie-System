import { expect, test, type Page } from '@playwright/test'

const date = '2030-01-01'
const initialPlan = { criteria: { date, people: 2, maxTotal: 80, excludedGenres: ['恐怖'], endBefore: '22:00', usePoints: false }, options: [{ kind: 'showtime', id: 42, movieId: 7, title: '周末喜剧' }] }
const outing = { ok: true, source: 'plan_movie_outing', data: { people: 2, usePoints: false, useCoupons: true, notice: '本系统查询快照，尚未锁座。', plans: [
  { showtimeId: 42, movieId: 7, movieTitle: '周末喜剧', movieGenre: '喜剧', movieRating: 8.2, cinemaName: '测试影城', hallName: '2号厅', startTime: `${date}T19:30:00`, endTime: `${date}T21:10:00`, originalPrice: 90,
    bestPlan: { totalPrice: 70, couponDiscount: 20, pointsUsed: 0, pointsDiscount: 0 }, seats: ['5排5座', '5排6座'], reason: '符合预算和结束时间', seatReason: '中间连座' }
] } }
const frame = (value: unknown) => `event: done\ndata: ${JSON.stringify(value)}\n\n`
const completion = (watchPlan = initialPlan, results: unknown[] = [outing]) => ({ answer: '找到符合条件的观影方案。', source: 'model', toolResults: results, watchPlan, suggestions: ['不用积分，其他条件不变，重新比较', '这些电影都看过了，换一批'] })
async function prepare(page: Page) {
  await page.route('**/api/assistant/capabilities', route => route.fulfill({ json: { code: 200, data: { configured: true, model: 'test-model' } } }))
  await page.route('**/api/assistant/conversations*', route => route.fulfill({ json: { code: 200, data: { items: [], hasMore: false } } }))
}
async function ask(page: Page, text: string) {
  await page.getByLabel('向影票观提问').fill(text)
  await page.getByRole('button', { name: '发送', exact: true }).click()
}

test('guest state, dynamic next actions and condition removal retain the other constraints', async ({ page }) => {
  await prepare(page)
  const requests: any[] = []
  await page.route('**/api/assistant/chat/stream', route => {
    const body = route.request().postDataJSON(); requests.push(body)
    const next = structuredClone(initialPlan)
    if (body.planPatch?.maxTotal === null) delete (next.criteria as any).maxTotal
    return route.fulfill({ contentType: 'text/event-stream', body: frame(completion(next)) })
  })
  await page.goto('/assistant')
  await ask(page, '两个人总预算80，不要恐怖片，十点前结束')
  await expect(page.getByRole('link', { name: '使用此观影方案' })).toBeVisible()
  await page.getByRole('button', { name: '不用积分，其他条件不变，重新比较' }).click()
  await expect.poll(() => requests.length).toBe(2)
  expect(JSON.parse(requests[1].guestPlan).criteria).toEqual(initialPlan.criteria)
  await page.locator('.watch-panel summary').click()
  await page.getByRole('button', { name: '移除条件：优惠后总预算：80元', exact: true }).click()
  await expect.poll(() => requests.length).toBe(3)
  expect(requests[2].planPatch).toEqual({ maxTotal: null })
  await expect(page.getByRole('button', { name: '移除条件：人数：2人', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '移除条件：排除：恐怖', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '移除条件：优惠后总预算：80元', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '新对话', exact: true }).click()
  await ask(page, '重新推荐')
  await expect.poll(() => requests.length).toBe(4)
  expect(JSON.parse(requests[3].guestPlan).criteria).toEqual({})
})

test('complete plan carries party size, asset preferences and real seats; layouts keep composer visible', async ({ page }) => {
  await prepare(page)
  await page.route('**/api/assistant/chat/stream', route => route.fulfill({ contentType: 'text/event-stream', body: frame(completion()) }))
  await page.goto('/assistant'); await ask(page, '推荐一个完整观影方案')
  const link = page.getByRole('link', { name: '使用此观影方案' })
  await expect(link).toBeVisible()
  const target = new URL((await link.getAttribute('href'))!, 'http://localhost')
  expect(target.pathname).toBe('/showtime/42/seats')
  expect(Object.fromEntries(target.searchParams)).toEqual({ people: '2', pricingPoints: 'false', pricingCoupons: 'true', assistantSeats: '5排5座,5排6座' })
  await expect(page.locator('.plan-total')).toHaveText('2人实付 ¥70.00')
  await page.locator('.watch-panel summary').click()
  for (const viewport of [{ width: 1440, height: 900 }, { width: 390, height: 844 }, { width: 320, height: 568 }]) {
    await page.setViewportSize(viewport)
    const composer = await page.locator('.composer').boundingBox()
    expect(composer!.y).toBeGreaterThanOrEqual(0)
    expect(composer!.y + composer!.height).toBeLessThanOrEqual(viewport.height)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    if (viewport.width !== 320) await page.screenshot({ path: `../.local/assistant-upgrade/planning-${viewport.width}.png`, fullPage: true })
  }
})

test('saved conversations restore their plan and long-term exclusions can be saved and removed', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', `x.${btoa(JSON.stringify({ exp: 4102444800 }))}.x`)
    localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'tester', nickname: '测试用户', role: 'USER' }))
  })
  await prepare(page)
  const id = '11111111-1111-1111-1111-111111111111'
  const conversation = { id, title: '两个人看电影', updatedAt: `${date}T12:00:00` }
  await page.route('**/api/assistant/conversations*', route => route.fulfill({ json: { code: 200, data: { items: [conversation], hasMore: false } } }))
  await page.route(`**/api/assistant/conversations/${id}`, route => route.fulfill({ json: { code: 200, data: { conversation, turns: [], hasMore: false, watchPlan: initialPlan } } }))
  const saved: any[] = []
  await page.route('**/api/assistant/preferences', route => {
    if (route.request().method() === 'PUT') saved.push(route.request().postDataJSON())
    return route.fulfill({ json: { code: 200, data: saved.at(-1) || {} } })
  })
  await page.goto('/assistant')
  await page.locator('.watch-panel summary').click()
  await expect(page.getByRole('button', { name: '移除条件：排除：恐怖', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '保存为长期排除偏好' }).click()
  await expect(page.getByRole('button', { name: '移除长期排除：恐怖' })).toBeVisible()
  expect(saved[0]).toEqual({ excludedGenres: ['恐怖'], excludedMovieIds: [] })
  await page.getByRole('button', { name: '移除长期排除：恐怖' }).click()
  await expect(page.getByText('暂无长期排除偏好', { exact: true })).toBeVisible()
  expect(saved[1]).toEqual({ excludedGenres: [], excludedMovieIds: [] })
  await expect(page.getByRole('button', { name: '移除条件：排除：恐怖', exact: true })).toBeVisible()
})

test('empty feasible plans suppress previous search cards that did not satisfy all constraints', async ({ page }) => {
  await prepare(page)
  const earlier = { ok: true, source: 'search_showtimes', data: { items: [{ id: 99, movieTitle: '不符合预算的电影', basePrice: 99 }] } }
  const empty = { ...outing, data: { ...outing.data, plans: [] } }
  await page.route('**/api/assistant/chat/stream', route => route.fulfill({ contentType: 'text/event-stream', body: frame({ ...completion(initialPlan, [earlier, empty]), answer: '符合预算的场次没有连座。' }) }))
  await page.goto('/assistant'); await ask(page, '两个人需要连座')
  await expect(page.locator('.bubble').last()).toContainText('没有连座')
  await expect(page.locator('.primary-results .result-card')).toHaveCount(0)
})
