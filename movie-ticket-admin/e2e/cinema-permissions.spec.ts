import { expect, test, type Page, type Route } from '@playwright/test'

const ok = (route: Route, data: unknown) => route.fulfill({
  status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data })
})
const paged = (records: unknown[]) => ({ records, total: records.length, current: 1, size: 10 })
const cinemas = [{ id: 10, name: '测试东城影院' }, { id: 20, name: '测试西城影院' }]

async function identity(page: Page, role = 'ADMIN', cinemaId: number | null = null) {
  await page.addInitScript(({ role, cinemaId }) => {
    localStorage.setItem('token', 'permission-browser-test')
    localStorage.setItem('userInfo', JSON.stringify({ id: 1, username: 'test_admin', role, cinemaId }))
  }, { role, cinemaId })
}

async function fallback(page: Page) {
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/statistics/dashboard') return ok(route, {})
    if (path === '/api/cinema/page') return ok(route, paged(cinemas))
    if (path.endsWith('/page')) return ok(route, paged([]))
    return ok(route, [])
  })
}

test('平台管理员必须选择所属影院，保存及降级时提交正确关联', async ({ page }) => {
  await identity(page)
  await fallback(page)
  let user = { id: 2, username: 'cinema_manager', nickname: '影院经理', role: 'CINEMA_ADMIN', cinemaId: null as number | null, status: 1 }
  const writes: Record<string, unknown>[] = []
  await page.route('**/api/user/page**', route => ok(route, paged([user])))
  await page.route('**/api/user/2', route => {
    const body = route.request().postDataJSON()
    writes.push(body)
    user = { ...user, ...body }
    return ok(route, null)
  })
  await page.goto('/user')
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '编辑用户' })
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog.getByText('请分配所属影院')).toBeVisible()
  expect(writes).toHaveLength(0)
  await dialog.locator('.el-form-item').filter({ hasText: '所属影院' }).locator('.el-select').click()
  await page.getByRole('option', { name: '测试东城影院' }).click()
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes[0]).toMatchObject({ role: 'CINEMA_ADMIN', cinemaId: 10 })
  await expect(page.getByRole('cell', { name: '测试东城影院', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await dialog.locator('.el-form-item').filter({ hasText: /^角色/ }).locator('.el-select').click()
  await page.getByRole('option', { name: '普通用户', exact: true }).click()
  await dialog.getByRole('button', { name: '确定', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(writes[1]).toMatchObject({ role: 'USER', cinemaId: null })
})

test('影院管理员隐藏平台入口且不能通过地址进入', async ({ page }) => {
  await identity(page, 'CINEMA_ADMIN', 10)
  await fallback(page)
  for (const path of ['/movie', '/user', '/recommendation-evaluation']) {
    await page.goto(path)
    await expect(page).toHaveURL(/\/dashboard$/)
  }
  await expect(page.getByRole('menuitem', { name: '推荐算法评估' })).toHaveCount(0)
  await page.goto('/cinema')
  await expect(page.getByRole('button', { name: /添加影院|新增影院|批量删除/ })).toHaveCount(0)
})

test('核销失败保留取票码，成功后刷新状态且隐藏退票操作', async ({ page }) => {
  await identity(page, 'CINEMA_ADMIN', 10)
  await fallback(page)
  let picked = false
  let calls = 0
  const order = () => ({ id: 9, orderNo: 'SCOPE-ORDER-9', userName: '购票用户', movieTitle: '测试电影',
    cinemaName: '测试东城影院', hallName: '一号厅', status: 1, isPicked: picked ? 1 : 0, totalPrice: 40 })
  await page.route('**/api/order/page**', route => ok(route, paged([order()])))
  await page.route('**/api/order/pick?**', route => {
    calls++
    expect(new URL(route.request().url()).searchParams.get('ticketCode')).toBe('ABC123')
    if (calls === 1) return route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ message: '权限不足' }) })
    picked = true
    return ok(route, order())
  })
  await page.goto('/order')
  await page.getByPlaceholder('输入取票码').fill(' ABC123 ')
  await page.getByRole('main').getByRole('button', { name: '确认核销', exact: true }).click()
  await page.getByRole('dialog', { name: '取票核销' }).getByRole('button', { name: '确认核销' }).click()
  await expect(page.getByText('权限不足', { exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('输入取票码')).toHaveValue(' ABC123 ')
  await expect(page.getByRole('cell', { name: '未核销', exact: true })).toBeVisible()
  await page.getByRole('main').getByRole('button', { name: '确认核销', exact: true }).click()
  await page.getByRole('dialog', { name: '取票核销' }).getByRole('button', { name: '确认核销' }).click()
  await expect(page.getByRole('cell', { name: '已核销', exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('输入取票码')).toHaveValue('')
  await expect(page.getByRole('button', { name: '退票', exact: true })).toHaveCount(0)
})

const evaluation = (k = 6, evaluatedUsers = 12) => ({
  k, evaluatedUsers, skippedUsers: 3, generatedAt: '2026-09-27T14:20:00',
  precisionAtK: 0.1, recallAtK: 0.6, ndcgAtK: 0.5, catalogCoverage: 0.8, diversity: 0.7,
  protocol: '按时间留出，历史行为截止于测试事件。', candidatePolicy: '当时已创建且未来七天存在场次的影片。',
  baselines: [{ key: 'POPULAR', name: '热门基线', precisionAtK: 0.05, recallAtK: 0.3, ndcgAtK: 0.2,
    catalogCoverage: 0.4, diversity: 0.5, recallChangeVsFull: -0.3, description: '仅使用历史热度' }],
  ablations: [{ key: 'NO_SCENE', name: '去除场景', precisionAtK: 0.08, recallAtK: 0.48, ndcgAtK: 0.4,
    catalogCoverage: 0.7, diversity: 0.6, recallChangeVsFull: -0.12, description: '去除场次与距离特征' }]
})

test('评估页面使用所选 K、展示真实指标并导出 JSON', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 1000 })
  await identity(page)
  await fallback(page)
  let selectedK = 0
  await page.route('**/api/recommend/evaluation?**', route => {
    selectedK = Number(new URL(route.request().url()).searchParams.get('k'))
    return ok(route, evaluation(selectedK))
  })
  await page.goto('/recommendation-evaluation')
  await expect(page.getByRole('button', { name: '导出实验结果' })).toBeDisabled()
  await page.locator('.controls .el-select').click()
  await page.getByRole('option', { name: '5', exact: true }).click()
  await page.getByRole('button', { name: '运行评估' }).click()
  await expect(page.getByRole('cell', { name: '热门基线', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: '0.6000', exact: true }).first()).toBeVisible()
  expect(selectedK).toBe(5)
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出实验结果' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('recommendation-evaluation-k5.json')
  const stream = await download.createReadStream()
  const chunks: Buffer[] = []
  for await (const chunk of stream!) chunks.push(chunk)
  expect(JSON.parse(Buffer.concat(chunks).toString())).toMatchObject({ k: 5, evaluatedUsers: 12 })
  await page.screenshot({ path: '../output/cinema-recommendation-evaluation.png', fullPage: true })
})

test('样本不足与接口失败时不把空结果展示为有效实验', async ({ page }) => {
  await identity(page)
  await fallback(page)
  let failed = false
  await page.route('**/api/recommend/evaluation?**', route => failed
    ? route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ message: '测试失败' }) })
    : ok(route, evaluation(6, 0)))
  await page.goto('/recommendation-evaluation')
  await page.getByRole('button', { name: '运行评估' }).click()
  await expect(page.getByText(/有效样本不足：/)).toBeVisible()
  await expect(page.getByRole('cell', { name: '0.6000', exact: true })).toHaveCount(0)
  failed = true
  await page.getByRole('button', { name: '运行评估' }).click()
  await expect(page.getByText('评估失败，请检查服务和数据后重试。')).toBeVisible()
  await expect(page.getByRole('button', { name: '导出实验结果' })).toBeDisabled()
})
