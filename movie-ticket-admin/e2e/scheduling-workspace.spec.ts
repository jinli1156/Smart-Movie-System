import { expect, test, type Page } from '@playwright/test'

async function setup(page: Page, disabledHall = false) {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'workspace-e2e')
    localStorage.setItem('userInfo', JSON.stringify({ id: 1, username: 'admin', role: 'ADMIN' }))
  })
  const state = { stale: false, unavailable: false, applied: 0, checkCalls: 0, saveCalls: 0, saveFails: false, readFails: false, latest: null as any, optimized: null as any, baseline: [] as any[], saved: [] as any[] }
  const films = [{ id: 1, title: '远山来信', duration: 120 }, { id: 2, title: '夏日列车', duration: 105 }, { id: 3, title: '星际漫游', duration: 120 }]
  const halls = [{ id: 1, name: '一号激光厅', status: 1 }, { id: 2, name: '二号全景声厅', status: disabledHall ? 0 : 1 }]
  const row = (date: string, movieId: number, hallId: number, hour: string, sourceShowtimeId?: number, locked = false) => {
    const startTime = `${date}T${hour}:00`
    const end = new Date(new Date(startTime).getTime() + films[movieId - 1].duration * 60000)
    const endTime = `${end.getFullYear()}-${String(end.getMonth() + 1).padStart(2, '0')}-${String(end.getDate()).padStart(2, '0')}T${String(end.getHours()).padStart(2, '0')}:${String(end.getMinutes()).padStart(2, '0')}:00`
    return { sourceShowtimeId, movieId, movieTitle: films[movieId - 1].title, hallId, hallName: halls[hallId - 1].name, startTime, endTime, basePrice: 45, unitPrice: 45, locked, lockReason: locked ? '已售或待付款' : '', predictedRate: 55, confidence: '中' }
  }
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const url = new URL(route.request().url()), path = url.pathname
    let data: any = []
    if (path === '/api/cinema/all') data = [{ id: 1, name: '向知影城 · 南宁万象店' }, { id: 2, name: '向知影城 · 青秀店' }]
    if (path === '/api/movie/now-showing') data = films
    if (path === '/api/movie/page') data = { records: films, total: films.length }
    if (path.endsWith('/marketing/capabilities')) data = { imageConfigured: true, textConfigured: true, posterContentRequired: true }
    if (path.endsWith('/scheduling/saved-drafts')) {
      if (route.request().method() === 'PUT') {
        state.saveCalls++
        if (state.saveFails) return route.fulfill({ status: 503, json: { code: 503, message: '暂存服务暂不可用' } })
        const body = route.request().postDataJSON()
        data = { summary: { id: `saved-${state.saveCalls}`, cinemaId: body.cinemaId, cinemaName: '向知影城', targetDate: body.targetDate, savedAt: `${body.targetDate}T09:00:00` }, content: body.content }
        state.saved = [data, ...state.saved.filter(s => s.summary.cinemaId !== body.cinemaId || s.summary.targetDate !== body.targetDate)]
      } else data = state.saved.map(s => s.summary)
    }
    if (path.includes('/scheduling/saved-drafts/')) {
      const id = path.split('/').pop()
      if (route.request().method() === 'DELETE') state.saved = state.saved.filter(s => s.summary.id !== id)
      else {
        if (state.readFails) return route.fulfill({ status: 503, json: { code: 503, message: '草案读取暂不可用' } })
        data = state.saved.find(s => s.summary.id === id)
      }
    }
    if (path.endsWith('/scheduling/workspace')) {
      const date = url.searchParams.get('targetDate')!
      if (!state.baseline.length) state.baseline = [row(date, 1, 1, '10:00', 11, true), row(date, 2, 2, '14:00', 12)]
      data = { version: `v${state.applied}`, minimumCleaningMinutes: 15, halls, items: state.baseline }
    }
    if (path.endsWith('/scheduling/optimize')) {
      const body = route.request().postDataJSON(); state.optimized = body
      if (state.unavailable) data = { status: 'INFEASIBLE', reason: '当前场次数与影片上限无法同时满足，请降低每厅场次。' }
      else data = { status: 'OPTIMAL', statusText: '已找到全局最优解', solver: 'CP-SAT', selectedShowtimes: [state.baseline[0], row(body.targetDate, 2, 1, '12:30'), row(body.targetDate, 3, 1, '18:00'), row(body.targetDate, 1, 2, '13:00'), row(body.targetDate, 2, 2, '18:30'), row(body.targetDate, 3, 2, '23:30')], comparisons: [], constraints: ['已售场次保留'] }
    }
    if (path.endsWith('/scheduling/check')) {
      state.checkCalls++; state.latest = route.request().postDataJSON()
      const metrics = (items: any[]) => ({ showtimeCount: items.length, distinctMovies: new Set(items.map(s => s.movieId)).size, expectedRevenue: items.reduce((n, s) => n + s.basePrice * 50, 0), expectedAudience: items.length * 50, occupancyRate: 50, historicalSamples: 0, items: items.map(s => ({ ...s, predictedRate: 50, confidence: '低' })) })
      data = { valid: true, cleaningMinutes: 20, changes: [], original: metrics(state.baseline), draft: metrics(state.latest.items) }
    }
    if (path.endsWith('/scheduling/apply')) {
      if (state.stale) return route.fulfill({ status: 409, json: { code: 409, message: '排片或售票状态已变化，请刷新排片后重新生成草案' } })
      state.applied++; const body = route.request().postDataJSON()
      state.baseline = body.items.map((s: any, index: number) => ({ ...s, sourceShowtimeId: s.sourceShowtimeId || 100 + index, movieTitle: films[s.movieId - 1].title, hallName: halls[s.hallId - 1].name, locked: s.sourceShowtimeId === 11 }))
      data = { created: 5, removed: 1, message: '排片已应用' }
    }
    return route.fulfill({ json: { code: 200, data } })
  })
  await page.goto('/smart-scheduling')
  await expect(page.getByRole('button', { name: '生成排片草案', exact: true })).toBeEnabled()
  return state
}

