import { expect, test, type Page } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.route('**/api/auth/captcha', route => route.fulfill({ json: { code: 200, data: { captchaId: '12345678-1234-1234-1234-123456789012', image: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', expiresIn: 120 } } }))
})

async function mockLogin(page: Page, success = true) {
  await page.route(url => url.pathname.startsWith('/api/'), route => new URL(route.request().url()).pathname === '/api/auth/captcha' ? route.fallback() : route.fulfill({
    json: { code: 200, data: [], message: '操作成功' }
  }))
  await page.route('**/api/auth/login', route => route.fulfill({
    json: success
      ? { code: 200, data: { token: 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig', userInfo: { id: 7, username: 'preview', role: 'USER' } } }
      : { code: 400, message: '用户名或密码错误', data: null }
  }))
}

async function submit(page: Page) {
  await page.getByLabel('用户名', { exact: true }).fill('preview')
  await page.getByLabel('密码', { exact: true }).fill('preview-password')
  await page.getByLabel('验证码', { exact: true }).fill('AB23')
  await page.getByRole('button', { name: '登录', exact: true }).click()
}

test('检票通过后保持原来的跳转目标，并阻止重复提交', async ({ page }) => {
  await mockLogin(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/login?redirect=%2Fmovies%3Fkeyword%3Dtest')
  await page.screenshot({ path: '../output/login-admission/desktop.png', fullPage: true, animations: 'disabled' })
  await page.clock.install()
  await page.clock.pauseAt(new Date(Date.now() + 1000))
  await submit(page)
  await expect(page.locator('.admission-stamp')).toBeVisible()
  await expect(page.getByRole('button', { name: '检票通过，欢迎入座' })).toBeDisabled()
  await page.screenshot({ path: '../output/login-admission/success.png', fullPage: true, animations: 'disabled' })
  await expect(page).toHaveURL(/\/login\?/)
  await page.clock.runFor(900)
  await expect(page).toHaveURL(/\/movies\?keyword=test$/)
})

test('错误凭据不盖章，允许再次提交', async ({ page }) => {
  await mockLogin(page, false)
  await page.goto('/login')
  await submit(page)
  await expect(page.getByText('用户名或密码错误', { exact: true })).toBeVisible()
  await expect(page.locator('.admission-stamp')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
  await expect(page).toHaveURL(/\/login$/)
})

test('检票反馈期间离开页面，不再执行延迟跳转', async ({ page }) => {
  await mockLogin(page)
  await page.goto('/login?redirect=%2Fmovies%3Fkeyword%3Dtest')
  await page.clock.install()
  await page.clock.pauseAt(new Date(Date.now() + 1000))
  await submit(page)
  await expect(page.locator('.admission-stamp')).toBeVisible()
  await page.getByRole('link', { name: '先去逛逛' }).click()
  await expect(page).toHaveURL(/\/home$/)
  await page.clock.runFor(1500)
  await expect(page).toHaveURL(/\/home$/)
})

test('手机窄屏和减少动态效果设置下，票根可见且正常登录', async ({ page }) => {
  await mockLogin(page)
  await page.setViewportSize({ width: 375, height: 812 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/login')
  await page.screenshot({ path: '../output/login-admission/mobile.png', fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.clock.install()
  await page.clock.pauseAt(new Date(Date.now() + 1000))
  await submit(page)
  await expect(page.locator('.admission-stamp')).toBeVisible()
  expect(await page.locator('.admission-stamp').evaluate(el => getComputedStyle(el).animationName)).toBe('none')
  await page.screenshot({ path: '../output/login-admission/mobile-success.png', fullPage: true, animations: 'disabled' })
  await page.clock.runFor(300)
  await expect(page).toHaveURL(/\/home$/)
})

