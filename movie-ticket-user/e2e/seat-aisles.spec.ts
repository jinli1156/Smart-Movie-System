import { expect, test } from '@playwright/test'

for (const scenario of [
  { name: '多条过道', layout: JSON.stringify({ seats: [[1, 1, 1, 1, 1, 0]], aisleAfterCols: [2, 4] }), aisles: 2 },
  { name: '无过道', layout: JSON.stringify({ seats: [[1, 1, 1, 1, 1, 0]], aisleAfterCols: [] }), aisles: 0 },
  { name: '旧布局中间过道', layout: '[[1,1,1,1,1,0]]', aisles: 1 },
  { name: '左右两侧过道', layout: JSON.stringify({ seats: [[1, 1, 1, 1, 1, 0]], aisleAfterCols: [0, 6] }), aisles: 2, edges: true },
  { name: '两侧与中间过道', layout: JSON.stringify({ seats: [[1, 1, 1, 1, 1, 0]], aisleAfterCols: [0, 2, 6] }), aisles: 3, edges: true }
]) {
  test(`${scenario.name}显示与跨过道推荐校验`, async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.setItem('token', 'e30.eyJleHAiOjQxMDI0NDQ4MDB9.sig')
      localStorage.setItem('userInfo', JSON.stringify({ id: 7, username: 'user', role: 'USER' }))
    })
    await page.route(url => url.pathname.startsWith('/api/'), route => {
      const path = new URL(route.request().url()).pathname
      let data: unknown = []
      if (path === '/api/showtime/101') data = {
        id: 101, movieId: 11, movieTitle: '过道选座验证', cinemaName: '测试影院', hallName: '测试厅',
        startTime: '2099-09-03T19:30:00', endTime: '2099-09-03T21:30:00', basePrice: 48, status: 0
      }
      if (path.endsWith('/seats')) data = {
        totalRows: 1, totalCols: 6, seatLayout: scenario.layout, occupiedSeats: [], totalSeats: 6, availableSeats: 6
      }
      if (path.endsWith('/seat-recommendation')) data = {
        recommendations: [{
          title: '情侣测试方案', icon: 'favorite', match: 98, seatLabel: '1排 2-4座',
          // Choose a block crossing the configured/legacy aisle.
          seats: scenario.aisles === 1 ? ['1排3座', '1排4座'] : ['1排2座', '1排3座'], count: 2,
          unitPrice: 48, reason: '双人连座', tags: [], singleSeatPenalty: 0,
          scores: { visionCenter: 90, goldenDistance: 90, continuity: 100, accessConvenience: 90, preference: 100, realtimeAvailability: 100 }
        }], interpretedPreference: 'COUPLE', preferenceSummary: '情侣双人', analysisSource: 'algorithm',
        availabilityToken: 'test', generatedAt: new Date().toISOString()
      }
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
    })
    await page.goto('/showtime/101/seats')
    await expect(page.locator('.seat-map .seat')).toHaveCount(6)
    await expect(page.locator('.seat-map .aisle')).toHaveCount(scenario.aisles)
    const disabled = page.locator('.seat-map [aria-label="1排6座"]')
    await expect(disabled).toHaveClass(/disabled/)
    await disabled.click()
    await expect(disabled).not.toHaveClass(/selected/)
    if (scenario.edges) {
      await expect(page.locator('.seat-row > .aisle').first()).toHaveAttribute('aria-label', '最左侧过道')
      await expect(page.locator('.seat-row > .aisle').last()).toHaveAttribute('aria-label', '最右侧过道')
    }
    if (scenario.aisles && scenario.name !== '左右两侧过道') {
      await expect(page.getByText('暂无满足 2 人同排且不跨过道的连座，请调整人数或场次')).toBeVisible()
      await expect(page.locator('.seat.recommended')).toHaveCount(0)
    } else {
      await expect(page.getByText('情侣测试方案', { exact: true })).toBeVisible()
      await expect(page.locator('.seat.recommended')).toHaveCount(2)
    }
    await page.screenshot({ path: test.info().outputPath('seat-aisles.png'), fullPage: true })
  })
}
