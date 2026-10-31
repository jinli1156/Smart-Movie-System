import { expect, test, type Page } from '@playwright/test'

test.use({ actionTimeout: 8_000 })
const prefix = 'admin:page-state:v1:'
const identity = { id: 1, username: 'admin', nickname: '状态测试管理员', role: 'ADMIN' }
const films = [{ id: 1, title: '测试电影一', duration: 120 }, { id: 2, title: '测试电影二', duration: 100 }]
const cinemas = [{ id: 1, name: '测试影院一' }, { id: 2, name: '测试影院二' }]
const halls = [{ id: 1, name: '测试影厅一', cinemaId: 2, status: 1 }, { id: 2, name: '测试影厅二', cinemaId: 2, status: 1 }]
const lists = [
  { path: 'cinema', menu: '影院管理', endpoint: '/api/cinema/page', placeholder: '影院名称或地址', parameter: 'keyword' },
  { path: 'hall', menu: '影厅管理', endpoint: '/api/hall/page', placeholder: '', parameter: 'cinemaId' },
  { path: 'movie', menu: '电影管理', endpoint: '/api/movie/page', placeholder: '搜索电影名称、导演、演员', parameter: 'keyword' },
  { path: 'showtime', menu: '排片管理', endpoint: '/api/showtime/page', placeholder: '', parameter: 'cinemaId' },
  { path: 'order', menu: '订单管理', endpoint: '/api/order/page', placeholder: '订单号', parameter: 'orderNo' },
  { path: 'user', menu: '用户管理', endpoint: '/api/user/page', placeholder: '用户名/昵称/手机/邮箱', parameter: 'keyword' },
  { path: 'coupon-code', menu: '兑换码管理', endpoint: '/api/admin/coupon-codes', placeholder: '兑换码或优惠券名称', parameter: 'keyword' }
]

function dateAfter(days: number) {
  const date = new Date(); date.setDate(date.getDate() + days)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

async function setup(page: Page) {
  await page.route(/https:\/\/fonts\.(googleapis|gstatic)\.com\//, route => route.abort())
  await page.addInitScript(user => {
    // 只在新上下文登录，允许测试切换账号及正常退出。
    if (!localStorage.getItem('userInfo')) {
      localStorage.setItem('token', 'page-state-test')
      localStorage.setItem('userInfo', JSON.stringify(user))
    }
  }, identity)
  const state = { revision: 1, evaluations: 0, requests: [] as URL[] }
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const url = new URL(route.request().url()), path = url.pathname
    state.requests.push(url)
    let data: unknown = []
    if (lists.some(item => item.endpoint === path)) {
      const marker = `最新数据-${state.revision}`
      data = { records: [{
        id: 1, title: marker, name: marker, movieTitle: marker, username: marker,
        orderNo: marker, code: marker, nickname: '测试用户', role: 'USER',
        status: 0, duration: 120, rating: 8, totalRows: 5, totalCols: 5,
        startTime: '2099-01-01T10:00:00', endTime: '2099-01-01T12:00:00',
        redeemedCount: 0, maxRedemptions: 100, amount: 10, minSpend: 20,
        validFrom: '2020-01-01T00:00:00', expireTime: '2099-01-01T00:00:00'
      }], total: 85 }
    }
    if (path === '/api/cinema/all') data = cinemas
    if (path === '/api/movie/now-showing') data = films
    if (path.startsWith('/api/hall/cinema/')) data = halls
    if (path === '/api/statistics/scheduling/workspace') data = { version: 'v1', minimumCleaningMinutes: 15, halls, items: [] }
    if (path === '/api/statistics/scheduling-insights') data = { halls, heatmap: [], summary: {} }
    if (path === '/api/recommend/evaluation') {
      state.evaluations++
      data = { k: Number(url.searchParams.get('k')), evaluatedUsers: 8, skippedUsers: 2,
        precisionAtK: 0.25, recallAtK: 0.5, baselines: [], ablations: [], generatedAt: '2026-10-19T12:00:00' }
    }
    return route.fulfill({ json: { code: 200, data } })
  })
  return state
}

