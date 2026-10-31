/** 当前标签页内的后台工作状态；退出登录时统一清理。 */
export const PAGE_STATE_PREFIX = 'admin:page-state:v1:'
const memory = new Map<string, string>()
export const UNDEFINED_STATE = '__adminPageStateUndefined'

export function readPageState(key: string): unknown {
  let value = memory.get(key)
  try { value = sessionStorage.getItem(key) ?? value } catch { /* 禁用存储时使用内存。 */ }
  if (!value) return undefined
  try { return JSON.parse(value) } catch { return undefined }
}

export function writePageState(key: string, value: unknown) {
  // 保留显式清空的筛选值，避免“全部”在恢复时变回默认的“可售”。
  const serialized = JSON.stringify(value, (_key, item) => item === undefined ? { [UNDEFINED_STATE]: true } : item)
  memory.set(key, serialized)
  try { sessionStorage.setItem(key, serialized) } catch { /* 配额不足时仍保留页面切换状态。 */ }
}

export function clearPageStates() {
  memory.clear()
  try {
    const keys = Array.from({ length: sessionStorage.length }, (_, i) => sessionStorage.key(i))
    keys.forEach(key => { if (key?.startsWith(PAGE_STATE_PREFIX)) sessionStorage.removeItem(key) })
  } catch { /* 存储不可用不影响退出。 */ }
}
