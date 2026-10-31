'use strict'

const fs = require('fs')
const path = require('path')
const { spawnSync } = require('child_process')

const root = path.resolve(__dirname, '..')
const apiBase = process.argv[2]
let url
try { url = new URL(apiBase) } catch (_) { /* checked below */ }
if (!url || url.protocol !== 'https:' || !url.pathname.endsWith('/api') || url.search || url.hash) {
  console.error('用法：node scripts/build-hosting.js https://<云函数URL化域名>/api')
  process.exit(2)
}

// 高德地图凭据：优先用当前终端的环境变量，缺失时回退到主项目 movie-ticket-user/.env（已被 Git 忽略）。
function amapEnv() {
  const env = { ...process.env }
  if (env.VITE_AMAP_KEY && env.VITE_AMAP_SECURITY_CODE) return env
  const dotenv = path.resolve(root, '..', 'movie-ticket-user', '.env')
  if (!fs.existsSync(dotenv)) return env
  for (const line of fs.readFileSync(dotenv, 'utf8').split('\n')) {
    const match = /^\s*(VITE_AMAP_KEY|VITE_AMAP_SECURITY_CODE)\s*=\s*(.*)$/.exec(line)
    if (match && !env[match[1]]) env[match[1]] = match[2].trim()
  }
  return env
}

const buildEnv = amapEnv()
if (!buildEnv.VITE_AMAP_KEY || !buildEnv.VITE_AMAP_SECURITY_CODE) {
  console.error('缺少 VITE_AMAP_KEY / VITE_AMAP_SECURITY_CODE，地图功能将不可用。请设置环境变量或填写 movie-ticket-user/.env。')
  process.exit(2)
}

const frontend = path.join(root, 'frontend')
for (const name of ['movie-ticket-user', 'movie-ticket-admin']) {
  const cwd = path.join(frontend, name)
  if (process.argv.includes('--stage-only')) {
    const assets = path.join(cwd, 'dist/assets')
    if (!fs.readdirSync(assets).some(name => name.endsWith('.js') && fs.readFileSync(path.join(assets, name), 'utf8').includes(apiBase))) throw new Error('Existing build does not contain the requested cloud API URL')
    continue
  }
  const temp = path.join(root, '.local', 'build-tmp', name)
  fs.mkdirSync(temp, { recursive: true })
  const result = spawnSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['run', 'build'], {
    cwd,
    env: { ...buildEnv, VITE_API_BASE_URL: apiBase, TEMP: temp, TMP: temp },
    stdio: 'inherit',
    shell: process.platform === 'win32'
  })
  if (result.error) throw result.error
  if (result.status !== 0) process.exit(result.status || 1)
}

const local = path.join(root, '.local')
fs.mkdirSync(local, { recursive: true })
const output = fs.mkdtempSync(path.join(local, 'hosting-stage-'))
fs.cpSync(path.join(frontend, 'movie-ticket-user', 'dist'), output, { recursive: true })
fs.cpSync(path.join(frontend, 'movie-ticket-admin', 'dist'), path.join(output, 'admin'), { recursive: true })
const userHtml = fs.readFileSync(path.join(output, 'index.html'), 'utf8')
const adminHtml = fs.readFileSync(path.join(output, 'admin', 'index.html'), 'utf8')
if (!userHtml.includes('/assets/') || !adminHtml.includes('/admin/assets/')) throw new Error('前端资源路径不正确')

let files = 0, bytes = 0
const walk = dir => {
  for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, item.name)
    if (item.isDirectory()) walk(full)
    else {
      const size = fs.statSync(full).size
      if (size > 50 * 1024 * 1024) throw new Error(`托管文件超过 50 MB: ${full}`)
      files++
      bytes += size
    }
  }
}
walk(output)
fs.writeFileSync(path.join(output, 'deployment-manifest.json'), JSON.stringify({ apiBase, files, bytes, createdAt: new Date().toISOString() }, null, 2) + '\n')
console.log(`托管目录：${output}`)
console.log(`文件：${files}，总大小：${(bytes / 1048576).toFixed(2)} MB`)
