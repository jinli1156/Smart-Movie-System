import { expect, test, type Page, type Route } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const movieSchema = [
  { key: 'title', label: '电影名称', type: 'text', required: true }, { key: 'director', label: '导演', type: 'text', required: false },
  { key: 'genre', label: '类型', type: 'text', required: true }, { key: 'duration', label: '时长（分钟）', type: 'integer', required: true },
  { key: 'description', label: '剧情简介', type: 'textarea', required: true }, { key: 'status', label: '发布状态', type: 'status', required: true }
]
const hallSchema = [
  { key: 'name', label: '影厅名称', type: 'text', required: true }, { key: 'totalRows', label: '座位行数', type: 'integer', required: true },
  { key: 'totalCols', label: '座位列数', type: 'integer', required: true }, { key: 'seatLayout', label: '座位布局（JSON）', type: 'textarea', required: true },
  { key: 'status', label: '发布状态', type: 'status', required: true }
]
const ok = (route: Route, data: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
async function setup(page: Page, role = 'ADMIN', kind = 'MOVIE', batchSize = 1) {
  page.on('pageerror', error => console.error('Listing page error:', error.message))
  page.on('console', message => { if (message.type() === 'error') console.error('Listing console:', message.text()) })
  await page.addInitScript(({ role }) => {
    localStorage.setItem('token', 'listing-e2e')
    localStorage.setItem('userInfo', JSON.stringify({ id: 99, username: 'admin', nickname: '管理员', role, cinemaId: role === 'CINEMA_ADMIN' ? 10 : undefined }))
  }, { role })
  const fields = kind === 'MOVIE' ? { title: '银幕旅途', director: '林导演', genre: '剧情', duration: 120, description: '发行方提供的真实剧情。', status: 2 } : { name: '小厅', totalRows: 2, totalCols: 4, seatLayout: '', status: 1 }
  const detail: any = { id: 'd1', kind, cinemaId: kind === 'HALL' ? 10 : null, ownerId: 99, status: 'DRAFT', fields, extracted: structuredClone(fields), sourceNames: ['发行资料.xlsx'], sourceImages: [], sourceText: '片名：银幕旅途\n时长：120分钟', extractionMode: 'AI_TEXT', extractionWarning: '', version: 1, reviewNote: '', issues: kind === 'HALL' ? ['请补充座位布局（JSON）'] : [], history: [] }
  detail.batchId = 'b1'
  const details = [detail, ...Array.from({ length: batchSize - 1 }, (_, i) => ({ ...structuredClone(detail), id: `d${i + 2}`, fields: { ...fields, title: `银幕旅途${i + 2}` } }))]
  const summary = (item: any) => ({ id: item.id, title: item.fields.title || item.fields.name, kind, status: item.status, extractionMode: item.extractionMode, updatedAt: '2026-10-31T00:00:00' })
  const writes: Array<{ path: string; method: string; body: any }> = []
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const req = route.request(), url = new URL(req.url()), endpoint = url.pathname
    if (endpoint.startsWith('/api/listing/templates/')) {
      const format = url.searchParams.get('format') || 'xlsx', templateKind = endpoint.split('/').at(-1)!.toLowerCase()
      const buffer = await readFile(path.resolve('../movie-ticket-backend/src/main/resources/listing/templates', `${templateKind}-batch-template.${format}`))
      return route.fulfill({ contentType: format.startsWith('xlsx') ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : format === 'json' ? 'application/json' : 'text/csv', body: buffer })
    }
    if (req.method() !== 'GET') {
      const body = req.headers()['content-type']?.includes('application/json') ? req.postDataJSON() : req.postDataBuffer()?.toString('latin1')
      writes.push({ path: endpoint, method: req.method(), body })
      if (endpoint === '/api/listing/import') return ok(route, { id: 'b1', total: details.length, drafts: details, extractionMode: 'AI_TEXT', warning: '' })
      const active = details.find(item => endpoint.includes(`/drafts/${item.id}`)) || detail
      if (req.method() === 'PUT') { active.fields = body.fields; active.version++; active.status = 'DRAFT'; active.issues = []; active.history.push({ action: 'SAVED', version: active.version, operatorId: 99 }) }
      if (endpoint.endsWith('/reject')) { active.status = 'REJECTED'; active.reviewNote = body.note; active.version++ }
      if (endpoint.endsWith('/publish')) { active.status = 'PUBLISHED'; active.entityId = 77; active.version++; active.history.push({ action: 'PUBLISHED', version: active.version, operatorId: 99 }) }
      return ok(route, active)
    }
    if (endpoint.endsWith('/capabilities')) return ok(route, { textConfigured: true, visionConfigured: false, schemas: role === 'ADMIN' ? { MOVIE: movieSchema, CINEMA: [], HALL: hallSchema } : { HALL: hallSchema } })
    if (endpoint === '/api/cinema/all') return ok(route, [{ id: 10, name: '东城影院' }])
    if (endpoint === '/api/listing/drafts') return ok(route, { records: details.map(summary), total: details.length })
    if (endpoint === '/api/listing/batches/b1') return ok(route, details.map(summary))
    if (endpoint.startsWith('/api/listing/drafts/')) return ok(route, details.find(item => endpoint.endsWith(item.id)))
    return ok(route, [])
  })
  return { detail, details, writes }
}

