import { expect, test, type Page, type Route } from '@playwright/test'
const ok = (route: Route, data: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j4WQAAAAASUVORK5CYII=', 'base64')
async function setup(page: Page, role = 'ADMIN') {
  await page.addInitScript(role => {
    localStorage.setItem('token', 'marketing-browser-test')
    localStorage.setItem('userInfo', JSON.stringify({ id: 1, username: 'test', nickname: '影院运营', role, cinemaId: role === 'CINEMA_ADMIN' ? 10 : null }))
  }, role)
  const asset = (id: string) => ({ id, movieId: 1, previewUrl: '/api/admin/marketing/assets/' + id + '/file', style: '简洁', ratio: '3:4', width: 864, height: 1152, sourceType: 'MODEL_GENERATED', model: 'glm-image' })
  const detail: any = {
    id: 'c1', cinemaId: 10, name: '周末观影计划', brief: '为周末结伴观影的朋友创作温暖的宣传内容', audience: '结伴观影的朋友',
    styles: ['简洁'], ratios: ['3:4'], movieSnapshot: [{ id: 1, title: '银幕上的旅途', genre: '冒险' }], createdAt: '2026-10-30T10:00:00',
    tasks: [{ id: 't1', batchId: 'b1', movieId: 1, kind: 'COPY', status: 'SUCCEEDED', attempts: 1, providerCalls: 1, model: 'test-text', result: { titles: ['周末，一起看好故事', '把时间留给银幕', '和朋友共享电影时光'], shortCopy: '和朋友走进影院，发现银幕上的温暖旅途。' } }],
    assets: [asset('a1')], versions: [], publications: [], logs: []
  }
  const writes: { path: string; body: any }[] = []
  const capabilities = { imageConfigured: true, textConfigured: true, posterContentRequired: true, imageModel: 'glm-image', maxImagesPerBatch: 18, dailyImageLimit: 60 }
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request(), path = new URL(request.url()).pathname, method = request.method()
    if (path.endsWith('/file')) {
      expect(request.headers().authorization).toBe('Bearer marketing-browser-test')
      return route.fulfill({ status: 200, contentType: 'image/png', body: pixel })
    }
    if (method === 'POST') {
      const body = request.postData() ? request.postDataJSON() : null
      writes.push({ path, body })
      if (path.endsWith('/batches')) {
        if (body.kind !== 'COPY') for (let i = 0; i < body.imageCount; i++) detail.tasks.push({ id: 'image-' + i, batchId: 'b2', movieId: body.posterContent.movieId, kind: 'IMAGE', style: '简洁', ratio: '3:4', status: 'QUEUED', attempts: 0, providerCalls: 0, result: { posterContent: body.posterContent } })
        return ok(route, { id: 'b2' })
      }
      if (path.endsWith('/versions')) {
        detail.assets.unshift(asset('a2'))
        const version = { id: 'v1', movieId: body.movieId, version: 1, status: 'DRAFT', payload: { title: body.title, copy: body.copy, assetId: 'a2', cta: '查看场次' }, createdAt: '2026-10-30T10:01:00' }
        detail.versions.unshift(version); return ok(route, version)
      }
      if (path.endsWith('/submit-review')) detail.versions[0].status = 'PENDING_REVIEW'
      if (path.endsWith('/review')) detail.versions[0].status = body.approve ? 'APPROVED' : 'REJECTED'
      if (path.endsWith('/publish')) {
        detail.versions[0].status = 'PUBLISHED'
        detail.publications.push({ id: 'p1', contentVersionId: 'v1', slot: body.slot, startAt: body.startAt || '2026-01-01T00:00:00', endAt: body.endAt, status: 'PUBLISHED' })
      }
      if (path.endsWith('/offline')) detail.publications[0].status = 'OFFLINE'
      if (path.endsWith('/retry')) detail.tasks.find((t: any) => t.id === 'unknown').status = 'QUEUED'
      return ok(route, {})
    }
    if (path.endsWith('/capabilities')) return ok(route, capabilities)
    if (path === '/api/admin/marketing/campaigns') return ok(route, [detail])
    if (path === '/api/admin/marketing/campaigns/c1') return ok(route, detail)
    if (path === '/api/cinema/all') return ok(route, [{ id: 10, name: '测试东城影院' }, { id: 20, name: '测试西城影院' }])
    if (path === '/api/movie/page') return ok(route, { records: [{ id: 1, title: '银幕上的旅途', status: 1 }], total: 1 })
    return ok(route, [])
  })
  return { detail, writes, capabilities }
}