async function navigate(page: Page, name: string, path: string) {
  await page.getByRole('menuitem', { name, exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`/${path}$`))
}

async function select(page: Page, placeholder: string, option: string) {
  await page.locator('.el-select').filter({ hasText: placeholder }).click()
  await page.getByRole('option', { name: option, exact: true }).click()
}

for (const item of lists) {
  test(`${item.menu}切换菜单及刷新后恢复筛选、页码和每页条数，并重新获取数据`, async ({ page }) => {
    const state = await setup(page)
    await page.goto(`/${item.path}`)
    await expect(page.locator('.el-table__body-wrapper .el-table__row')).toHaveCount(1)
    if (item.placeholder) await page.getByPlaceholder(item.placeholder, { exact: true }).fill('保留的查询')
    else await select(page, item.path === 'hall' ? '全部影院' : '选择影院', '测试影院二')
    await page.getByRole('button', { name: /^(搜索|查询)$/ }).click()
    const pagination = page.locator('.el-pagination').first()
    await pagination.locator('.el-select').click()
    await page.getByRole('option', { name: '20/page', exact: true }).click()
    await pagination.getByRole('listitem', { name: 'page 3', exact: true }).click()
    await expect(pagination.locator('.el-pager .is-active')).toHaveText('3')
    const expectedQuery = item.placeholder ? '保留的查询' : '2'
    await expect.poll(() => state.requests.filter(url => url.pathname === item.endpoint).at(-1)?.searchParams.get('page')).toBe('3')

    await navigate(page, '智能排片工作台', 'smart-scheduling')
    state.revision = 2
    const before = state.requests.length
    await navigate(page, item.menu, item.path)
    await expect(page.locator('.el-table__body-wrapper')).toContainText('最新数据-2')
    await expect(pagination.locator('.el-pager .is-active')).toHaveText('3')
    if (item.placeholder) await expect(page.getByPlaceholder(item.placeholder, { exact: true })).toHaveValue('保留的查询')
    else await expect(page.locator('.el-select').filter({ hasText: '测试影院二' })).toBeVisible()
    const restored = state.requests.slice(before).find(url => url.pathname === item.endpoint)!
    expect(Object.fromEntries(restored.searchParams)).toMatchObject({ page: '3', size: '20', [item.parameter]: expectedQuery })

    state.revision = 3
    await page.reload()
    await expect(page.locator('.el-table__body-wrapper')).toContainText('最新数据-3')
    await expect(pagination.locator('.el-pager .is-active')).toHaveText('3')
    expect(Object.fromEntries(state.requests.filter(url => url.pathname === item.endpoint).at(-1)!.searchParams))
      .toMatchObject({ page: '3', size: '20', [item.parameter]: expectedQuery })
  })
}

test('排片保留日期、电影、影院和全部状态，重置后继续使用默认条件', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/showtime')
  await select(page, '选择影院', '测试影院二')
  // 排片的电影选项来自分页接口。
  await select(page, '选择电影', '最新数据-1')
  const date = dateAfter(1)
  await page.getByPlaceholder('选择日期', { exact: true }).fill(date)
  await page.getByPlaceholder('选择日期', { exact: true }).press('Enter')
  await page.getByText('全部', { exact: true }).click()
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await navigate(page, '智能排片工作台', 'smart-scheduling')
  await navigate(page, '排片管理', 'showtime')
  await expect(page.getByPlaceholder('选择日期', { exact: true })).toHaveValue(date)
  await expect(page.getByRole('radio', { name: '全部', exact: true })).toBeChecked()
  let query = state.requests.filter(url => url.pathname === '/api/showtime/page').at(-1)!.searchParams
  expect(query.get('date')).toBe(date)
  expect(query.get('cinemaId')).toBe('2')
  expect(query.get('movieId')).toBe('1')
  expect(query.has('status')).toBe(false)
  await page.reload()
  await expect(page.getByRole('radio', { name: '全部', exact: true })).toBeChecked()
  await page.getByRole('button', { name: '重置', exact: true }).click()
  await navigate(page, '电影管理', 'movie')
  await navigate(page, '排片管理', 'showtime')
  await expect(page.getByPlaceholder('选择日期', { exact: true })).toHaveValue('')
  await expect(page.getByRole('radio', { name: '可售', exact: true })).toBeChecked()
  query = state.requests.filter(url => url.pathname === '/api/showtime/page').at(-1)!.searchParams
  expect(query.get('status')).toBe('0')
  expect(query.has('cinemaId')).toBe(false)
  expect(query.has('movieId')).toBe(false)
  expect(query.has('date')).toBe(false)
})

