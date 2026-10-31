import { expect, test, type Route } from '@playwright/test'

const movie = {
  id: 3,
  title: '封神第二部：战火西岐',
  englishTitle: 'Creation of the Gods II',
  director: '乌尔善',
  actors: '黄渤,费翔,李雪健,于适,陈牧驰,娜然,那尔那茜',
  genre: '奇幻,动作',
  country: '中国',
  language: '汉语普通话',
  duration: 150,
  releaseDate: '2025-01-29',
  poster: '/api/uploads/poster-movie-03.jpg',
  description: '殷商大军兵临西岐，姜子牙与姬发带领西岐军民守卫家园。面对闻仲与魔家四将布下的重重险阵，众人必须在信念、亲情与天下苍生之间作出选择。这是一段关于守护、成长与共同命运的神话史诗。',
  rating: 8.0,
  status: 1
}

const json = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 200, message: '操作成功', data })
})

test('影片详情的全文、演职员、剧照与预告入口均可操作', async ({ page }) => {
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/movie/3') return json(route, movie)
    if (path === '/api/cinema/all') return json(route, [])
    if (path === '/api/recommend/similar/3') return json(route, [])
    if (path === '/api/comment/movie/3') return json(route, [])
    if (path.startsWith('/api/uploads/')) {
      return route.fulfill({ status: 200, contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180"><rect width="100%" height="100%" fill="#332015"/></svg>' })
    }
    return json(route, [])
  })
  await page.route('https://i.ytimg.com/**', route => route.fulfill({
    status: 200,
    contentType: 'image/svg+xml',
    body: '<svg xmlns="http://www.w3.org/2000/svg" width="480" height="270"><rect width="100%" height="100%" fill="#21170f"/></svg>'
  }))

  await page.goto('/movie/3')
  await expect(page.getByRole('heading', { name: '剧情简介' })).toBeVisible()
  await expect(page.locator('.hero-cta-price')).toHaveCount(0)
  await expect(page.getByText(/支持改签|可改签|IMDb/)).toHaveCount(0)

  await page.getByRole('button', { name: '展开全文' }).click()
  await expect(page.getByRole('button', { name: '收起全文' })).toBeVisible()

  await page.getByRole('button', { name: '查看全部' }).click()
  await expect(page.getByText('8 位主创')).toBeVisible()
  await expect(page.locator('.cast-dialog-item')).toHaveCount(8)
  await page.locator('.cast-dialog .el-dialog__headerbtn').click()

  await page.getByRole('button', { name: '全部 7 张' }).click()
  await expect(page.locator('.lightbox-stage img')).toBeVisible()
  await expect(page.locator('.lightbox-thumbs button')).toHaveCount(7)
  await page.locator('.stills-dialog .el-dialog__headerbtn').click()

  await page.getByRole('button', { name: '3 个预告' }).click()
  await expect(page.locator('.trailer-library > button')).toHaveCount(3)
  await expect(page.locator('.trailer-player-wrap iframe')).toHaveAttribute('src', /xYQ65FNLJvU/)
})
