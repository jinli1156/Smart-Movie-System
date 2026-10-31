import { expect, test, type Page, type Route } from '@playwright/test'

const json = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 200, message: '操作成功', data })
})

const movies = Array.from({ length: 12 }, (_, index) => ({
  id: index + 1,
  title: `推荐电影 ${index + 1}`,
  englishTitle: `Movie ${index + 1}`,
  director: '测试导演',
  genre: index % 2 === 0 ? '科幻,冒险' : '剧情',
  duration: 120 + index,
  rating: 9 - index / 10,
  status: 1
}))

const cinemas = [
  { id: 101, name: '浦东测试影城', address: '上海市浦东新区世纪大道 1 号', lng: 121.51, lat: 31.24, status: 1 },
  { id: 102, name: '陆家嘴测试影城', address: '上海市浦东新区花园石桥路 2 号', lng: 121.50, lat: 31.23, status: 1 }
]

async function mockHomeBackend(page: Page, loggedIn = false) {
  await page.addInitScript((shouldLogIn) => {
    sessionStorage.setItem('user_location_cache', JSON.stringify({ label: '上海 · 浦东新区' }))
    if (shouldLogIn) {
      const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))
        .replace(/=/g, '')
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
      localStorage.setItem('token', `test.${payload}.signature`)
      localStorage.setItem('userInfo', JSON.stringify({ id: 1, username: 'test-user', nickname: '测试用户' }))
    }
  }, loggedIn)
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/movie/now-showing') return json(route, movies)
    if (url.pathname === '/api/movie/coming-soon') return json(route, [])
    if (url.pathname === '/api/movie/box-office/today') {
      return json(route, [
        { movieId: 2, movieTitle: '推荐电影 2', boxOffice: 1280, ticketCount: 20 },
        { movieId: 1, movieTitle: '推荐电影 1', boxOffice: 960, ticketCount: 15 },
        { movieId: 3, movieTitle: '推荐电影 3', boxOffice: 640, ticketCount: 10 }
      ])
    }
    if (url.pathname === '/api/recommend/profile') {
      return json(route, {
        needsColdStart: false,
        favoriteGenres: ['科幻', '冒险'],
        viewedCount: 4,
        favoriteCount: 2,
        purchasedCount: 1,
        ratedCount: 1
      })
    }
    if (url.pathname === '/api/recommend/profile/radar') {
      return json(route, {
        dimensions: { 内容契合: 88, 显式偏好: 82, 行为相似: 91, 场景适配: 75, 近期趋势: 79 },
        topGenres: ['科幻', '冒险'],
        evidenceCount: 8,
        summary: '当前推荐主要由“行为相似”驱动，共使用 8 条用户侧证据'
      })
    }
    if (url.pathname === '/api/recommend/personal') {
      return json(route, movies.map((movie, index) => ({
        ...movie,
        matchScore: 95 - index,
        recommendationReason: index === 0 ? '因为你喜欢科幻片' : '因为近期口碑与购票热度较高'
      })))
    }
    if (url.pathname === '/api/recommend/hot') return json(route, movies)
    if (url.pathname === '/api/cinema/all') return json(route, cinemas)
    return json(route, [])
  })
}

test('首页展示今日票房并能切换下一批推荐', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await mockHomeBackend(page)
  await page.goto('/home')

  await expect(page.getByText('今日票房排行榜')).toBeVisible()
  await expect(page.getByText('¥1,280 · 20 人次')).toBeVisible()

  const recommendationNames = page.locator('.rec-grid .rec-name')
  await expect(recommendationNames).toHaveText([
    '推荐电影 1', '推荐电影 2', '推荐电影 3',
    '推荐电影 4', '推荐电影 5', '推荐电影 6'
  ])

  await page.getByRole('button', { name: '换一批' }).click()
  await expect(recommendationNames).toHaveText([
    '推荐电影 7', '推荐电影 8', '推荐电影 9',
    '推荐电影 10', '推荐电影 11', '推荐电影 12'
  ])
})

test('附近影院卡片可进入对应影院详情', async ({ page }) => {
  await mockHomeBackend(page)
  await page.goto('/home')

  await page.getByRole('button', { name: '查看陆家嘴测试影城详情' }).click()
  await expect(page).toHaveURL(/\/cinema\/102$/)
})