test('智能排片保留影院、日期、策略和自定义参数，过期日期及无权限影院回退', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/smart-scheduling')
  await expect(page.getByRole('button', { name: '生成排片草案', exact: true })).toBeEnabled()
  await page.locator('.scope-bar .el-select').click()
  await page.getByRole('option', { name: '测试影院二', exact: true }).click()
  await expect(page.locator('.el-loading-mask')).toHaveCount(0)
  const date = dateAfter(2)
  await page.getByRole('combobox', { name: '排片日期', exact: true }).fill(date)
  await page.getByRole('combobox', { name: '排片日期', exact: true }).press('Enter')
  await expect(page.locator('.el-loading-mask')).toHaveCount(0)
  await page.getByRole('button', { name: /丰富片单/ }).click()
  await page.getByRole('spinbutton', { name: '每厅目标场次' }).fill('7')
  await page.getByRole('spinbutton', { name: '清场时间', exact: true }).fill('30')
  await navigate(page, '排片管理', 'showtime')
  await navigate(page, '智能排片工作台', 'smart-scheduling')
  await expect(page.getByRole('spinbutton', { name: '每厅目标场次' })).toHaveValue('7')
  await expect(page.getByRole('spinbutton', { name: '清场时间', exact: true })).toHaveValue('30')
  await expect(page.getByRole('button', { name: /丰富片单/ })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('combobox', { name: '排片日期', exact: true })).toHaveValue(date)
  expect(Object.fromEntries(state.requests.filter(url => url.pathname.endsWith('/scheduling/workspace')).at(-1)!.searchParams))
    .toMatchObject({ cinemaId: '2', targetDate: date })
  await page.reload()
  await expect(page.getByRole('spinbutton', { name: '每厅目标场次' })).toHaveValue('7')
  await page.evaluate(({ prefix }) => {
    const key = Object.keys(sessionStorage).find(key => key.startsWith(prefix) && key.endsWith(':smart-scheduling'))!
    const value = JSON.parse(sessionStorage.getItem(key)!)
    value.cinemaId = 999
    value.targetDate = '2000-01-01'
    sessionStorage.setItem(key, JSON.stringify(value))
  }, { prefix })
  await page.reload()
  await expect(page.getByRole('combobox', { name: '排片日期', exact: true })).toHaveValue(dateAfter(1))
  await expect.poll(() => state.requests.filter(url => url.pathname.endsWith('/scheduling/workspace')).at(-1)?.searchParams.get('cinemaId')).toBe('1')
})

