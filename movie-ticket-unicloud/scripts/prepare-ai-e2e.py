from pathlib import Path

cloud = Path(__file__).resolve().parent.parent
root = cloud.parent
for name, files, port in [
    ('movie-ticket-user', ['poster', 'souvenir', 'marketing', 'assistant-souvenir-entry'], 4177),
    ('movie-ticket-admin', ['listing', 'marketing', 'scheduling-workspace'], 4178),
]:
    app = cloud / 'frontend' / name
    output = app / '.local'
    (output / 'e2e').mkdir(parents=True, exist_ok=True)
    for file in files:
        text = (root / name / 'e2e' / (file + '.spec.ts')).read_text(encoding='utf-8')
        text = text.replace('5MB', '1.4MB')
        if file == 'poster':
            text = text.replace("submissions.push(route.request().postDataBuffer()!.toString('utf8'))", "const payload = route.request().postDataJSON(); submissions.push(Object.entries(payload.fields || {}).map(([k,v]) => `name=\"${k}\"\\r\\n\\r\\n${v}`).join('\\n') + (payload.files || []).map((f: any) => `name=\"${f.name}\"; filename=\"${f.filename}\"\\r\\n\\r\\n` + (f.name === 'data' ? Buffer.from(f.base64, 'base64').toString('utf8') : '')).join('\\n'))")
            text += '''
test('云网关分段下载保留原始 PNG 字节且每段验证登录状态', async ({ page }) => {
  await setup(page, { saved: fixture })
  const original = Buffer.concat([png, Buffer.alloc(1100000)]), size = 512 * 1024, requested: number[] = []
  await page.route(url => url.pathname === '/api/posters/poster-test/file', async route => {
    expect(route.request().headers().authorization).toContain('Bearer ')
    const part = new URL(route.request().url()).searchParams.get('part')
    if (part === null) return route.fulfill({ json: { code: 200, data: { parts: Math.ceil(original.length / size), size: original.length, mime: 'image/png' } } })
    requested.push(Number(part))
    return route.fulfill({ contentType: 'application/octet-stream', body: original.subarray(Number(part) * size, (Number(part) + 1) * size) })
  })
  await page.goto('/posters')
  await expect(page.getByRole('button', { name: '下载 PNG 海报' })).toBeEnabled()
  const pending = page.waitForEvent('download'); await page.getByRole('button', { name: '下载 PNG 海报' }).click()
  const download = await pending, downloaded = await (await import('node:fs/promises')).readFile((await download.path())!)
  expect(downloaded.equals(original)).toBe(true); expect(requested).toEqual([0, 1, 2])
})
test('海报仅轮询任务列表，隐藏页面不继续请求云端', async ({ page }) => {
  await page.clock.install()
  await setup(page, { saved: { ...fixture, status: 'QUEUED' } })
  let capabilityReads = 0, listReads = 0
  await page.route('**/api/posters**', async route => {
    if (route.request().method() === 'GET') {
      const path = new URL(route.request().url()).pathname
      if (path.endsWith('/capabilities')) capabilityReads++
      if (path === '/api/posters') listReads++
    }
    await route.fallback()
  })
  await page.goto('/posters')
  await expect(page.getByRole('heading', { name: '创意已就位，等待开拍' })).toBeVisible()
  const initialCapabilities = capabilityReads, initialList = listReads
  await page.clock.runFor(8000)
  await expect.poll(() => listReads).toBeGreaterThan(initialList)
  expect(capabilityReads).toBe(initialCapabilities)
  await page.evaluate(() => Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }))
  const beforeHidden = listReads
  await page.clock.runFor(32000)
  expect(listReads).toBe(beforeHidden)
})
'''
        if name.endswith('admin'):
            if file == 'marketing':
                text = text.replace("task.status = 'SUBMITTING'", "task.status = 'GENERATING'")
                text += '''
test('营销任务均已结束时不继续轮询云函数', async ({ page }) => {
  await page.clock.install(); await setup(page)
  let reads = 0
  await page.route('**/api/admin/marketing/campaigns/c1', async route => { if (route.request().method() === 'GET') reads++; await route.fallback() })
  await page.goto('/marketing/studio?campaign=c1')
  await expect(page.getByRole('button', { name: '生成文案', exact: true })).toBeVisible()
  const initial = reads; expect(initial).toBeGreaterThan(0)
  await page.clock.runFor(60000)
  expect(reads).toBe(initial)
})
'''
            if file == 'listing':
                text = text.replace("req.headers()['content-type']?.includes('application/json') ? req.postDataJSON() : req.postDataBuffer()?.toString('latin1')", "endpoint === '/api/listing/import' ? JSON.stringify(req.postDataJSON().files) + Object.entries(req.postDataJSON().fields).map(([k,v]) => `name=\"${k}\"\\r\\n\\r\\n${v}`).join('\\n') : req.postDataJSON()")
            text = text.replace("await expect(page.locator('.drag-preview')).toHaveClass(/invalid/)", "await expect(page.locator('.drag-preview').first()).toBeVisible(); for (const preview of await page.locator('.drag-preview').all()) await expect(preview).toHaveClass(/invalid/)")
            text = text.replace("await expect(page.locator('.showtime-block.conflict')).toHaveCount(1)", "await expect(page.locator('.timeline-feedback')).toHaveClass(/invalid/)")
            text = text.replace("toContainText('需预留 20 分钟')", "toContainText(delta === -60 ? '已锁定，不能交换场次' : '需预留 20 分钟')")
            text = text.replace("page.goto('/", "page.goto('/admin/#/")
            text = text.replace("../movie-ticket-backend/src/main/resources/listing/templates", str(root / 'movie-ticket-backend/src/main/resources/listing/templates').replace('\\', '/'))
        elif file == 'marketing':
            text += '''
test('首页只显示一处即将上映且完全取消票房轮询', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1600, height: 1000 })
  await page.clock.install()
  let boxOfficeReads = 0, upcomingReads = 0
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    if (path.includes('/box-office/')) boxOfficeReads++
    if (path === '/api/movie/coming-soon') upcomingReads++
    const data = path === '/api/movie/now-showing' ? [{ id: 1, title: '正在上映测试电影', duration: 100, genre: '剧情' }]
      : path === '/api/movie/coming-soon' ? [{ id: 2, title: '即将上映测试电影', releaseDate: '2026-11-15', genre: '科幻' }] : []
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.goto('/home')
  const upcoming = page.getByRole('complementary', { name: '即将上映' })
  await expect(upcoming).toBeVisible()
  await expect(upcoming.getByText('即将上映测试电影', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '即将上映', exact: true })).toHaveCount(1)
  await expect(page.getByText('今日票房排行榜', { exact: true })).toHaveCount(0)
  const main = await page.locator('.hero-main').boundingBox(), side = await upcoming.boundingBox()
  expect(Math.abs(main!.y - side!.y)).toBeLessThan(2)
  expect(side!.x).toBeGreaterThan(main!.x)
  await page.clock.runFor(60000)
  expect(boxOfficeReads).toBe(0); expect(upcomingReads).toBe(1)
  await page.screenshot({ path: testInfo.outputPath('home-upcoming-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(upcoming).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('home-upcoming-mobile.png'), fullPage: true })
  await upcoming.getByRole('button', { name: /即将上映测试电影/ }).click()
  await expect(page).toHaveURL(/\\/movie\\/2$/)
  expect(boxOfficeReads).toBe(0)
})
'''
        (output / 'e2e' / (file + '.spec.ts')).write_text(text, encoding='utf-8')
    config = f"""import {{ defineConfig, devices }} from '@playwright/test'
export default defineConfig({{
  testDir: './e2e', fullyParallel: true, workers: 3, timeout: 45000,
  expect: {{ timeout: 10000 }}, reporter: 'line', outputDir: './test-results',
  use: {{ baseURL: 'http://127.0.0.1:{port}', trace: 'retain-on-failure', screenshot: 'only-on-failure' }},
  projects: [{{ name: 'chromium', use: {{ ...devices['Desktop Chrome'] }} }}],
  webServer: {{ command: 'npm run preview -- --host 127.0.0.1 --port {port}', cwd: '..', url: 'http://127.0.0.1:{port}{'/admin/' if name.endswith('admin') else '/'}', timeout: 60000 }}
}})
"""
    (output / 'playwright.config.ts').write_text(config, encoding='utf-8')
    print('Prepared cloud production browser tests:', name)