test('图文选择、保存、审核、发布和撤下形成完整页面流程', async ({ page }, testInfo) => {
  const state = await setup(page)
  await page.goto('/marketing/studio?campaign=c1')
  await expect(page.getByRole('heading', { name: '让好电影，被更多人看见' })).toBeVisible()
  const editor = page.getByRole('region', { name: '编辑发布内容', exact: true })
  await expect(editor.getByRole('button', { name: '生成海报（1 张）' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '全部生成' })).toHaveCount(0)
  await page.getByRole('button', { name: /周末，一起看好故事.*选用/ }).click()
  const count = editor.getByRole('spinbutton', { name: '海报生成张数' })
  await expect(count).toHaveValue('1')
  await count.fill('3')
  await count.press('Tab')
  await page.getByRole('button', { name: '生成海报（3 张）' }).click()
  await expect(page.getByText('将为《银幕上的旅途》根据营销标题「周末，一起看好故事」生成 3 张宣传海报，先由语言模型编写提示词，再调用生图模型。')).toBeVisible()
  await page.getByRole('button', { name: '开始生成', exact: true }).click()
  await expect.poll(() => state.writes.filter(w => w.path.endsWith('/batches')).length).toBe(1)
  expect(state.writes.find(w => w.path.endsWith('/batches'))?.body).toMatchObject({ kind: 'IMAGE', imageCount: 3, posterContent: { movieId: 1, title: '周末，一起看好故事', copy: '和朋友走进影院，发现银幕上的温暖旅途。' } })
  await expect(page.getByRole('region', { name: '海报生成进度' })).toBeVisible()
  await expect(page.locator('.generation-card')).toHaveCount(3)
  for (const steps of await page.locator('.generation-steps').all())
    await expect.poll(() => steps.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
  await page.locator('.asset-choice').first().click()
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: testInfo.outputPath('marketing-studio.png'), fullPage: true })
  await page.getByRole('button', { name: '保存内容版本' }).click()
  await expect(page.getByText('V1 · 周末，一起看好故事', { exact: true })).toBeVisible()
  expect(state.writes.find(w => w.path.endsWith('/versions'))?.body).toMatchObject({ movieId: 1, assetId: 'a1' })
  await page.getByRole('button', { name: '提交审核', exact: true }).click()
  await expect(page.getByText('银幕上的旅途 · 待审核')).toBeVisible()
  await page.locator('.studio-nav').getByRole('link', { name: '审核与发布' }).click()
  await page.getByRole('button', { name: '预览与对照' }).click()
  await expect(page.getByRole('dialog', { name: '内容预览与版本对照' })).toBeVisible()
  await page.getByRole('dialog').getByRole('button', { name: 'Close this dialog' }).click()
  await page.getByRole('button', { name: '审核通过', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '审核通过', exact: true }).click()
  await page.getByRole('button', { name: '发布到站内' }).click()
  await page.getByRole('button', { name: '确认发布', exact: true }).click()
  await expect(page.getByRole('cell', { name: '展示中', exact: true })).toBeVisible()
  expect(state.writes.find(w => w.path.endsWith('/publish'))?.body.idempotencyKey).toBeTruthy()
  await page.getByRole('button', { name: '撤下', exact: true }).click()
  await expect(page.getByRole('cell', { name: '已撤下', exact: true })).toBeVisible()
})

test('影院管理员限定影院且不显示审核发布按钮', async ({ page }) => {
  const state = await setup(page, 'CINEMA_ADMIN')
  state.detail.versions = [{ id: 'v1', movieId: 1, version: 1, status: 'PENDING_REVIEW', payload: { title: '待审核内容', copy: '人工确认后发布', assetId: 'a1' }, createdAt: '2026-10-30T10:00:00' }]
  await page.goto('/marketing/review?campaign=c1')
  await expect(page.getByRole('heading', { name: 'V1 · 待审核内容' })).toBeVisible()
  await expect(page.getByRole('button', { name: /审核通过|发布到站内|退回/ })).toHaveCount(0)
  await page.getByRole('button', { name: '新建营销需求' }).click()
  const dialog = page.getByRole('dialog', { name: '新建营销需求' })
  await dialog.locator('.el-select').first().click()
  await expect(page.getByRole('option', { name: '测试东城影院' })).toBeVisible()
  await expect(page.getByRole('option', { name: '测试西城影院' })).toHaveCount(0)
})

test('不确定的付费请求需要人工核实确认后才可重试', async ({ page }) => {
  const state = await setup(page)
  state.detail.tasks.push({ id: 'unknown', batchId: 'b1', movieId: 1, kind: 'IMAGE', style: '简洁', ratio: '3:4', status: 'SUBMIT_UNKNOWN', attempts: 1, providerCalls: 1 })
  await page.goto('/marketing/tasks?campaign=c1')
  await page.getByRole('button', { name: '核实后重试' }).click()
  expect(state.writes.filter(w => w.path.endsWith('/retry'))).toHaveLength(0)
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '核实后重试' }).click()
  await page.getByRole('button', { name: '已核实，重新生成', exact: true }).click()
  await expect.poll(() => state.writes.filter(w => w.path.endsWith('/retry')).length).toBe(1)
  expect(state.writes.find(w => w.path.endsWith('/retry'))?.body).toEqual({ acknowledgePossibleCharge: true })
})