test('经营分析抽屉再次打开时保留预测条件和热力图影厅', async ({ page }) => {
  await setup(page)
  await page.goto('/smart-scheduling')
  await page.getByRole('button', { name: '经营分析', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '经营分析与预测工具' })
  await expect(drawer.locator('.el-loading-mask')).toHaveCount(0)
  await drawer.locator('.predict-card .el-select').nth(0).click()
  await page.getByRole('option', { name: '测试电影二', exact: true }).click()
  await drawer.locator('.predict-card .el-select').nth(1).click()
  await page.getByRole('option', { name: '测试影厅二', exact: true }).click()
  await drawer.getByRole('button', { name: 'Close this dialog' }).click()
  await expect(drawer).toHaveCount(0)
  await page.getByRole('button', { name: '经营分析', exact: true }).click()
  await expect(drawer.locator('.el-loading-mask')).toHaveCount(0)
  await expect(drawer.locator('.predict-card')).toContainText('测试电影二')
  await expect(drawer.locator('.predict-card')).toContainText('测试影厅二')
})

test('评估参数和已完成的结果在切换及刷新后保留，不自动重跑实验', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/recommendation-evaluation')
  await page.locator('.controls .el-select').click()
  await page.getByRole('option', { name: '10', exact: true }).click()
  await page.getByRole('button', { name: '运行评估' }).click()
  await expect(page.locator('.summary')).toContainText('有效用户8')
  await navigate(page, '订单管理', 'order')
  await navigate(page, '推荐算法评估', 'recommendation-evaluation')
  await expect(page.locator('.summary')).toContainText('本次 K 值10')
  await expect(page.getByRole('button', { name: '导出实验结果' })).toBeEnabled()
  await page.reload()
  await expect(page.locator('.summary')).toContainText('有效用户8')
  expect(state.evaluations).toBe(1)
})

test('查询状态按账号和影院权限隔离，退出登录清理状态', async ({ page }) => {
  await setup(page)
  await page.goto('/showtime')
  await select(page, '选择影院', '测试影院二')
  await page.evaluate(() => localStorage.setItem('userInfo', JSON.stringify({ id: 2, username: 'other', role: 'ADMIN' })))
  await page.reload()
  await expect(page.locator('.search-bar .el-select').filter({ hasText: '测试影院二' })).toHaveCount(0)
  await page.evaluate(user => localStorage.setItem('userInfo', JSON.stringify(user)), identity)
  await page.reload()
  await expect(page.locator('.search-bar .el-select').filter({ hasText: '测试影院二' })).toBeVisible()
  await page.evaluate(user => localStorage.setItem('userInfo', JSON.stringify({ ...user, role: 'CINEMA_ADMIN', cinemaId: 1 })), identity)
  await page.reload()
  await expect(page.locator('.search-bar .el-select').filter({ hasText: '测试影院二' })).toHaveCount(0)
  await page.locator('.user-menu').click()
  await page.getByRole('menuitem', { name: '退出登录', exact: true }).click()
  await page.getByRole('button', { name: '确认退出', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(await page.evaluate(prefix => Object.keys(sessionStorage).filter(key => key.startsWith(prefix)), prefix)).toEqual([])
})

test('缓存损坏或浏览器禁止存储时页面仍可使用', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/order')
  await page.getByPlaceholder('订单号', { exact: true }).fill('内存恢复')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await page.evaluate(prefix => {
    const key = Object.keys(sessionStorage).find(key => key.startsWith(prefix) && key.endsWith(':order'))!
    sessionStorage.setItem(key, '{invalid')
  }, prefix)
  await page.reload()
  await expect(page.getByPlaceholder('订单号', { exact: true })).toHaveValue('')
  await page.evaluate(() => {
    const storage = window.sessionStorage
    Object.defineProperty(window, 'sessionStorage', { configurable: true, get() { throw new Error('disabled') } })
    // 保留 localStorage，模拟仅会话缓存被浏览器拒绝。
    storage.clear()
  })
  await page.getByPlaceholder('订单号', { exact: true }).fill('内存恢复')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await navigate(page, '影院管理', 'cinema')
  await navigate(page, '订单管理', 'order')
  await expect(page.getByPlaceholder('订单号', { exact: true })).toHaveValue('内存恢复')
  expect(state.requests.filter(url => url.pathname === '/api/order/page').at(-1)!.searchParams.get('orderNo')).toBe('内存恢复')
})