test('首页空数据不展示可点击演示影片或虚构起价', async ({ page }) => {
  await mockHomeBackend(page)
  await page.route('**/api/movie/now-showing', route => json(route, []))
  await page.route('**/api/recommend/hot?**', route => json(route, []))
  await page.goto('/home')
  await expect(page.getByText('暂无影片', { exact: true })).toBeVisible()
  await expect(page.locator('.movie-card, .rec-card, .cs-card')).toHaveCount(0)
  await expect(page.locator('.cinema-item .cinema-start-price')).toHaveCount(0)
  await expect(page.getByText(/支持改签|免费停车|功能敬请期待/)).toHaveCount(0)
  await page.screenshot({ path: '../output/cinema-home-empty.png', fullPage: true })
})

test('AI 洞察展示后端画像与推荐理由生成的真实分析', async ({ page }) => {
  await mockHomeBackend(page, true)
  await page.goto('/home')

  await expect(page.locator('.ai-insight-text')).toContainText('共使用 8 条用户侧证据')
  await expect(page.locator('.ai-insight-text')).toContainText('你的主要偏好类型是科幻、冒险')
  await expect(page.locator('.ai-insight-text')).toContainText('当前首选《推荐电影 1》，推荐理由：因为你喜欢科幻片')
})


test('实时票房自动更新排序，失败保留数据并恢复空榜单', async ({ page }) => {
  await page.clock.install()
  await mockHomeBackend(page, false)
  let state: 'first' | 'updated' | 'failed' | 'empty' = 'first'
  await page.route('**/api/movie/box-office/today?**', route => {
    if (state === 'failed') return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '服务暂不可用' }) })
    return json(route, state === 'empty' ? [] : state === 'first' ? [
      { movieId: 1, movieTitle: '推荐电影 1', boxOffice: 960, ticketCount: 15 }
    ] : [
      { movieId: 2, movieTitle: '推荐电影 2', boxOffice: 1500, ticketCount: 25 },
      { movieId: 1, movieTitle: '推荐电影 1', boxOffice: 1000, ticketCount: 16 }
    ])
  })
  await page.goto('/home')
  const ranking = page.getByLabel('今日票房排行榜', { exact: true })
  await expect(ranking.locator('.un-title')).toHaveText(['推荐电影 1'])
  await expect(ranking).toContainText('每5秒刷新')
  state = 'updated'
  await page.clock.runFor(5100)
  await expect(ranking.locator('.un-title')).toHaveText(['推荐电影 2', '推荐电影 1'])
  await expect(ranking).toContainText('¥1,500 · 25 人次')
  state = 'failed'
  await page.clock.runFor(5100)
  await expect(ranking).toContainText('更新失败 · 上次更新')
  await expect(ranking).toContainText('¥1,500 · 25 人次')
  state = 'empty'
  await page.clock.runFor(5100)
  await expect(ranking).toContainText('今日暂无票房排行')
  await expect(ranking.locator('.up-next-card')).toHaveCount(0)
  await expect(ranking).not.toContainText('更新失败')
})

test('票房刷新在后台暂停，返回立即更新，离开首页停止请求', async ({ page }) => {
  await page.clock.install()
  await mockHomeBackend(page, false)
  let requests = 0
  await page.route('**/api/movie/box-office/today?**', route => {
    requests++
    return json(route, [{ movieId: 1, movieTitle: '推荐电影 1', boxOffice: requests * 100, ticketCount: requests }])
  })
  await page.goto('/home')
  await expect(page.locator('.un-sub').first()).toHaveText('¥100 · 1 人次')
  await page.evaluate(() => {
    Object.defineProperty(document, 'hidden', { configurable: true, value: true })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  await page.clock.runFor(16000)
  expect(requests).toBe(1)
  await page.evaluate(() => {
    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  await expect(page.locator('.un-sub').first()).toHaveText('¥200 · 2 人次')
  await page.getByRole('button', { name: '打开影票观', exact: true }).click()
  await expect(page).toHaveURL(/assistant/)
  await page.clock.runFor(16000)
  expect(requests).toBe(2)
})
