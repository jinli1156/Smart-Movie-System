export function scrollToListStart(list: HTMLElement | null) {
  if (!list) return
  const header = list.closest('main')?.querySelector<HTMLElement>('.top-bar')
  list.style.scrollMarginTop = `${(header?.getBoundingClientRect().height || 0) + 16}px`
  list.scrollIntoView({ block: 'start', behavior: 'instant' })
}