test('海报动画随构思、生图、保存状态自动更新并在完成后显示素材', async ({ page }, testInfo) => {
  const state = await setup(page)
  await page.goto('/marketing/studio?campaign=c1')
  await page.getByRole('button', { name: /周末，一起看好故事.*选用/ }).click()
  await page.getByRole('button', { name: '生成海报（1 张）' }).click()
  await page.getByRole('button', { name: '开始生成', exact: true }).click()
  const progress = page.getByRole('region', { name: '海报生成进度' })
  await expect(progress.getByText('等待处理', { exact: true })).toBeVisible()
  const task = state.detail.tasks.find((task: any) => task.id === 'image-0')
  task.status = 'PLANNING'
  await expect(progress.getByText('正在构思画面', { exact: true })).toBeVisible()
  await progress.screenshot({ path: testInfo.outputPath('marketing-generation-desktop.png') })
  await page.locator('.studio-nav').getByRole('link', { name: '任务与素材' }).click()
  await expect(progress.getByText('正在构思画面', { exact: true })).toBeVisible()
  task.status = 'SUBMITTING'
  await expect(progress.getByText('正在生成图片', { exact: true })).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  await expect.poll(() => progress.locator('.generation-steps').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
  await progress.screenshot({ path: testInfo.outputPath('marketing-generation-mobile.png') })
  task.status = 'PERSISTING'
  await expect(progress.getByText('正在保存素材', { exact: true })).toBeVisible()
  task.status = 'SUCCEEDED'
  state.detail.assets.push({ ...state.detail.assets[0], id: 'generated', previewUrl: '/api/admin/marketing/assets/generated/file' })
  await expect(progress).toHaveCount(0)
  await expect(page.locator('.gallery-card')).toHaveCount(2)
})

for (const status of ['FAILED', 'SUBMIT_UNKNOWN']) {
  test('图片任务进入 ' + status + ' 后停止生成动画并展示处理操作', async ({ page }) => {
    const state = await setup(page)
    const task = { id: 'image-0', batchId: 'b2', movieId: 1, kind: 'IMAGE', style: '简洁', ratio: '3:4', status: 'PLANNING', attempts: 1, providerCalls: 0, errorMessage: '' }
    state.detail.tasks.push(task)
    await page.goto('/marketing/tasks?campaign=c1')
    await expect(page.getByRole('region', { name: '海报生成进度' })).toBeVisible()
    task.status = status; task.errorMessage = status === 'FAILED' ? '绘图指令生成失败，请重试' : '请求结果待核实'
    await expect(page.getByRole('region', { name: '海报生成进度' })).toHaveCount(0)
    await expect(page.getByRole('cell', { name: task.errorMessage, exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: status === 'FAILED' ? '重试' : '核实后重试', exact: true })).toBeVisible()
  })
}

test('缺少语言模型配置时提示配置并禁用海报生成', async ({ page }) => {
  const state = await setup(page)
  state.capabilities.textConfigured = false
  await page.goto('/marketing/studio?campaign=c1')
  await expect(page.getByText('语言模型尚未配置，配置后即可生成文案和绘图指令。')).toBeVisible()
  await page.getByRole('button', { name: /周末，一起看好故事.*选用/ }).click()
  await expect(page.getByRole('button', { name: '生成海报（1 张）' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '全部生成' })).toHaveCount(0)
})

test('海报使用编辑区最新文案，提交后继续编辑不改变正在生成的任务', async ({ page }) => {
  const state = await setup(page)
  state.capabilities.imageModel = 'gpt-image-2'
  await page.goto('/marketing/studio?campaign=c1')
  await page.getByRole('button', { name: /周末，一起看好故事.*选用/ }).click()
  const editor = page.getByRole('region', { name: '编辑发布内容', exact: true })
  await expect(editor.getByText('生图模型：gpt-image-2', { exact: true })).toBeVisible()
  await editor.getByRole('textbox', { name: '营销标题', exact: true }).fill('把周末留给星光')
  await editor.getByRole('textbox', { name: '短文案', exact: true }).fill('在蓝色星海般的光影中，和朋友一起奔赴银幕上的旅途。')
  await editor.getByRole('button', { name: '生成海报（1 张）' }).click()
  await expect(page.getByText(/将为《银幕上的旅途》根据营销标题「把周末留给星光」生成 1 张宣传海报/)).toBeVisible()
  await page.getByRole('button', { name: '开始生成', exact: true }).click()
  await expect(editor.getByRole('region', { name: '海报生成进度' })).toBeVisible()
  const content = { movieId: 1, title: '把周末留给星光', copy: '在蓝色星海般的光影中，和朋友一起奔赴银幕上的旅途。' }
  expect(state.writes.find(w => w.path.endsWith('/batches'))?.body.posterContent).toEqual(content)
  await editor.getByRole('textbox', { name: '短文案', exact: true }).fill('这是下一版尚未提交的文案。')
  expect(state.detail.tasks.find((task: any) => task.id === 'image-0').result.posterContent).toEqual(content)
})

test('编辑区标题和短文案都填写后才可生成海报', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/marketing/studio?campaign=c1')
  const editor = page.getByRole('region', { name: '编辑发布内容', exact: true })
  const generate = editor.getByRole('button', { name: '生成海报（1 张）' })
  await expect(generate).toBeDisabled()
  await editor.getByRole('textbox', { name: '营销标题', exact: true }).fill('一起去看电影')
  await expect(generate).toBeDisabled()
  await editor.getByRole('textbox', { name: '短文案', exact: true }).fill('  ')
  await expect(generate).toBeDisabled()
  await editor.getByRole('textbox', { name: '短文案', exact: true }).fill('和朋友一起分享电影里的温暖时光。')
  await expect(generate).toBeEnabled()
  expect(state.writes.filter(w => w.path.endsWith('/batches'))).toHaveLength(0)
})

