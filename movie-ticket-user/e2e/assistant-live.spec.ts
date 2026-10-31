import { expect, test } from '@playwright/test'

// Opt-in only: this test uses the configured backend and incurs a small model API request.
test('live DeepSeek stream queries project data and renders real seat links', async ({ page }) => {
  test.skip(process.env.ASSISTANT_LIVE !== '1', 'Set ASSISTANT_LIVE=1 and start the backend to run paid API verification')
  test.setTimeout(190000)
  await page.goto('/assistant')
  await expect(page.getByText('DeepSeek · deepseek-v4-flash', { exact: true })).toBeVisible()
  await page.getByLabel('向影票观提问').fill('请查询今天本系统的可购票场次，给出电影、影院、开场时间、票价和余票。简短回答。')
  const streamResponse = page.waitForResponse(r => r.url().endsWith('/api/assistant/chat/stream'))
  await page.getByRole('button', { name: '发送', exact: true }).click()
  const response = await streamResponse
  expect(response.headers()['content-type']).toContain('text/event-stream')
  await expect(page.locator('.primary-results')).toBeVisible({ timeout: 170000 })
  await expect(page.getByRole('button', { name: '停止回答' })).toHaveCount(0, { timeout: 170000 })
  // The fetch reader is cancelled after the terminal SSE event; Chromium does not
  // retain a response body for that stream. Assert the events' rendered effects.
  await expect(page.locator('.bubble').last()).not.toBeEmpty()
  await expect(page.locator('.query-results')).toHaveCount(0)
  await page.screenshot({ path: '../.local/assistant-live.png', fullPage: true })
  await expect(page.locator('.message-label').last(), await page.locator('.bubble').last().innerText()).not.toContainText('未完成')
  await expect(page.getByRole('link', { name: '去选座' }).first()).toHaveAttribute('href', /\/showtime\/\d+\/seats/)
  await page.screenshot({ path: '../.local/assistant-live.png', fullPage: true })
})
