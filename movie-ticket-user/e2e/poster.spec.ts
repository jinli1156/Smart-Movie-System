import { expect, test, type Page } from '@playwright/test'

const styles = [
  { id: 'CYBERPUNK', name: '赛博朋克', description: '霓虹都市' }, { id: 'FANTASY', name: '东方神话', description: '东方奇境' },
  { id: 'SCIFI', name: '星际科幻', description: '深空星云' }, { id: 'ANIME', name: '日系动画', description: '明亮色彩' },
  { id: 'RETRO', name: '复古胶片', description: '胶片颗粒' }, { id: 'SUSPENSE', name: '悬疑电影', description: '迷雾悬念' }
]
const fixture = {
  id: 'poster-test', movieTitle: '哪吒', style: 'CYBERPUNK', prompt: '我是主角', ratio: '3:4', hasPortrait: false,
  status: 'SUCCEEDED', errorMessage: null as string | null, retryable: false, createdAt: '2026-10-31T12:00:00'
}
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j2ioAAAAASUVORK5CYII=', 'base64')

async function setup(page: Page, options: { available?: boolean; photoSupported?: boolean; saved?: typeof fixture | null; failFirst?: boolean } = {}) {
  let saved = options.saved || null, failFirst = options.failFirst
  const submissions: string[] = [], errors: string[] = []
  let retries = 0
  page.on('pageerror', e => errors.push(e.message))
  await page.addInitScript(() => {
    localStorage.setItem('token', 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig')
    localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'user1', nickname: '海报测试', role: 'USER' }))
  })
  await page.route(/fonts\.(googleapis|gstatic)\.com/, route => route.abort())
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    const json = (data: unknown) => route.fulfill({ json: { code: 200, data } })
    if (path === '/api/posters/capabilities') return json({ available: options.available ?? true, photoSupported: options.photoSupported ?? true, styles, dailyLimit: 6, remaining: saved ? 5 : 6, message: 'AI 海报服务暂未开放，请稍后再来' })
    if (path === '/api/posters') {
      if (route.request().method() === 'POST') {
        submissions.push(route.request().postDataBuffer()!.toString('utf8'))
        if (failFirst) { failFirst = false; return route.abort('connectionreset') }
        saved = { ...fixture, status: 'QUEUED' }; return json(saved)
      }
      return json(saved ? [saved] : [])
    }
    if (path === '/api/posters/poster-test/retry') { retries++; saved = { ...fixture, status: 'QUEUED' }; return json(saved) }
    if (path === '/api/posters/poster-test/file') {
      expect(route.request().headers().authorization).toContain('Bearer ')
      return route.fulfill({ contentType: 'image/png', body: png })
    }
    return json([])
  })
  return { submissions, errors, ready: () => { saved = { ...fixture } }, retries: () => retries }
}

test('一句话创意和照片一起提交，自动更新后可下载及分享海报', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/posters')
  await page.getByRole('button', { name: '试试「赛博哪吒」灵感' }).click()
  await page.getByLabel('上传人物参考照片').setInputFiles({ name: 'my-portrait.png', mimeType: 'image/png', buffer: png })
  await expect(page.getByAltText('已选择的人物参考照片')).toBeVisible()
  await page.getByRole('radio', { name: '东方神话', exact: true }).check()
  await page.getByRole('radio', { name: '横版 16:9' }).check()
  await page.getByRole('button', { name: '生成我的电影海报' }).click()
  await expect(page.getByRole('heading', { name: '创意已就位，等待开拍' })).toBeVisible()
  expect(state.submissions).toHaveLength(1)
  expect(state.submissions[0]).toContain('name="photo"; filename="my-portrait.png"')
  expect(state.submissions[0]).toContain('"style":"FANTASY"')
  expect(state.submissions[0]).toContain('"ratio":"16:9"')
  expect(state.submissions[0]).toContain('"movieTitle":"哪吒"')
  state.ready()
  await expect(page.getByAltText('哪吒的专属电影海报')).toBeVisible({ timeout: 12_000 })
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载 PNG 海报' }).click()
  expect((await download).suggestedFilename()).toBe('哪吒-专属海报.png')
  await page.evaluate(() => Object.defineProperty(navigator, 'canShare', { configurable: true, value: () => false }))
  const shareDownload = page.waitForEvent('download')
  await page.getByRole('button', { name: '分享', exact: true }).click()
  expect((await shareDownload).suggestedFilename()).toBe('哪吒-专属海报.png')
  expect(state.submissions).toHaveLength(1); expect(state.errors).toEqual([])
})

test('电影主题由详情入口带入，手机画面不横向溢出', async ({ page }) => {
  const state = await setup(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/posters?movie=星际穿越')
  await expect(page.getByLabel('电影主题')).toHaveValue('星际穿越')
  await expect(page.getByRole('radio', { name: '赛博朋克', exact: true })).toBeChecked()
  await expect(page.getByRole('button', { name: '生成我的电影海报' })).toBeEnabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: '../.local/poster-studio-mobile.png', fullPage: true, animations: 'disabled' })
  expect(state.errors).toEqual([])
})

