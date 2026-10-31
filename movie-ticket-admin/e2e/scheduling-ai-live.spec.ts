import { expect, test } from '@playwright/test'

// Opt-in: uses the local database and makes one real model call; does not save schedules.
test('真实数据库经大模型生成排片分析并展示到管理端', async ({ page, request }) => {
  test.skip(process.env.SCHEDULING_MODEL_LIVE !== '1', '需要显式启用真实模型联调')
  test.setTimeout(90_000)
  const backend = process.env.SCHEDULING_BACKEND_URL || 'http://127.0.0.1:8080'
  const login = await request.post(`${backend}/api/auth/login`, {
    data: { username: process.env.SCHEDULING_ADMIN_USER || 'admin', password: process.env.SCHEDULING_ADMIN_PASSWORD || 'admin123' }
  })
  const auth = await login.json()
  expect(auth.code).toBe(200)
  await page.addInitScript(({ token, userInfo }) => {
    localStorage.setItem('token', token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
  }, auth.data)
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const url = new URL(route.request().url())
    const response = await route.fetch({ url: `${backend}${url.pathname}${url.search}`, timeout: 70_000 })
    await route.fulfill({ response })
  })
  const responsePromise = page.waitForResponse(response => response.url().includes('/statistics/scheduling-insights'), { timeout: 75_000 })
  await page.goto('/smart-scheduling')
  await page.getByRole('button', { name: '经营分析', exact: true }).click()
  const response = await (await responsePromise).json()
  expect(response.code).toBe(200)
  expect(response.data.aiAnalysis.source).toBe('model')
  expect(response.data.aiAnalysis.dataBasis.source).toBe('database')
  const panel = page.getByRole('region', { name: '大模型排片分析' })
  await expect(panel).toContainText(response.data.aiAnalysis.model)
  await expect(panel).toContainText(`数据库历史样本 ${response.data.summary.historicalSamples} 场`)
  await expect(panel).toContainText(response.data.aiAnalysis.summary)
  expect(errors).toEqual([])
  await page.screenshot({ path: '../.local/scheduling-live-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(panel).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: '../.local/scheduling-live-mobile.png', fullPage: true })
})
