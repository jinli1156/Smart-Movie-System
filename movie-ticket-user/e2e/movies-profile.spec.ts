import { expect, test, type Page, type Route } from '@playwright/test'

const json = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify({ code: 200, message: '操作成功', data })
})

const movies = [
  {
    id: 1,
    title: '真实推荐电影',
    englishTitle: 'Real Recommendation',
    director: '测试导演',
    actors: '测试演员',
    genre: '科幻,冒险',
    duration: 128,
    rating: 9.2,
    status: 1,
    matchScore: 93,
    recommendationReason: '因为你喜欢科幻片'
  }
]

async function mockMoviesBackend(page: Page, emptyGenres = false) {
  await page.addInitScript(() => {
    const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))
      .replace(/=/g, '')
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
    localStorage.setItem('token', `test.${payload}.signature`)
    localStorage.setItem('userInfo', JSON.stringify({ id: 1, username: 'test-user', nickname: '测试用户' }))
    sessionStorage.setItem('user_location_cache', JSON.stringify({ label: '上海 · 浦东新区' }))
  })
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/movie/now-showing') return json(route, movies)
    if (path === '/api/movie/coming-soon') return json(route, [])
    if (path === '/api/recommend/favorites') return json(route, [])
    if (path === '/api/recommend/personal') return json(route, movies)
    if (path === '/api/recommend/profile') {
      return json(route, {
        needsColdStart: emptyGenres,
        favoriteGenres: emptyGenres ? [] : ['科幻', '冒险'],
        viewedCount: 4,
        favoriteCount: 2,
        purchasedCount: 1,
        ratedCount: 1
      })
    }
    if (path === '/api/recommend/profile/radar') {
      return json(route, {
        dimensions: { 内容契合: 88, 显式偏好: 82, 行为相似: 91, 场景适配: 75, 近期趋势: 79 },
        topGenres: emptyGenres ? [] : ['科幻', '冒险'],
        evidenceCount: 8,
        summary: '当前推荐主要由“行为相似”驱动，共使用 8 条用户侧证据'
      })
    }
    if (/\/api\/movie\/\d+\/insight$/.test(path)) {
      return json(route, {
        movieId: 1,
        todayBoxOffice: 1200,
        todayViewerCount: 20,
        occupancyRate: 60,
        trendPercent: 5,
        trendPoints: [1, 2, 3, 4, 5, 6, 7],
        generatedAt: new Date().toISOString()
      })
    }
    return json(route, [])
  })
}

