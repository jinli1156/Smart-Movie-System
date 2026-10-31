/**
 * 列表分页或筛选后滚动到内容起点，兼容页面及内部滚动容器。
 */
/** 在列表更新后滚动到列表起点。 */
export function scrollToListStart(list: HTMLElement | null) {
  if (!list) return
  const header = list.closest('main')?.querySelector<HTMLElement>('.top-bar')
  list.style.scrollMarginTop = `${(header?.getBoundingClientRect().height || 0) + 16}px`
  list.scrollIntoView({ block: 'start', behavior: 'instant' })
}
