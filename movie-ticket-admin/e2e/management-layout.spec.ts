import { expect, test, type Page } from '@playwright/test'

test.use({ actionTimeout: 8_000 })
const cinemaName = 'UME国际影城（南宁青秀万象城激光IMAX旗舰店）'
const movieTitle = '年会不能停！2：重返职场的奇妙冒险'
const poster = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="60" height="80"><rect width="60" height="80" fill="#527c69"/></svg>')
const pages = [
  { path: 'showtime', endpoint: '/api/showtime/page', labels: ['ID', '电影', '影院', '影厅', '开始时间', '结束时间', '票价', '座位', '状态', '操作'] },
  { path: 'movie', endpoint: '/api/movie/page', labels: ['ID', '海报', '电影名称', '英文名称', '导演', '主演', '类型', '时长', '上映日期', '评分', '状态', '操作'] },
  { path: 'cinema', endpoint: '/api/cinema/page', labels: ['ID', '影院名称', '地址', '电话', '状态', '创建时间', '操作'] },
  { path: 'hall', endpoint: '/api/hall/page', labels: ['ID', '所属影院', '影厅名称', '座位', '状态', '创建时间', '操作'] },
  { path: 'order', endpoint: '/api/order/page', labels: ['订单号', '电影', '影厅', '放映时间', '座位', '用户', '金额', '状态', '下单时间', '核销状态', '操作'] },
  { path: 'user', endpoint: '/api/user/page', labels: ['ID', '头像', '用户名', '昵称', '手机', '邮箱', '角色', '状态', '所属影院', '注册时间', '操作'] },
  { path: 'coupon-code', endpoint: '/api/admin/coupon-codes', labels: ['兑换码', '优惠券名称', '优惠规则', '兑换进度', '领取后有效期', '活动时间', '状态', '操作'] }
]

async function setup(page: Page, empty = false) {
  await page.route(/https:\/\/fonts\.(googleapis|gstatic)\.com\//, route => route.abort())
  await page.addInitScript(() => {
    localStorage.setItem('token', 'layout-test')
    localStorage.setItem('userInfo', JSON.stringify({ id: 1, username: 'admin', role: 'ADMIN' }))
  })
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = []
    if (pages.some(item => item.endpoint === path)) data = {
      records: empty ? [] : [0, 1].map(index => ({
        id: 8664 + index, title: movieTitle, movieTitle, moviePoster: poster, poster,
        movieDuration: 125, cinemaId: 2, cinemaName, hallName: '一号激光全景声影厅',
        name: path === '/api/cinema/page' ? cinemaName : '周末观影满八十元立减二十元专享优惠券',
        address: '广西壮族自治区南宁市青秀区民族大道万象城购物中心第五层', phone: '13800138000',
        englishTitle: 'The Extraordinary Adventures of the Annual Meeting: Back to Work',
        director: '董润年、马克·克里斯托弗', actors: '大鹏、白客、庄达菲、李乃文、王迅、孙艺洲',
        genre: '喜剧、剧情', duration: 125, releaseDate: '2026-10-25', rating: 8.6,
        status: index, totalRows: 12, totalCols: 15, availableSeats: 150, totalSeats: 180,
        startTime: '2026-10-25T22:05:00', endTime: '2026-10-26T00:10:00', basePrice: 56,
        createTime: '2026-10-19T10:15:30', orderNo: 'ORDER202610191015301234567890',
        showtime: '2026-10-25T22:05:00', totalPrice: 168, seats: '10排10座、10排11座、10排12座',
        userName: '测试购票用户的完整昵称', username: 'cinema_manager_nanning', nickname: '南宁万象城影院管理员',
        email: 'nanning.cinema.administrator@example.com', role: 'CINEMA_ADMIN',
        code: 'WEEKEND-CINEMA-20261025', amount: 20, minSpend: 80, validDays: 30,
        redeemedCount: 125, maxRedemptions: 1000, validFrom: '2026-10-01T00:00:00', expireTime: '2026-11-30T23:59:59'
      })), total: empty ? 0 : 150
    }
    if (path === '/api/cinema/all') data = [{ id: 2, name: cinemaName }]
    if (path === '/api/movie/now-showing') data = [{ id: 1, title: movieTitle, duration: 125 }]
    if (path === '/api/statistics/scheduling/workspace') data = { version: 'v1', minimumCleaningMinutes: 15, halls: [], items: [] }
    if (path === '/api/statistics/scheduling-insights') data = {
      halls: [], heatmap: [], summary: {},
      lowOccupancyShowtimes: [{ showtimeId: 1, movieTitle, hallName: '一号激光全景声影厅', startTime: '2026-10-25T22:05:00', predictedRate: 20, reason: '历史客流不足，建议适当调整影片与开场时间。' }],
      frequencyRecommendations: [{ movieTitle, historicalRate: 32, historicalSamples: 100, currentWeeklyShowtimes: 20, suggestedChange: -5, action: 'REDUCE', reason: '未来一周场次供给高于历史同期观影需求，请减少重复场次。' }]
    }
    if (path === '/api/admin/coupon-codes/redemptions') data = { total: 150, records: [{
      code: 'WEEKEND-CINEMA-20261025', couponName: '周末观影满八十元立减二十元专享优惠券',
      username: 'cinema_manager_nanning', nickname: '南宁万象城影院管理员', couponStatus: 1,
      redeemTime: '2026-10-19T10:15:30', useTime: '2026-10-25T22:05:00'
    }] }
    return route.fulfill({ json: { code: 200, data } })
  })
}