test('默认下载 Excel，菜单提供 CSV 和 JSON', async ({ page }) => {
  await setup(page); await page.goto('/listing')
  const xlsx = page.waitForEvent('download'); await page.getByRole('button', { name: '下载电影批量模板', exact: true }).click()
  expect((await xlsx).suggestedFilename()).toBe('movie-batch-template.xlsx')
  for (const format of ['csv', 'json']) {
    await page.locator('.text-label .el-dropdown__caret-button').click()
    const downloading = page.waitForEvent('download'); await page.getByRole('menuitem', { name: `${format.toUpperCase()} 批量模板`, exact: true }).click()
    expect((await downloading).suggestedFilename()).toBe(`movie-batch-template.${format}`)
  }
})

test('上传 Excel，修改暂存、审核确认后才发布', async ({ page }, testInfo) => {
  const state = await setup(page); await page.goto('/listing')
  await page.locator('input[type=file]').setInputFiles(path.resolve('../movie-ticket-backend/src/main/resources/listing/templates/movie-batch-template.xlsx'))
  await page.getByRole('button', { name: '生成待审核草稿', exact: true }).click()
  await expect(page.getByRole('region', { name: '原始资料', exact: true })).toContainText('120分钟')
  expect(state.writes[0].body).toContain('movie-batch-template.xlsx')
  expect(state.writes[0].body).toContain('name="importMode"\r\n\r\nTEMPLATE')
  expect(state.writes.filter(w => w.path.endsWith('/publish'))).toHaveLength(0)
  await expect(page.getByRole('button', { name: '审核通过并发布' })).toBeDisabled()
  await page.getByRole('textbox', { name: '电影名称', exact: true }).fill('银幕旅途（修订）')
  await expect(page.getByRole('checkbox')).toBeDisabled()
  await page.getByRole('button', { name: '暂存草稿', exact: true }).click()
  await page.getByText('我已对照原资料核对全部字段，确认可以发布', { exact: true }).click()
  await expect(page.getByRole('checkbox')).toBeChecked()
  await page.getByRole('button', { name: '审核通过并发布' }).click()
  expect(state.writes.filter(w => w.path.endsWith('/publish'))).toHaveLength(0)
  await page.getByRole('button', { name: '确认发布', exact: true }).click()
  await expect(page.getByText('已审核发布，目录编号 #77。后续修改请进入对应管理页面。')).toBeVisible()
  expect(state.writes.find(w => w.path.endsWith('/publish'))?.body).toEqual({ version: 2, acknowledged: true })
  await page.screenshot({ path: testInfo.outputPath('listing-published.png'), fullPage: true })
})