test('影片页观影画像使用真实接口数据并支持导出', async ({ page }) => {
  await mockMoviesBackend(page)
  await page.goto('/movies')

  const profile = page.locator('.ai-analysis-section')
  await profile.scrollIntoViewIfNeeded()
  await expect(profile.locator('.aas-sub')).toContainText('基于 8 条用户侧证据')
  await expect(profile.getByText('科幻', { exact: true })).toBeVisible()
  await expect(profile.getByText('TOP 1')).toBeVisible()
  await expect(profile.locator('.radar-summary')).toHaveText('当前推荐主要由“行为相似”驱动，共使用 8 条用户侧证据')
  await expect(profile.locator('.evidence-total strong')).toHaveText('8')
  await expect(profile.locator('.summary-text')).toContainText('当前首选《真实推荐电影》，因为你喜欢科幻片')
  await expect(profile).not.toContainText('平均观影时长 142 分钟')

  const downloadPromise = page.waitForEvent('download')
  await profile.getByRole('button', { name: '导出报告' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toMatch(/^CINEMA-观影画像-\d{4}-\d{2}-\d{2}\.txt$/)
})

test('去设置会打开个人中心的观影偏好编辑区域并重置页面滚动', async ({ page }) => {
  await mockMoviesBackend(page, true)
  await page.goto('/movies')

  const profile = page.locator('.ai-analysis-section')
  await profile.scrollIntoViewIfNeeded()
  await profile.getByRole('button', { name: '去设置' }).click()

  await expect(page).toHaveURL(/\/profile\?edit=preferences$/)
  await expect(page.locator('.el-dialog')).toBeVisible()
  await expect(page.locator('.el-dialog').getByText('观影偏好', { exact: true })).toBeVisible()
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0)
})


test('影票观右侧入口跳转后直接打开观影偏好', async ({ page }) => {
  await mockMoviesBackend(page)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.goto('/assistant')
  await expect(page.getByRole('heading', { name: /影票观.*AI/ })).toBeVisible()
  await expect(page.getByLabel('向影票观提问')).toHaveAttribute('placeholder', '给影票观发送消息')
  await page.getByRole('link', { name: '管理观影偏好' }).click()
  await expect(page).toHaveURL(/\/profile\?edit=preferences$/)
  await expect(page.locator('.el-dialog')).toBeVisible()
  await expect(page.locator('.el-dialog').getByText('观影偏好', { exact: true })).toBeVisible()
})



test('大模型画像展示服务端解读，换一批重新请求动态推荐', async ({ page }) => {
  await mockMoviesBackend(page)
  let requests = 0
  await page.route('**/api/recommend/personal?**', route => {
    requests += 1
    return json(route, [{ ...movies[0], analysisSource: 'model', recommendationReason: `模型推荐理由${requests}` }])
  })
  await page.route('**/api/recommend/profile', route => json(route, {
    needsColdStart: false, favoriteGenres: ['科幻'], viewedCount: 4, favoriteCount: 2,
    purchasedCount: 1, ratedCount: 1, analysisSource: 'model', model: 'test-model',
    summary: '你更偏爱有科学设定的冒险故事，可以尝试太空题材。'
  }))
  await page.goto('/movies')
  await expect(page.locator('.summary-text')).toHaveText('你更偏爱有科学设定的冒险故事，可以尝试太空题材。')
  await expect(page.locator('.aas-sub')).toContainText('大模型分析')
  await page.getByRole('button', { name: '换一批' }).click()
  await expect.poll(() => requests).toBe(2)
  await expect(page.locator('.ai-pick-section')).toContainText('模型推荐理由2')
})


const historicalMovies = [
  { ...movies[0], id: 1, title: '高评分低票房', rating: 9.9, genre: '剧情' },
  { ...movies[0], id: 2, title: '历史票房冠军', rating: 6, genre: '科幻' },
  { ...movies[0], id: 3, title: '同票房较少人次', rating: 8, genre: '剧情' },
  { ...movies[0], id: 4, title: '暂无购票', rating: 10, genre: '剧情' }
]
const historicalRanking = [
  { movieId: 2, movieTitle: '历史票房冠军', boxOffice: 500, ticketCount: 10 },
  { movieId: 3, movieTitle: '同票房较少人次', boxOffice: 500, ticketCount: 5 },
  { movieId: 1, movieTitle: '高评分低票房', boxOffice: 100, ticketCount: 2 },
  { movieId: 4, movieTitle: '暂无购票', boxOffice: 0, ticketCount: 0 }
]

test('热度按累计历史票房排序，评分与类型筛选仍独立可用', async ({ page }) => {
  await mockMoviesBackend(page)
  await page.route('**/api/movie/now-showing', route => json(route, historicalMovies))
  await page.route('**/api/movie/box-office/history?**', route => json(route, historicalRanking))
  await page.goto('/movies')
  const titles = page.locator('.movies-grid .card-title-below')
  await expect(titles).toHaveText(['历史票房冠军', '同票房较少人次', '高评分低票房', '暂无购票'])
  await expect(page.locator('.movies-grid .history-box-office').first()).toHaveText('累计票房 ¥500')
  await expect(page.locator('.movies-grid .history-box-office').last()).toHaveText('累计票房 ¥0')
  await page.getByRole('button', { name: '剧情', exact: true }).click()
  await expect(titles).toHaveText(['同票房较少人次', '高评分低票房', '暂无购票'])
  await page.getByRole('button', { name: '历史票房', exact: true }).click()
  await expect(titles).toHaveText(['暂无购票', '高评分低票房', '同票房较少人次'])
})

test('热映榜也使用历史票房，失败明示并可重试', async ({ page }) => {
  await mockMoviesBackend(page)
  await page.route('**/api/movie/now-showing', route => json(route, historicalMovies))
  let fail = true
  await page.route('**/api/movie/box-office/history?**', route => fail
    ? route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503 }) })
    : json(route, historicalRanking))
  await page.goto('/movies?ranking=hot')
  await expect(page.getByText('历史票房暂不可用，暂按影片原顺序展示。', { exact: false })).toBeVisible()
  await expect(page.locator('.movies-grid .card-title-below')).toHaveText(historicalMovies.map(movie => movie.title))
  fail = false
  await page.locator('.history-box-office-error').getByRole('button', { name: '重试' }).click()
  await expect(page.locator('.movies-grid .card-title-below').first()).toHaveText('历史票房冠军')
  await expect(page.locator('.history-box-office-error')).toHaveCount(0)
  await expect(page.locator('.movies-section .ms-sub')).toContainText('累计历史票房排行')
  await page.locator('.fb-view-btn').nth(1).click()
  await expect(page.locator('.movie-row .row-title').first()).toContainText('历史票房冠军')
  await expect(page.locator('.movie-row .history-box-office').first()).toHaveText('累计票房 ¥500')
})
