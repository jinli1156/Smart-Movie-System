import { expect, test } from '@playwright/test'

test('排片分析显示模型来源、真实样本和失败状态，模型内容作为文本渲染', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'scheduling-e2e')
    localStorage.setItem('userInfo', JSON.stringify({ id: 1, username: 'admin', role: 'ADMIN' }))
  })
  let failed = false
  await page.route(url => url.pathname.startsWith('/api/'), route => {
    const path = new URL(route.request().url()).pathname
    const analysis = {
      source: failed ? 'algorithm' : 'model', status: failed ? 'unavailable' : 'success', model: 'test-model',
      generatedAt: '2026-09-08T12:00:00', summary: failed ? '大模型调用失败，可重新计算重试。' : '基于真实订单分析 <script>window.injected=true</script>',
      actions: [{ evidenceId: 'E1', movieTitle: '测试影片', advice: '根据历史样本核对供需。' }], risks: ['预测不等于实际收入'],
      dataBasis: { historicalSamples: 0, coldStart: true, occupancyBasis: '数据库已支付订单', predictionBasis: '无样本时使用先验' }
    }
    let data: unknown = []
    if (path === '/api/cinema/all') data = [{ id: 1, name: '测试影院' }]
    if (path === '/api/movie/now-showing') data = [{ id: 1, title: '测试影片' }]
    if (path === '/api/hall/cinema/1') data = [{ id: 1, name: '一号厅' }]
    if (path === '/api/statistics/scheduling/workspace') data = { version: 'test', items: [], halls: [], minimumCleaningMinutes: 15 }
    if (path === '/api/statistics/scheduling-insights') data = {
      summary: { historicalSamples: 0, predictedAverageRate: null }, aiAnalysis: analysis, halls: [],
      frequencyRecommendations: [{ movieId: 1, movieTitle: '测试影片', historicalRate: null, historicalSamples: 0, action: 'KEEP' }]
    }
    return route.fulfill({ json: { code: 200, data } })
  })
  await page.goto('/smart-scheduling')
  await page.getByRole('button', { name: '经营分析', exact: true }).click()
  const panel = page.getByRole('region', { name: '大模型排片分析' })
  await expect(panel).toContainText('test-model')
  await expect(panel).toContainText('暂无历史样本，预测为冷启动估计')
  await expect(panel).toContainText('<script>window.injected=true</script>')
  expect(await page.evaluate(() => (window as any).injected)).toBeUndefined()
  await expect(page.locator('.metric.orange strong')).toHaveText('—')
  await expect(page.locator('.accuracy > strong')).toHaveText('—')
  failed = true
  await page.getByRole('button', { name: '重新计算' }).click()
  await expect(panel).toContainText('模型暂不可用')
  await expect(panel).not.toContainText('test-model')
})