test('左上角切换 AI 导入，保留各自输入并提交无模板资料', async ({ page }, testInfo) => {
  const state = await setup(page, 'ADMIN', 'MOVIE', 2); await page.goto('/listing')
  await page.locator('#source-text').fill('{"title":"模板里的电影"}')
  await page.locator('input[type=file]').setInputFiles({ name: 'template.json', mimeType: 'application/json', buffer: Buffer.from('{"title":"模板电影"}') })
  await page.getByRole('button', { name: 'AI 导入', exact: false }).click()
  await expect(page).toHaveURL(/mode=ai/)
  await expect(page.getByRole('heading', { name: 'AI 导入上架资料' })).toBeVisible()
  await expect(page.getByRole('button', { name: '下载电影批量模板', exact: true })).toHaveCount(0)
  await expect(page.locator('#source-text')).toHaveValue('')
  const text = '【发行方资料】银幕旅途，剧情片，120分钟。\n第二部电影是周末来信，喜剧，95分钟。'
  await page.locator('#source-text').fill(text)
  await page.locator('input[type=file]').setInputFiles({ name: 'raw.txt', mimeType: 'text/plain', buffer: Buffer.from(text) })
  await page.getByRole('button', { name: '模板导入', exact: true }).click()
  await expect(page.locator('#source-text')).toHaveValue('{"title":"模板里的电影"}')
  await expect(page.locator('.el-upload-list')).toContainText('template.json')
  await page.getByRole('button', { name: 'AI 导入', exact: false }).click()
  await expect(page.locator('#source-text')).toHaveValue(text)
  await expect(page.locator('.el-upload-list')).toContainText('raw.txt')
  await page.screenshot({ path: testInfo.outputPath('listing-ai-import.png'), fullPage: true })
  await page.getByRole('button', { name: 'AI 识别并生成草稿', exact: true }).click()
  await expect(page.getByRole('heading', { name: '本次识别 2 条资料' })).toBeVisible()
  expect(state.writes[0].body).toContain('name="importMode"\r\n\r\nAI')
  expect(state.writes[0].body).toContain('raw.txt')
  expect(state.writes.filter(w => w.path.endsWith('/publish'))).toHaveLength(0)
  await page.reload()
  await expect(page.getByRole('heading', { name: '本次识别 2 条资料' })).toBeVisible()
  await page.getByRole('button', { name: '返回上架工作台' }).click()
  await expect(page.getByRole('heading', { name: 'AI 导入上架资料' })).toBeVisible()
})

test('AI 导入失败保留原文和文件，重试后才能生成草稿', async ({ page }) => {
  await setup(page); await page.goto('/listing?mode=ai')
  await page.route('**/api/listing/import', route => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 400, message: '文字模型未配置，请配置后重试' }) }))
  await page.locator('#source-text').fill('发行资料，无需固定字段和排版')
  await page.locator('input[type=file]').setInputFiles({ name: 'source.txt', mimeType: 'text/plain', buffer: Buffer.from('原始发行资料') })
  await page.getByRole('button', { name: 'AI 识别并生成草稿', exact: true }).click()
  await expect(page.locator('.notice')).toContainText('文字模型未配置')
  await expect(page.locator('#source-text')).toHaveValue('发行资料，无需固定字段和排版')
  await expect(page.locator('.el-upload-list')).toContainText('source.txt')
  await page.unroute('**/api/listing/import')
  await page.getByRole('button', { name: 'AI 识别并生成草稿', exact: true }).click()
  await expect(page.getByRole('heading', { name: '审核电影资料' })).toBeVisible()
})

test('AI 导入在窄屏可用，影院管理员只能导入所属影厅', async ({ page }, testInfo) => {
  await setup(page, 'CINEMA_ADMIN', 'HALL'); await page.setViewportSize({ width: 390, height: 1100 }); await page.goto('/listing?mode=ai')
  await expect(page.getByRole('group', { name: '上架类型' }).getByRole('button')).toHaveCount(1)
  await expect(page.getByRole('combobox', { name: '所属影院' })).toBeDisabled()
  await page.getByRole('button', { name: '填入示例', exact: true }).click()
  await expect(page.locator('#source-text')).toHaveValue(/星光厅/)
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('listing-ai-import-mobile.png'), fullPage: true })
})