test('从策略生成草案、对照原排片，微调后重算并确认发布', async ({ page }) => {
  const state = await setup(page)
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message))
  await page.getByRole('button', { name: /收益优先/ }).click()
  await page.getByRole('button', { name: '生成排片草案', exact: true }).click()
  await expect(page.getByText('草案检查通过', { exact: true })).toBeVisible()
  expect(state.optimized.revenueWeight).toBe(75)
  await expect(page.locator('.showtime-block')).toHaveCount(6)
  await expect(page.getByText('暂无历史样本，预测为冷启动估计', { exact: false })).toBeVisible()
  await page.getByText('原排片', { exact: true }).click()
  await expect(page.locator('.showtime-block')).toHaveCount(2)
  await page.getByText('排片草案', { exact: true }).click()
  await page.getByRole('button', { name: '夏日列车 12:30 调整场次' }).click()
  const editor = page.getByRole('dialog', { name: '调整放映场次' })
  await editor.getByRole('spinbutton', { name: '基础票价' }).fill('52')
  await editor.getByRole('button', { name: '保存到草案' }).click()
  await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeDisabled()
  await expect(page.locator('.comparison-grid')).toHaveCount(0)
  await page.getByRole('button', { name: '检查并比较' }).click()
  await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeEnabled()
  expect(state.latest.items.find((s: any) => s.movieId === 2 && s.hallId === 1).basePrice).toBe(52)
  await page.getByRole('button', { name: '确认并应用排片' }).click()
  expect(state.applied).toBe(0)
  await page.getByRole('button', { name: '应用到影院', exact: true }).click()
  await expect(page.getByText('正在查看已发布排片')).toBeVisible()
  expect(state.applied).toBe(1)
  await expect(page.locator('.showtime-block')).toHaveCount(6)
  expect(errors).toEqual([])
})

test('锁定场次不可编辑，时间重叠阻止提交，移除冲突后恢复', async ({ page }) => {
  await setup(page)
  await page.getByRole('button', { name: '远山来信 10:00 已锁定' }).click()
  const locked = page.getByRole('dialog', { name: '场次详情 · 已锁定' })
  await expect(locked.getByRole('button', { name: '保存到草案' })).toHaveCount(0)
  await expect(locked.getByRole('spinbutton')).toBeDisabled()
  await locked.getByRole('button', { name: '关闭', exact: true }).click()
  await page.getByRole('button', { name: '添加场次' }).click()
  await page.getByRole('button', { name: '保存到草案' }).click()
  await expect(page.getByText('1 处需要调整')).toBeVisible()
  await expect(page.getByRole('button', { name: '检查并比较' })).toBeDisabled()
  await page.getByRole('button', { name: '远山来信 10:00 调整场次', exact: true }).click()
  await page.getByRole('button', { name: '移除场次' }).click()
  await expect(page.getByText('1 处需要调整')).toHaveCount(0)
})