async function overflow(page: Page, selector = '.management-table') {
  return page.evaluate(selector => {
    const issues: string[] = []
    if (document.documentElement.scrollWidth > innerWidth + 1) issues.push('页面横向溢出')
    for (const table of document.querySelectorAll(selector)) {
    for (const element of table.querySelectorAll<HTMLElement>('.el-scrollbar__wrap, .cell, .movie-title, .table-actions, .el-rate, .el-tag')) {
      if (element.clientWidth > 0 && element.scrollWidth > element.clientWidth + 1) {
        issues.push(`${element.className} ${element.textContent?.trim().slice(0, 35)}: ${element.scrollWidth}/${element.clientWidth}`)
      }
    }
    const bounds = table.getBoundingClientRect()
    for (const cell of table.querySelectorAll<HTMLElement>('td.el-table__cell')) {
      const rect = cell.getBoundingClientRect()
      if (rect.left < bounds.left - 1 || rect.right > bounds.right + 1) issues.push(`列超出表格: ${cell.innerText}`)
    }
    }
    return issues
  }, selector)
}

for (const item of pages) {
  test(`${item.path}在桌面、笔记本及手机宽度展示完整字段且无需横向滚动`, async ({ page }) => {
    await setup(page)
    await page.setViewportSize({ width: 1920, height: 1080 })
    await page.goto(`/${item.path}`)
    const table = page.locator('.management-table').first()
    await expect(table.locator('.el-table__row')).toHaveCount(2)
    for (const width of [1920, 1440, 1280, 1024, 768, 390, 320]) {
      await test.step(`${width}px`, async () => {
        await page.setViewportSize({ width, height: 1080 })
        await expect.poll(() => overflow(page)).toEqual([])
        const row = table.locator('.el-table__row').first()
        const labels = await row.locator('td .cell').evaluateAll(cells => cells.map(cell =>
          JSON.parse((cell as HTMLElement).style.getPropertyValue('--management-label') || getComputedStyle(cell).getPropertyValue('--management-label'))))
        for (const label of item.labels) expect(labels).toContain(label)
        await expect(row.getByRole('button').first()).toBeVisible()
        if ((item.path === 'showtime' && [1440, 390].includes(width)) || (item.path === 'movie' && width === 1280)) {
          await page.screenshot({ path: `../output/admin-layout/${item.path}-${width}.png`, fullPage: true })
        }
      })
    }
    if (item.path === 'showtime') await expect(table).toContainText(cinemaName)
    if (item.path === 'user') await expect(table).toContainText('nanning.cinema.administrator@example.com')
  })
}

test('窄屏保留全选、清空选择和编辑入口，调整宽度后恢复表格', async ({ page }) => {
  await setup(page)
  await page.setViewportSize({ width: 390, height: 1080 })
  await page.goto('/showtime')
  const table = page.locator('.management-table')
  await expect(table).toHaveClass(/management-table--stacked/)
  await table.locator('.el-table__header-wrapper .el-checkbox').click()
  await expect(page.getByLabel('批量操作', { exact: true })).toContainText('已选 2 项')
  await page.getByRole('button', { name: '取消选择', exact: true }).click()
  await expect(page.getByLabel('批量操作', { exact: true })).toContainText('已选 0 项')
  await table.getByRole('button', { name: '编辑', exact: true }).first().click()
  await expect(page.getByRole('dialog', { name: '编辑排片' })).toBeVisible()
  await page.getByRole('dialog', { name: '编辑排片' }).getByRole('button', { name: '取消', exact: true }).click()
  await page.setViewportSize({ width: 1440, height: 1080 })
  await expect(table).not.toHaveClass(/management-table--stacked/)
  await expect.poll(() => overflow(page)).toEqual([])
})

test('空列表在窄屏仍显示筛选、空状态及分页入口', async ({ page }) => {
  await setup(page, true)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/showtime')
  await expect(page.locator('.el-table__empty-block')).toBeVisible()
  await expect(page.getByRole('button', { name: '搜索', exact: true })).toBeVisible()
  await expect(page.locator('.el-pagination')).toBeVisible()
  await expect.poll(() => overflow(page)).toEqual([])
})

test('兑换记录和经营分析抽屉中的表格同样展示完整字段', async ({ page }) => {
  await setup(page)
  await page.goto('/coupon-code')
  await page.getByRole('button', { name: '全部兑换记录', exact: true }).click()
  let drawer = page.getByRole('dialog', { name: '全部兑换记录', exact: true })
  await expect(drawer.locator('.el-table__row')).toHaveCount(1)
  for (const width of [1280, 390]) {
    await page.setViewportSize({ width, height: 1080 })
    await expect.poll(() => overflow(page, '.el-drawer .management-table')).toEqual([])
    await expect.poll(() => drawer.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true)
  }
  await drawer.getByRole('button', { name: 'Close this dialog' }).click()
  await page.goto('/smart-scheduling')
  await page.getByRole('button', { name: '经营分析', exact: true }).click()
  drawer = page.getByRole('dialog', { name: '经营分析与预测工具' })
  await expect(drawer.locator('.el-table__row')).toHaveCount(2)
  for (const width of [1280, 390]) {
    await page.setViewportSize({ width, height: 1080 })
    await expect.poll(() => overflow(page, '.el-drawer .management-table')).toEqual([])
    await expect(drawer.getByRole('button', { name: '模拟调整', exact: true })).toBeVisible()
  }
})