test('驳回原因可见，暂存修改后才可重新审核', async ({ page }) => {
  const state = await setup(page); await page.goto('/listing?draft=d1')
  await page.getByRole('button', { name: '驳回', exact: true }).click()
  await page.getByRole('textbox', { name: '驳回原因' }).fill('请核实导演名字')
  await page.getByRole('button', { name: '确认驳回', exact: true }).click()
  await expect(page.getByText('驳回原因：请核实导演名字。修改并暂存后可重新审核。')).toBeVisible()
  await expect(page.getByRole('button', { name: '审核通过并发布' })).toBeDisabled()
  await page.getByRole('button', { name: '暂存草稿', exact: true }).click()
  expect(state.detail.status).toBe('DRAFT')
})

test('影院管理员只处理所属影院影厅，并显式生成规则座位', async ({ page }) => {
  await setup(page, 'CINEMA_ADMIN', 'HALL'); await page.goto('/listing')
  const tabs = page.getByRole('group', { name: '上架类型' })
  await expect(tabs.getByRole('button')).toHaveCount(1)
  await expect(tabs.getByRole('button', { name: '影厅' })).toBeVisible()
  await page.getByRole('button', { name: '继续审核' }).click()
  await expect(page.getByRole('button', { name: '审核通过并发布' })).toBeDisabled()
  await page.getByRole('button', { name: '生成全可用规则座位' }).click()
  await expect(page.getByRole('region', { name: '审核资料字段' }).locator('.seat-row')).toHaveCount(2)
  await page.getByRole('button', { name: '暂存草稿', exact: true }).click()
  await page.getByText('我已对照原资料核对全部字段，确认可以发布', { exact: true }).click()
  await expect(page.getByRole('checkbox')).toBeChecked()
  await expect(page.getByRole('button', { name: '审核通过并发布' })).toBeEnabled()
})

test('宽屏和窄屏录入、审核页面没有横向溢出', async ({ page }, testInfo) => {
  await setup(page)
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1100 }); await page.goto('/listing')
    await expect(page.getByRole('heading', { name: '新建上架资料' })).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`listing-upload-${width}.png`), fullPage: true })
    await page.getByRole('button', { name: '继续审核' }).click()
    await expect(page.getByRole('heading', { name: '审核电影资料' })).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`listing-review-${width}.png`), fullPage: true })
  }
})

test('一个文件生成多份草稿，可切换审核，发布一份不会发布其他资料', async ({ page }, testInfo) => {
  const state = await setup(page, 'ADMIN', 'MOVIE', 3); await page.goto('/listing')
  await page.locator('input[type=file]').setInputFiles({ name: '多部电影.json', mimeType: 'application/json', buffer: Buffer.from('[{"title":"银幕旅途"},{"title":"银幕旅途2"},{"title":"银幕旅途3"}]') })
  await page.getByRole('button', { name: '生成待审核草稿', exact: true }).click()
  const results = page.getByRole('region', { name: '本次批量识别结果' })
  await expect(results.getByRole('heading', { name: '本次识别 3 条资料' })).toBeVisible()
  await expect(results.getByRole('button')).toHaveCount(3)
  await results.getByRole('button', { name: '2 银幕旅途2 待审核' }).click()
  await expect(page.getByRole('textbox', { name: '电影名称', exact: true })).toHaveValue('银幕旅途2')
  await page.getByText('我已对照原资料核对全部字段，确认可以发布', { exact: true }).click()
  await page.getByRole('button', { name: '审核通过并发布' }).click(); await page.getByRole('button', { name: '确认发布', exact: true }).click()
  await expect(results).toContainText('已发布 1 条')
  expect(state.details.map(item => item.status)).toEqual(['DRAFT', 'PUBLISHED', 'DRAFT'])
  await page.reload(); await expect(results.getByRole('button')).toHaveCount(3); await expect(results).toContainText('已发布 1 条')
  await page.screenshot({ path: testInfo.outputPath('listing-batch-review.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 1100 })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('listing-batch-review-mobile.png'), fullPage: true })
})