test('未配置服务时禁用创作，仍能查看和下载历史作品', async ({ page }) => {
  await setup(page, { available: false, saved: fixture })
  await page.goto('/posters')
  await expect(page.getByText('AI 海报服务暂未开放，请稍后再来')).toBeVisible()
  await expect(page.getByRole('button', { name: '生成我的电影海报' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '下载 PNG 海报' })).toBeVisible()
})

test('支持恢复失败的保存任务，不自动重试结果未知的请求', async ({ page }) => {
  const state = await setup(page, { saved: { ...fixture, status: 'FAILED', errorMessage: '画面已生成，保存未完成，可以继续保存', retryable: true } })
  await page.goto('/posters')
  await expect(page.getByText('画面已生成，保存未完成，可以继续保存')).toBeVisible()
  await page.getByRole('button', { name: '重试生成 / 继续保存' }).click()
  state.ready()
  await expect(page.getByRole('button', { name: '下载 PNG 海报' })).toBeVisible({ timeout: 12_000 })
  expect(state.retries()).toBe(1); expect(state.submissions).toHaveLength(0)
})

test('网络中断后重复提交相同创意，使用相同请求标识', async ({ page }) => {
  const state = await setup(page, { failFirst: true })
  await page.goto('/posters?movie=哪吒')
  await page.getByRole('button', { name: '生成我的电影海报' }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await page.getByRole('button', { name: '生成我的电影海报' }).click()
  await expect(page.getByRole('heading', { name: '创意已就位，等待开拍' })).toBeVisible()
  expect(state.submissions).toHaveLength(2)
  const ids = state.submissions.map(body => body.match(/"requestId":"([a-f0-9-]+)"/)?.[1])
  expect(ids[0]).toBeTruthy(); expect(ids[0]).toBe(ids[1])
})

test('未知结果不展示重试按钮，刷新和重进页面不会再提交生成', async ({ page }) => {
  const state = await setup(page, { saved: { ...fixture, status: 'FAILED', errorMessage: '生图结果尚未确定，请先核实，避免重复生成', retryable: false } })
  await page.goto('/posters')
  await expect(page.getByText('生图结果尚未确定，请先核实，避免重复生成')).toBeVisible()
  await expect(page.getByRole('button', { name: '重试生成 / 继续保存' })).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', { name: '这次创作暂未完成' })).toBeVisible()
  expect(state.submissions).toHaveLength(0); expect(state.retries()).toBe(0)
})

test('照片可移除，非法文件被拒绝，桌面创作预览完整展示', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/posters')
  await expect(page.getByRole('button', { name: '生成我的电影海报' })).toBeDisabled()
  await page.getByLabel('上传人物参考照片').setInputFiles({ name: 'me.png', mimeType: 'image/png', buffer: png })
  await page.getByRole('button', { name: '移除照片' }).click()
  await expect(page.getByAltText('已选择的人物参考照片')).toHaveCount(0)
  await page.getByLabel('上传人物参考照片').setInputFiles({ name: 'me.txt', mimeType: 'text/plain', buffer: Buffer.from('invalid') })
  await expect(page.getByText('请选择不超过 5MB 的 JPG 或 PNG 照片')).toBeVisible()
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.getByLabel('电影主题').fill('哪吒')
  await page.getByRole('button', { name: '试试「赛博哪吒」灵感' }).click()
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.getByText('请选择不超过 5MB 的 JPG 或 PNG 照片').waitFor({ state: 'hidden' })
  await page.screenshot({ path: '../.local/poster-studio-desktop.png', fullPage: true, animations: 'disabled' })
  expect(state.submissions).toHaveLength(0); expect(state.errors).toEqual([])
})

test('未登录用户前往登录页并保留海报主题', async ({ page }) => {
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill({ json: { code: 200, data: { captchaId: 'test', image: '' } } }))
  await page.goto('/posters?movie=哪吒')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  const target = new URL(new URL(page.url()).searchParams.get('redirect')!, page.url())
  expect(target.pathname).toBe('/posters'); expect(target.searchParams.get('movie')).toBe('哪吒')
})

test('刷新历史作品期间提交创意，仍会持续更新到完成状态', async ({ page }) => {
  const state = await setup(page)
  let pauseNextList = false, intercepted = false, release = () => {}
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route(url => url.pathname === '/api/posters', async route => {
    if (route.request().method() === 'GET' && pauseNextList) {
      pauseNextList = false; intercepted = true
      await gate
      return route.fulfill({ json: { code: 200, data: [] } })
    }
    return route.fallback()
  })
  await page.goto('/posters?movie=哪吒')
  await expect(page.getByRole('button', { name: '生成我的电影海报' })).toBeEnabled()
  pauseNextList = true
  await page.getByRole('button', { name: '刷新作品' }).click()
  await expect.poll(() => intercepted).toBe(true)
  await page.getByRole('button', { name: '生成我的电影海报' }).click()
  await expect(page.getByRole('heading', { name: '创意已就位，等待开拍' })).toBeVisible()
  state.ready(); release()
  await expect(page.getByRole('button', { name: '下载 PNG 海报' })).toBeVisible({ timeout: 12_000 })
  expect(state.submissions).toHaveLength(1)
})
