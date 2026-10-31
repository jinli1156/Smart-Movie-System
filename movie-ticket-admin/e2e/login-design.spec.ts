import { expect, test } from '@playwright/test'

for (const role of ['ADMIN', 'USER']) {
  test(`管理登录反馈与权限：${role}`, async ({ page }) => {
    await page.route(url => url.pathname.startsWith('/api/'), route => {
      const path = new URL(route.request().url()).pathname
      const data = path.endsWith('/captcha')
        ? { captchaId: 'test-id', image: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', expiresIn: 120 }
        : path.endsWith('/login') ? { token: 'test-token', userInfo: { id: 1, username: 'preview', role } } : {}
      return route.fulfill({ json: { code: 200, data } })
    })
    await page.goto('/login')
    await expect(page.getByRole('button', { name: '登录控制台' })).toBeEnabled()
    await page.mouse.move(350, 300)
    await expect(page.locator('.pointer-light')).toHaveCSS('opacity', '1')
    await page.getByLabel('管理员账号').fill('preview')
    await page.getByLabel('密码', { exact: true }).fill('test-password')
    await page.getByLabel('验证码', { exact: true }).fill('AB23')
    await page.clock.install()
    await page.clock.pauseAt(new Date(Date.now() + 1000))
    await page.getByRole('button', { name: '登录控制台' }).click()
    if (role === 'ADMIN') {
      await expect(page.locator('.admission-stamp')).toContainText('验证通过')
      await expect(page.getByRole('button', { name: '验证通过，欢迎进入' })).toBeDisabled()
      await page.screenshot({ path: '../output/admin-login-success.png', fullPage: true, animations: 'disabled' })
      await page.clock.runFor(900)
      await expect(page).toHaveURL(/\/dashboard$/)
    } else {
      await expect(page.getByText('权限不足，请使用管理员账号登录')).toBeVisible()
      await expect(page.locator('.admission-stamp')).toHaveCount(0)
      await expect(page).toHaveURL(/\/login$/)
    }
  })
}

test('移动端及减少动态效果设置保持静态且不溢出', async ({ page }) => {
  await page.route('**/api/auth/captcha', route => route.fulfill({ status: 503, json: {} }))
  await page.setViewportSize({ width: 375, height: 812 })
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/login')
  await expect(page.locator('.cinema-atmosphere')).toBeHidden()
  await expect(page.locator('.ambient-glow-warm')).toHaveCSS('animation-name', 'none')
  await expect(page.getByLabel('验证码', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: '../output/admin-login-mobile.png', fullPage: true, animations: 'disabled' })
})
