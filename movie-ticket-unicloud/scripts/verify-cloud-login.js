'use strict'

// 云端部署验收：识别验证码 → 登录 → 带 token 访问受保护接口。
// 用法：node scripts/verify-cloud-login.js <apiBase> <username> <password>

const { PNG } = require('pngjs')

const FONT = {
  2: ['11111', '00001', '00001', '11111', '10000', '10000', '11111'],
  3: ['11111', '00001', '00001', '11111', '00001', '00001', '11111'],
  4: ['10001', '10001', '10001', '11111', '00001', '00001', '00001'],
  5: ['11111', '10000', '10000', '11111', '00001', '00001', '11111'],
  6: ['11111', '10000', '10000', '11111', '10001', '10001', '11111'],
  7: ['11111', '00001', '00010', '00100', '00100', '00100', '00100'],
  8: ['11111', '10001', '10001', '11111', '10001', '10001', '11111'],
  9: ['11111', '10001', '10001', '11111', '00001', '00001', '11111']
}
const DIGITS = '23456789'

function decode(dataUrl) {
  return PNG.sync.read(Buffer.from(dataUrl.split(',')[1], 'base64'))
}

function foreground(png, x, y) {
  const p = (y * png.width + x) * 4
  return png.data[p] < 110 && png.data[p + 2] < 110
}

function recognize(png) {
  let answer = ''
  for (let i = 0; i < 4; i++) {
    const baseLeft = 14 + i * 32
    let best = { digit: '2', score: -Infinity }
    for (const digit of DIGITS) {
      const glyph = FONT[digit]
      for (let left = baseLeft - 3; left <= baseLeft + 3; left++) {
        for (let top = 5; top <= 13; top++) {
          let score = 0
          for (let row = 0; row < 7; row++) {
            for (let col = 0; col < 5; col++) {
              let on = 0
              for (let dy = 0; dy < 4; dy++) for (let dx = 0; dx < 4; dx++) {
                const x = left + col * 4 + dx
                const y = top + row * 4 + dy
                if (x >= 0 && x < png.width && y >= 0 && y < png.height && foreground(png, x, y)) on++
              }
              const expected = glyph[row][col] === '1'
              score += expected ? on : -on
            }
          }
          if (score > best.score) best = { digit, score }
        }
      }
    }
    answer += best.digit
  }
  return answer
}

async function main() {
  const [apiBase, username = 'admin', password = 'admin123'] = process.argv.slice(2)
  if (!apiBase) {
    console.error('用法：node scripts/verify-cloud-login.js https://<云函数URL化域名>/api [username] [password]')
    process.exit(2)
  }
  const captcha = await fetch(`${apiBase}/auth/captcha`).then(r => r.json())
  if (captcha.code !== 200) throw new Error(`获取验证码失败：${captcha.message}`)
  const answer = recognize(decode(captcha.data.image))
  console.log(`验证码识别结果：${answer}`)

  const login = await fetch(`${apiBase}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, captchaId: captcha.data.captchaId, captcha: answer })
  }).then(r => r.json())
  if (login.code !== 200) throw new Error(`登录失败：${login.message}`)
  console.log(`登录成功：${login.data.userInfo.username} 角色=${login.data.userInfo.role}`)

  const token = login.data.token
  const me = await fetch(`${apiBase}/user/me`, { headers: { Authorization: `Bearer ${token}` } }).then(r => r.json())
  if (me.code !== 200) throw new Error(`读取用户资料失败：${me.message}`)
  console.log(`受保护接口可用：user/me 返回 ${me.data.username}`)

  const orders = await fetch(`${apiBase}/order/page?page=1&pageSize=1`, { headers: { Authorization: `Bearer ${token}` } }).then(r => r.json())
  console.log(`订单接口：code=${orders.code} total=${orders.data?.total ?? 'n/a'}`)
  console.log('云端登录链路验收通过')
}

module.exports = { recognize, decode }
if (require.main === module) main().catch(error => {
  console.error(`验收失败：${error.message}`)
  process.exit(1)
})