test('多影片需求选用第二部影片的文案后只为该片生成海报', async ({ page }) => {
  const state = await setup(page)
  state.detail.movieSnapshot = [{ id: 40, title: '好东西' }, { id: 39, title: '海洋奇缘2' }]
  state.detail.tasks = [{ id: 'moana-copy', batchId: 'copy-batch', movieId: 39, kind: 'COPY', status: 'SUCCEEDED', attempts: 1, providerCalls: 1,
    result: { titles: ['向着未知海域出发', '与海风同行', '一起寻找新的航线'], shortCopy: '与朋友一起穿越星光与海浪，踏上新的远航。' } }]
  await page.goto('/marketing/studio?campaign=c1')
  const editor = page.getByRole('region', { name: '编辑发布内容', exact: true })
  await expect(editor.getByText('请选择本次要宣传的影片', { exact: true })).toBeVisible()
  await expect(editor.getByRole('button', { name: '生成海报（1 张）' })).toBeDisabled()
  await page.getByRole('button', { name: /向着未知海域出发.*选用/ }).click()
  await expect(editor.getByText('将为《海洋奇缘2》根据当前营销标题创作宣传海报。')).toBeVisible()
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await editor.getByRole('button', { name: '生成海报（1 张）' }).click()
  await expect(page.getByText(/将为《海洋奇缘2》根据营销标题「向着未知海域出发」/)).toBeVisible()
  await page.getByRole('button', { name: '开始生成', exact: true }).click()
  await expect.poll(() => state.writes.filter(w => w.path.endsWith('/batches')).length).toBe(1)
  expect(state.writes.find(w => w.path.endsWith('/batches'))?.body.posterContent).toEqual({ movieId: 39, title: '向着未知海域出发', copy: '与朋友一起穿越星光与海浪，踏上新的远航。' })
  expect(state.detail.tasks.find((task: any) => task.kind === 'IMAGE').movieId).toBe(39)
})

test('切换关联影片会清除上一部影片的文案和选中海报', async ({ page }) => {
  const state = await setup(page)
  state.detail.movieSnapshot.push({ id: 2, title: '另一部电影' })
  await page.goto('/marketing/studio?campaign=c1')
  await page.getByRole('button', { name: /周末，一起看好故事.*选用/ }).click()
  await page.locator('.asset-choice').first().click()
  const editor = page.getByRole('region', { name: '编辑发布内容', exact: true })
  await editor.locator('.el-select').first().click()
  await page.getByRole('option', { name: '另一部电影', exact: true }).click()
  await expect(editor.getByRole('textbox', { name: '营销标题', exact: true })).toHaveValue('')
  await expect(editor.getByRole('textbox', { name: '短文案', exact: true })).toHaveValue('')
  await expect(editor.getByRole('button', { name: '生成海报（1 张）' })).toBeDisabled()
  await expect(editor.getByRole('button', { name: '保存内容版本' })).toBeDisabled()
})

test('后端仍是旧生图流程时阻止发送生成请求', async ({ page }) => {
  const state = await setup(page)
  state.capabilities.posterContentRequired = false
  await page.goto('/marketing/studio?campaign=c1')
  await page.getByRole('button', { name: /周末，一起看好故事.*选用/ }).click()
  await expect(page.getByText('海报生成服务尚未更新，请重启后端服务后再根据文案生成海报。')).toBeVisible()
  await expect(page.getByRole('button', { name: '生成海报（1 张）' })).toBeDisabled()
  expect(state.writes.filter(w => w.path.endsWith('/batches'))).toHaveLength(0)
})
