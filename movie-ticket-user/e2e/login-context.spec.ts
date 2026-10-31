import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/auth/captcha', route => route.fulfill({ json: { code: 200, data: { captchaId: '12345678-1234-1234-1234-123456789012', image: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', expiresIn: 120 } } }))
})

test.beforeEach(async ({ page }) => {
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/captcha') return route.fallback()
    const data = path === '/api/movie/now-showing'
      ? [{ id: 11, title: '真实热映测试片', genre: '剧情', duration: 120, poster: '/poster-placeholder.svg' }]
      : path === '/api/showtime/101' ? { id: 101, movieTitle: '购票目标电影' } : []
    return route.fulfill({ json: { code: 200, data } })
  })
})

test('普通登录移除今日片单及其数据请求', async ({ page }) => {
  const requests: string[] = []
  page.on('request', request => requests.push(request.url()))
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '欢迎入座' })).toBeVisible()
  await expect(page.getByText('今日片单', { exact: true })).toHaveCount(0)
  expect(requests.some(url => url.includes('/api/movie/now-showing'))).toBe(false)
  await page.mouse.move(300, 300)
  await expect(page.locator('.pointer-light')).toHaveCSS('opacity', '1')
  await page.screenshot({ path: '../output/login-context/desktop.png', fullPage: true, animations: 'disabled' })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect(page.locator('.pointer-light')).toBeHidden()
  await expect(page.locator('.ambient-glow-warm')).toHaveCSS('animation-name', 'none')
})

for (const target of ['/showtime/101/seats', '/order/confirm?showtimeId=101&seats=2排2座']) {
  test(`购票登录根据场次读取片名: ${target}`, async ({ page }) => {
    await page.goto(target)
    await expect(page).toHaveURL(/\/login\?redirect=/)
    await expect(page.getByText('登录后继续购买《购票目标电影》', { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: /注册账号/ })).toHaveAttribute('href', /redirect=/)
    await page.screenshot({ path: '../output/login-context/purchase.png', fullPage: true, animations: 'disabled' })
  })
}

test('手机不请求片单，长片名不溢出', async ({ page }) => {
  let picks = 0
  page.on('request', request => { if (request.url().includes('/api/movie/now-showing')) picks++ })
  await page.setViewportSize({ width: 375, height: 812 })
  await page.route('**/api/showtime/101', route => route.fulfill({ json: { code: 200, data: { movieTitle: '一部名字很长很长的电影：从清晨到深夜的光影旅程' } } }))
  await page.goto('/showtime/101/seats')
  await expect(page.locator('.purchase-copy')).toContainText('从清晨到深夜')
  await expect(page.locator('.daily-pick')).toHaveCount(0)
  expect(picks).toBe(0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: '../output/login-context/mobile.png', fullPage: true, animations: 'disabled' })
})

test('可选数据加载失败不干扰登录，保留普通欢迎提示', async ({ page }) => {
  await page.route(url => url.pathname.startsWith('/api/'), route => new URL(route.request().url()).pathname === '/api/auth/captcha' ? route.fallback() : route.fulfill({ status: 503, json: { message: '暂不可用' } }))
  await page.goto('/login?redirect=%2Fshowtime%2F101%2Fseats')
  await expect(page.getByRole('heading', { name: '欢迎入座' })).toBeVisible()
  await expect(page.locator('.daily-pick')).toHaveCount(0)
  await expect(page.locator('.el-message')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
})
