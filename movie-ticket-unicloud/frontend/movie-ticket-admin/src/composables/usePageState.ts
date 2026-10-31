import { isRef, toRef, unref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { PAGE_STATE_PREFIX, UNDEFINED_STATE, readPageState, writePageState } from '@/utils/pageState'

const isObject = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value)

/** 仅恢复已声明的字段；损坏或类型不符的缓存回退到页面默认值。 */
function restoreValue(defaultValue: unknown, saved: unknown): unknown {
  if (isObject(saved) && saved[UNDEFINED_STATE] === true) return undefined
  if (defaultValue === undefined) return typeof saved === 'number' && Number.isFinite(saved) ? saved : undefined
  if (defaultValue === null) return saved === null || isObject(saved) ? saved : null
  if (Array.isArray(defaultValue)) return Array.isArray(saved) && saved.every(item => typeof item === 'number' && Number.isFinite(item)) ? saved : defaultValue
  if (isObject(defaultValue)) {
    if (!isObject(saved)) return defaultValue
    return Object.fromEntries(Object.entries(defaultValue).map(([key, value]) => [
      key, Object.prototype.hasOwnProperty.call(saved, key) ? restoreValue(value, saved[key]) : value
    ]))
  }
  return typeof saved === typeof defaultValue && (typeof saved !== 'number' || Number.isFinite(saved)) ? saved : defaultValue
}

/**
 * 保存明确选取的查询/工作参数。按账号、角色和所属影院隔离，刷新后也可恢复。
 * 列表数据、编辑弹窗与提交状态不参与缓存，返回页面后照常从接口读取。
 */
export function usePageState(page: string, sources: Record<string, object>) {
  const userStore = useUserStore()
  const scope = () => {
    const user = userStore.userInfo
    return userStore.isLoggedIn && user ? JSON.stringify([user.id, user.role, user.cinemaId ?? null]) : ''
  }
  const owner = scope()
  if (!owner) return
  const key = `${PAGE_STATE_PREFIX}${encodeURIComponent(owner)}:${page}`
  const saved = readPageState(key)
  if (isObject(saved)) {
    for (const [name, source] of Object.entries(sources)) {
      if (!Object.prototype.hasOwnProperty.call(saved, name)) continue
      const restored = restoreValue(unref(source), saved[name])
      if (isRef(source)) source.value = restored
      else if (isObject(restored)) Object.assign(source, restored)
    }
  }
  watch(() => Object.fromEntries(Object.entries(sources).map(([name, source]) => [name, unref(source)])), value => {
    // 登出后未完成的请求不得把旧账号状态重新写回。
    if (scope() === owner) writePageState(key, value)
  }, { deep: true, flush: 'sync' })
}

/** 列表统一保留筛选、页码及每页条数，总数由接口更新。 */
export function useListPageState(page: string, searchForm: object, pagination: { page: number; size: number }) {
  usePageState(page, { searchForm, page: toRef(pagination, 'page'), size: toRef(pagination, 'size') })
  if (!Number.isSafeInteger(pagination.page) || pagination.page < 1) pagination.page = 1
  if (![10, 20, 50, 100].includes(pagination.size)) pagination.size = 10
}
