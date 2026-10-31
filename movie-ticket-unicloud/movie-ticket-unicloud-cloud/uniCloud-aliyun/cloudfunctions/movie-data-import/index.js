'use strict'

// 一次性数据导入云函数，导入已完成并移除 URL 化入口。
// 保留此占位实现仅为避免云端残留可执行入口；可在 uniCloud 控制台直接删除该函数。

exports.main = async function () {
  return { code: 404, message: '该云函数已停用' }
}
