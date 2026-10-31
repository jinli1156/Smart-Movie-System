import { expect, test } from '@playwright/test'
test('首页展示已发布营销内容并进入对应影片', async ({ page }) => {
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    const data = path === '/api/marketing/placements' ? [{ id: 'p1', title: '周末，一起看好故事', copy: '在影院与朋友共享温暖的电影时光。', cinemaName: '测试影院', movieTitle: '银幕上的旅途', imageUrl: '/poster-placeholder.svg', targetUrl: '/movie/1' }] : path === '/api/movie/1' ? { id: 1, title: '银幕上的旅途', duration: 110 } : []
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
  })
  await page.goto('/home')
  const section = page.getByRole('region', { name: '影院精选活动' })
  await expect(section.getByRole('heading', { name: '周末，一起看好故事' })).toBeVisible()
  await section.getByRole('link', { name: '查看场次' }).click()
  await expect(page).toHaveURL(/\/movie\/1$/)
})
test('没有已发布营销内容时不显示空白活动栏', async ({ page }) => {
  await page.route(url => url.pathname.startsWith('/api/'), route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data: [] }) }))
  await page.goto('/home')
  await expect(page.getByRole('region', { name: '影院精选活动' })).toHaveCount(0)
})