test('提交遇到新订单时保留草案并撤销检查通过状态', async ({ page }) => {
  const state = await setup(page); state.stale = true
  await page.getByRole('button', { name: '生成排片草案', exact: true }).click()
  await page.getByRole('button', { name: '确认并应用排片' }).click()
  await page.getByRole('button', { name: '应用到影院', exact: true }).click()
  await expect(page.locator('.workspace-alert')).toContainText('售票状态已变化')
  await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeDisabled()
  await expect(page.locator('.showtime-block')).toHaveCount(6)
  expect(state.applied).toBe(0)
})

test('无可行解显示原因，离开草案时可选择保留', async ({ page }) => {
  const state = await setup(page); state.unavailable = true
  await page.getByRole('button', { name: '生成排片草案', exact: true }).click()
  await expect(page.locator('.workspace-alert')).toContainText('降低每厅场次')
  await expect(page.locator('.showtime-block')).toHaveCount(2)
  state.unavailable = false
  await page.getByRole('button', { name: '生成排片草案', exact: true }).click()
  await expect(page.getByText('草案检查通过', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '刷新排片' }).click()
  await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(page.locator('.showtime-block')).toHaveCount(6)
})

test('桌面与窄屏时间轴可浏览，跨日场次标记清晰', async ({ page }) => {
  await page.setViewportSize({ width: 1480, height: 1100 })
  await setup(page)
  await page.getByRole('button', { name: '生成排片草案', exact: true }).click()
  await expect(page.getByText('草案检查通过', { exact: true })).toBeVisible()
  await expect(page.locator('.showtime-block').filter({ hasText: '23:30–次日 01:30' })).toHaveCount(1)
  await page.screenshot({ path: '../.local/scheduling-workspace-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('button', { name: '重新生成草案' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: '../.local/scheduling-workspace-mobile.png', fullPage: true })
})

test.describe('草案恢复与离开保护', () => {
  async function generated(page: Page) {
    const state = await setup(page)
    await page.getByRole('button', { name: '生成排片草案', exact: true }).click()
    await expect(page.getByText('草案检查通过', { exact: true })).toBeVisible()
    return state
  }
  async function save(page: Page) {
    await page.getByRole('button', { name: '暂存草案', exact: true }).click()
    await expect(page.locator('.draft-save-status')).toContainText('已暂存，可安全离开')
  }
  async function editPrice(page: Page, value: string) {
    await page.getByRole('button', { name: '夏日列车 12:30 调整场次' }).click()
    const editor = page.getByRole('dialog', { name: '调整放映场次' })
    await editor.getByRole('spinbutton', { name: '基础票价' }).fill(value)
    await editor.getByRole('button', { name: '保存到草案' }).click()
  }
  const marketing = (page: Page) => page.getByRole('menuitem', { name: '营销工作台', exact: true })
  const scheduling = (page: Page) => page.getByRole('menuitem', { name: '智能排片工作台', exact: true })

  test('暂存后刷新恢复场次与票价，切换营销再返回也恢复且不重复弹窗', async ({ page }) => {
    const state = await generated(page)
    await editPrice(page, '52')
    await save(page)
    const unloadBlocked = await page.evaluate(() => {
      const event = new Event('beforeunload', { cancelable: true })
      window.dispatchEvent(event)
      return event.defaultPrevented
    })
    expect(unloadBlocked).toBe(false)
    await page.reload()
    await expect(page.locator('.showtime-block')).toHaveCount(6)
    await expect(page.getByRole('button', { name: '夏日列车 12:30 调整场次' })).toContainText('¥52')
    await expect(page.locator('.draft-save-status')).toContainText('已暂存，可安全离开')
    await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeDisabled()
    await marketing(page).click()
    await expect(page).toHaveURL(/\/marketing\/studio$/)
    await scheduling(page).click()
    await expect(page.locator('.showtime-block')).toHaveCount(6)
    await expect(page.getByRole('button', { name: '夏日列车 12:30 调整场次' })).toContainText('¥52')
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(state.saveCalls).toBe(1)
    expect(state.applied).toBe(0)
  })

  test('继续编辑取消导航，暂存失败不离开，重试暂存成功后跳转并可恢复', async ({ page }) => {
    const state = await generated(page)
    await marketing(page).click()
    let dialog = page.getByRole('dialog', { name: '当前草案有未暂存的修改' })
    await dialog.getByRole('button', { name: '继续编辑', exact: true }).click()
    await expect(page).toHaveURL(/\/smart-scheduling$/)
    await expect(page.locator('.showtime-block')).toHaveCount(6)
    state.saveFails = true
    await marketing(page).click()
    dialog = page.getByRole('dialog', { name: '当前草案有未暂存的修改' })
    await dialog.getByRole('button', { name: '暂存并离开', exact: true }).click()
    await expect(page.getByText('暂存失败：暂存服务暂不可用', { exact: true })).toBeVisible()
    await expect(page).toHaveURL(/\/smart-scheduling$/)
    await expect(dialog).toBeVisible()
    expect(state.saved).toHaveLength(0)
    state.saveFails = false
    await dialog.getByRole('button', { name: '暂存并离开', exact: true }).click()
    await expect(page).toHaveURL(/\/marketing\/studio$/)
    await scheduling(page).click()
    await expect(page.locator('.showtime-block')).toHaveCount(6)
    await expect(page.locator('.draft-save-status')).toContainText('已暂存，可安全离开')
    expect(state.saved).toHaveLength(1)
  })

  test('放弃未暂存修改后离开，之前暂存的版本仍可恢复', async ({ page }) => {
    const state = await generated(page)
    await save(page)
    await editPrice(page, '62')
    await expect(page.locator('.draft-save-status')).toContainText('有未暂存修改')
    await marketing(page).click()
    await page.getByRole('button', { name: '放弃修改并离开', exact: true }).click()
    await expect(page).toHaveURL(/\/marketing\/studio$/)
    await scheduling(page).click()
    await expect(page.locator('.draft-save-status')).toContainText('已暂存，可安全离开')
    await expect(page.getByRole('button', { name: '夏日列车 12:30 调整场次' })).toContainText('¥45')
    expect(state.saveCalls).toBe(1)
  })

  test('没有暂存时可直接放弃并离开，回来显示正式排片', async ({ page }) => {
    const state = await generated(page)
    await marketing(page).click()
    await page.getByRole('button', { name: '放弃修改并离开', exact: true }).click()
    await expect(page).toHaveURL(/\/marketing\/studio$/)
    await scheduling(page).click()
    await expect(page.locator('.showtime-block')).toHaveCount(2)
    await expect(page.getByText('正在查看已发布排片')).toBeVisible()
    expect(state.saveCalls).toBe(0)
  })

  test('售票快照变化后仍恢复暂存内容，并提示重新生成', async ({ page }) => {
    const state = await generated(page)
    await save(page)
    state.applied++
    await page.reload()
    await expect(page.locator('.showtime-block')).toHaveCount(6)
    await expect(page.getByText('暂存后排片或售票状态已变化。', { exact: false })).toBeVisible()
    await expect(page.getByRole('button', { name: '检查并比较' })).toBeDisabled()
    expect(state.saved).toHaveLength(1)
  })

  test('恢复失败明确提示，重试可取回数据库中的草案', async ({ page }) => {
    const state = await generated(page)
    await save(page)
    state.readFails = true
    await page.reload()
    await expect(page.getByText('暂存草案读取失败：草案读取暂不可用。可在暂存草案列表中重试。', { exact: true })).toBeVisible()
    await expect(page.locator('.showtime-block')).toHaveCount(2)
    state.readFails = false
    await page.getByRole('button', { name: /暂存草案列表/ }).click()
    await page.getByRole('button', { name: '恢复草案', exact: true }).click()
    await expect(page.locator('.showtime-block')).toHaveCount(6)
    await expect(page.locator('.draft-save-status')).toContainText('已暂存，可安全离开')
    expect(state.saveCalls).toBe(1)
  })

  test('切换影院清除旧暂存标记，切回时恢复对应草案', async ({ page }) => {
    await generated(page)
    await save(page)
    await page.locator('.scope-bar .el-select__wrapper').click()
    await page.getByRole('option', { name: '向知影城 · 青秀店', exact: true }).click()
    await expect(page.getByText('正在查看已发布排片')).toBeVisible()
    await expect(page.locator('.draft-save-status')).toHaveCount(0)
    await page.locator('.scope-bar .el-select__wrapper').click()
    await page.getByRole('option', { name: '向知影城 · 南宁万象店', exact: true }).click()
    await expect(page.locator('.showtime-block')).toHaveCount(6)
    await expect(page.locator('.draft-save-status')).toContainText('已暂存，可安全离开')
  })

  test('应用暂存草案后清理该草案，刷新保留正式排片', async ({ page }) => {
    const state = await generated(page)
    await save(page)
    await page.getByRole('button', { name: '确认并应用排片' }).click()
    await page.getByRole('button', { name: '应用到影院', exact: true }).click()
    await expect(page.getByText('正在查看已发布排片')).toBeVisible()
    expect(state.saved).toHaveLength(0)
    await page.reload()
    await expect(page.getByText('正在查看已发布排片')).toBeVisible()
    await expect(page.locator('.showtime-block')).toHaveCount(6)
    expect(state.applied).toBe(1)
  })
})

test.describe('时间轴拖动调整', () => {
  test.use({ viewport: { width: 1680, height: 1100 } })

  async function generate(page: Page, disabledHall = false) {
    const state = await setup(page, disabledHall)
    await page.getByRole('button', { name: '生成排片草案', exact: true }).click()
    await expect(page.getByText('草案检查通过', { exact: true })).toBeVisible()
    return state
  }

  async function dragTo(page: Page, name: string, deltaX: number, hallId?: number) {
    const block = page.getByRole('button', { name, exact: true })
    await block.click({ trial: true })
    const box = (await block.boundingBox())!
    const target = hallId ? (await page.locator(`.lane-track[data-hall-id="${hallId}"]`).boundingBox())! : box
    const x = box.x + 20, y = box.y + 36
    await page.mouse.move(x, y)
    await page.mouse.down()
    await page.mouse.move(x + deltaX, hallId ? target.y + 48 : y, { steps: 5 })
  }

  test('按五分钟吸附并保留片长票价，重查后使用拖动结果应用', async ({ page }) => {
    const state = await generate(page)
    await dragTo(page, '夏日列车 12:30 调整场次', 37)
    await expect(page.locator('.drag-preview')).toContainText('13:05–14:50')
    await page.mouse.up()
    await expect(page.getByRole('button', { name: '夏日列车 13:05 调整场次' })).toBeVisible()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeDisabled()
    expect(state.checkCalls).toBe(1)
    expect(state.applied).toBe(0)
    await page.getByRole('button', { name: '检查并比较' }).click()
    await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeEnabled()
    const row = state.latest.items.find((s: any) => s.movieId === 2 && s.hallId === 1)
    expect(row.startTime.slice(11)).toBe('13:05:00')
    expect(row.endTime.slice(11)).toBe('14:50:00')
    expect(row.basePrice).toBe(45)
    expect(state.latest.items.find((s: any) => s.sourceShowtimeId === 11)).toMatchObject({ startTime: state.baseline[0].startTime, endTime: state.baseline[0].endTime })
    await page.getByRole('button', { name: '确认并应用排片' }).click()
    await page.getByRole('button', { name: '应用到影院', exact: true }).click()
    await expect(page.getByText('正在查看已发布排片')).toBeVisible()
    expect(state.baseline.find(s => s.movieId === 2 && s.hallId === 1).startTime).toBe(row.startTime)
  })

  test('跨影厅拖动同时调整时间，检查请求使用目标影厅', async ({ page }) => {
    const state = await generate(page)
    await dragTo(page, '夏日列车 12:30 调整场次', 210, 2)
    await expect(page.locator('.drop-target .hall-label')).toContainText('二号全景声厅')
    await expect(page.locator('.timeline-feedback')).toContainText('16:00–17:45')
    await page.screenshot({ path: '../.local/scheduling-timeline-drag-preview.png', fullPage: true })
    await page.mouse.up()
    await expect(page.locator('[data-hall-id="2"] .showtime-block').filter({ hasText: '16:00–17:45' })).toHaveCount(1)
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await page.getByRole('button', { name: '检查并比较' }).click()
    await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeEnabled()
    expect(state.latest.items.find((s: any) => s.movieId === 2 && s.startTime.slice(11, 16) === '16:00').hallId).toBe(2)
    await page.getByRole('button', { name: '夏日列车 16:00 调整场次' }).focus()
    await page.keyboard.press('ArrowLeft')
    await page.keyboard.press('ArrowUp')
    await expect(page.locator('[data-hall-id="1"] .showtime-block').filter({ hasText: '15:55–17:40' })).toHaveCount(1)
    await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeDisabled()
  })

  test('重叠与清场不足实时标红，松开后回到原安排并保留检查结果', async ({ page }) => {
    const state = await generate(page)
    for (const delta of [-60, -15]) {
      await dragTo(page, '夏日列车 12:30 调整场次', delta)
      await expect(page.locator('.drag-preview')).toHaveClass(/invalid/)
      await expect(page.locator('.showtime-block.conflict')).toHaveCount(1)
      await expect(page.locator('.timeline-feedback')).toContainText('需预留 20 分钟')
      if (delta === -15) await page.screenshot({ path: '../.local/scheduling-timeline-drag-conflict.png', fullPage: true })
      await page.mouse.up()
      await expect(page.getByRole('button', { name: '夏日列车 12:30 调整场次' })).toBeVisible()
      await expect(page.locator('.timeline-feedback')).toContainText('已保留原安排')
      await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeEnabled()
    }
    expect(state.checkCalls).toBe(1)
    await dragTo(page, '夏日列车 12:30 调整场次', -10)
    await expect(page.locator('.drag-preview')).not.toHaveClass(/invalid/)
    await page.mouse.up()
    await expect(page.getByRole('button', { name: '夏日列车 12:20 调整场次' })).toHaveCount(1)
    await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeDisabled()
  })

  test('Esc、指针取消与拖出影厅均保留原草案', async ({ page }) => {
    await generate(page)
    await dragTo(page, '夏日列车 12:30 调整场次', 60)
    await page.keyboard.press('Escape')
    await page.mouse.up()
    await expect(page.locator('.drag-preview')).toHaveCount(0)
    await expect(page.locator('.timeline-feedback')).toContainText('已取消拖动')
    await dragTo(page, '夏日列车 12:30 调整场次', 60)
    await page.getByRole('button', { name: '夏日列车 12:30 调整场次' }).dispatchEvent('pointercancel')
    await page.mouse.up()
    await expect(page.locator('.drag-preview')).toHaveCount(0)
    await dragTo(page, '夏日列车 12:30 调整场次', 60)
    await page.mouse.move(20, 20)
    await expect(page.locator('.timeline-feedback')).toContainText('请在目标影厅的时间轴内松开')
    await page.mouse.up()
    await expect(page.getByRole('button', { name: '夏日列车 12:30 调整场次' })).toHaveCount(1)
    await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeEnabled()
    await expect(page.getByRole('dialog')).toHaveCount(0)
  })

  test('锁定场次和原排片禁止拖动，停用影厅禁止放置', async ({ page }) => {
    const state = await generate(page, true)
    await dragTo(page, '远山来信 10:00 已锁定', 45)
    await expect(page.locator('.drag-preview')).toHaveCount(0)
    await page.mouse.up()
    await expect(page.getByRole('button', { name: '远山来信 10:00 已锁定' })).toHaveCount(1)
    await page.getByRole('dialog', { name: '场次详情 · 已锁定' }).getByRole('button', { name: '关闭', exact: true }).click()
    await dragTo(page, '夏日列车 12:30 调整场次', 210, 2)
    await expect(page.locator('.timeline-feedback')).toContainText('该影厅已停用')
    await page.mouse.up()
    await page.getByText('原排片', { exact: true }).click()
    await dragTo(page, '夏日列车 14:00 调整场次', 40)
    await expect(page.locator('.drag-preview')).toHaveCount(0)
    await page.mouse.up()
    await page.getByText('排片草案', { exact: true }).click()
    await expect(page.getByRole('button', { name: '夏日列车 12:30 调整场次' })).toHaveCount(1)
    await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeEnabled()
    expect(state.checkCalls).toBe(1)
  })

  test('拖动边缘自动横向滚动，次日散场允许、次日开场拒绝', async ({ page }) => {
    const state = await generate(page)
    const block = page.getByRole('button', { name: '星际漫游 23:30 调整场次' })
    await block.scrollIntoViewIfNeeded()
    const initialScroll = await page.locator('.timeline-scroll').evaluate(el => el.scrollLeft)
    const box = (await block.boundingBox())!, scrollBox = (await page.locator('.timeline-scroll').boundingBox())!
    await page.mouse.move(box.x + 10, box.y + 35)
    await page.mouse.down()
    await page.mouse.move(scrollBox.x + scrollBox.width - 5, box.y + 35, { steps: 5 })
    await expect.poll(() => page.locator('.timeline-scroll').evaluate(el => el.scrollLeft)).toBeGreaterThan(initialScroll)
    await page.keyboard.press('Escape')
    await page.mouse.up()
    await block.focus()
    await page.keyboard.press('ArrowRight')
    await expect(page.getByRole('button', { name: '星际漫游 23:35 调整场次' })).toContainText('次日 01:35')
    const later = page.getByRole('button', { name: '星际漫游 23:35 调整场次' })
    await later.focus()
    for (let i = 0; i < 5; i++) await page.keyboard.press('ArrowRight')
    await expect(page.getByRole('button', { name: '星际漫游 23:55 调整场次' })).toHaveCount(1)
    await expect(page.locator('.timeline-feedback')).toContainText('开场时间须在所选日期内')
    await page.getByRole('button', { name: '检查并比较' }).click()
    await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeEnabled()
    expect(state.latest.items.find((s: any) => s.movieId === 3 && s.hallId === 2).endTime.slice(11, 16)).toBe('01:55')
  })

  test('拖动已发布场次建立草案，可暂存并保留原场次来源', async ({ page }) => {
    const state = await setup(page)
    let saved: any
    await page.route('**/api/statistics/scheduling/saved-drafts', async route => {
      saved = route.request().postDataJSON()
      await route.fulfill({ json: { code: 200, data: { summary: { id: 'drag-saved', cinemaId: 1, targetDate: saved.targetDate, savedAt: `${saved.targetDate}T09:00:00` }, content: saved.content } } })
    })
    await dragTo(page, '夏日列车 14:00 调整场次', 25)
    await page.mouse.up()
    await expect(page.getByText('草案尚未应用')).toBeVisible()
    await expect(page.getByRole('button', { name: '夏日列车 14:25 调整场次' })).toBeVisible()
    await page.getByRole('button', { name: '暂存草案', exact: true }).click()
    await expect(page.getByText('已暂存，可安全离开', { exact: false })).toBeVisible()
    expect(saved.content.draft.find((s: any) => s.sourceShowtimeId === 12)).toMatchObject({ hallId: 2, startTime: `${saved.targetDate}T14:25:00`, basePrice: 45 })
    expect(state.baseline[1].startTime.slice(11, 16)).toBe('14:00')
    expect(state.applied).toBe(0)
  })

  test('窄屏触摸拖动可调整场次，轻触仍打开编辑器', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    const state = await generate(page)
    const block = page.getByRole('button', { name: '夏日列车 12:30 调整场次' })
    await block.scrollIntoViewIfNeeded()
    const box = (await block.boundingBox())!
    const session = await page.context().newCDPSession(page)
    const x = box.x + 20, y = box.y + 35
    await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x, y }] })
    await session.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: x + 30, y }] })
    await expect(page.locator('.drag-preview')).toContainText('13:00–14:45')
    await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
    await expect(page.getByRole('button', { name: '夏日列车 13:00 调整场次' })).toHaveCount(1)
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await page.getByRole('button', { name: '检查并比较' }).click()
    await expect(page.getByRole('button', { name: '确认并应用排片' })).toBeEnabled()
    expect(state.latest.items.find((s: any) => s.movieId === 2 && s.hallId === 1).startTime.slice(11, 16)).toBe('13:00')
    await page.getByRole('button', { name: '夏日列车 13:00 调整场次' }).click()
    await expect(page.getByRole('dialog', { name: '调整放映场次' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
})
