import { expect, test } from '@playwright/test'

const image = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII='

test('验证码必填，刷新清空输入，错误后换图并保留账号密码', async ({ page }) => {
  let generation = 0
  const submissions: any[] = []
  await page.route('**/api/auth/captcha', route => route.fulfill({ json: { code: 200, data: { captchaId: `challenge-${++generation}`, image, expiresIn: 120 } } }))
  await page.route('**/api/auth/login', route => {
    submissions.push(route.request().postDataJSON())
    return route.fulfill({ json: { code: 400, message: '验证码错误，请重新输入' } })
  })
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill('preview')
  await page.getByLabel('密码', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  expect(submissions).toHaveLength(0)
  await page.getByLabel('验证码', { exact: true }).fill('AB23')
  await page.getByRole('button', { name: '刷新验证码' }).click()
  await expect(page.getByLabel('验证码', { exact: true })).toHaveValue('')
  await page.getByLabel('验证码', { exact: true }).fill('ab23')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText('验证码错误，请重新输入', { exact: true })).toBeVisible()
  expect(submissions[0]).toEqual({ username: 'preview', password: 'test-password', captchaId: 'challenge-2', captcha: 'ab23' })
  await expect(page.getByLabel('验证码', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('test-password')
  await expect.poll(() => generation).toBe(3)
})

test('图片加载失败可以重试，恢复前禁止登录', async ({ page }) => {
  await page.route('**/api/auth/captcha', route => route.fulfill({ status: 503, json: {} }))
  await page.goto('/login')
  await expect(page.getByText('验证码加载失败，请点击图片重试')).toBeVisible()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeDisabled()
  await page.route('**/api/auth/captcha', route => route.fulfill({ json: { code: 200, data: { captchaId: 'recovered', image, expiresIn: 120 } } }))
  await page.getByRole('button', { name: '刷新验证码' }).click()
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeEnabled()
})
