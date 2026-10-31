'use strict'

// 环境变量读取：优先云端环境变量，缺失时回退到同目录 secrets.json（.git 忽略）。
// secrets.json 用于无法通过控制台配置环境变量的自动化部署场景。

let secrets = {}
try { secrets = require('./secrets.json') } catch (_) { secrets = {} }

module.exports = function readEnv(name) {
  return process.env[name] !== undefined && process.env[name] !== '' ? process.env[name] : secrets[name]
}
