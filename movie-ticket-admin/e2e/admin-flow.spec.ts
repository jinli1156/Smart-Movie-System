import { expect, test, type Page, type Route } from '@playwright/test'

const order = {
  id: 9,
  orderNo: 'ORDER-ADMIN-E2E',
  userId: 7,
  userName: 'E2E 用户',
  showtimeId: 101,
  movieTitle: 'E2E 测试电影',
  cinemaName: 'E2E 中心影院',
  hallName: 'IMAX 厅',
  showtime: '2099-09-03T19:30:00',
  seats: '2排2座',
  seatCount: 1,
  totalPrice: 48,
  status: 1,
  statusText: '已支付',
  createTime: '2026-09-01T10:00:00',
  payTime: '2026-09-01T10:01:00'
}

const json = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 200, message: '操作成功', data })
})

async function mockBackend(page: Page, role = 'ADMIN') {
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname

    if (path === '/api/auth/login') {
      return json(route, {
        token: `e2e-${role.toLowerCase()}-token`,
        userInfo: { id: 1, username: 'admin', nickname: 'E2E 管理员', role }
      })
    }
    if (path === '/api/statistics/dashboard') {
      return json(route, { totalMovies: 8, totalCinemas: 3, totalUsers: 12, totalOrders: 21, totalRevenue: 1024 })
    }
    if (path === '/api/statistics/box-office' || path === '/api/statistics/revenue') {
      return json(route, [])
    }
    if (path === '/api/order/page') {
      return json(route, { records: [order], total: 1, size: 10, current: 1, pages: 1 })
    }
    if (path === '/api/order/ORDER-ADMIN-E2E') {
      return json(route, order)
    }
    return json(route, [])
  })
}

async function login(page: Page) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入管理员账号').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('admin123')
  const captcha = (await page.locator('.captcha-img').innerText()).replace(/\s/g, '')
  await page.getByPlaceholder('请输入验证码').fill(captcha)
  await page.getByRole('button', { name: '登录控制台' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

test('平台管理员可登录、查看订单列表和订单详情', async ({ page }) => {
  await mockBackend(page)
  await login(page)

  await expect(page.getByText('用户管理', { exact: true })).toBeVisible()
  await page.goto('/order')
  await expect(page).toHaveURL(/\/order$/)
  await expect(page.getByText('ORDER-ADMIN-E2E')).toBeVisible()
  await page.getByRole('button', { name: '详情' }).click()
  await expect(page.getByRole('dialog', { name: '订单详情' })).toContainText('E2E 测试电影')
})

test('影院管理员无法进入平台用户管理页面', async ({ page }) => {
  await mockBackend(page, 'CINEMA_ADMIN')
  await page.addInitScript(() => {
    localStorage.setItem('token', 'e2e-cinema-admin-token')
    localStorage.setItem('userInfo', JSON.stringify({ id: 2, username: 'cinema_admin', nickname: '影院管理员', role: 'CINEMA_ADMIN' }))
  })

  await page.goto('/user')
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByText('用户管理', { exact: true })).toHaveCount(0)
})
