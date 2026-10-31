import type { Directive } from 'vue'

interface PosterOptions {
  eager?: boolean
}

const FALLBACK_SRC = '/poster-placeholder.svg'

const poster: Directive<HTMLImageElement, PosterOptions | undefined> = {
  mounted(el, binding) {
    const eager = binding.value?.eager === true
    el.loading = eager ? 'eager' : 'lazy'
    el.decoding = 'async'
    if (eager) el.fetchPriority = 'high'

    el.addEventListener('error', () => {
      if (el.dataset.posterFallback === 'true') return
      el.dataset.posterFallback = 'true'
      el.src = FALLBACK_SRC
    })

    if (!el.getAttribute('src')) {
      el.dataset.posterFallback = 'true'
      el.src = FALLBACK_SRC
    }
  },
  updated(el) {
    if (el.getAttribute('src') !== FALLBACK_SRC) {
      delete el.dataset.posterFallback
    }
  }
}

export default poster
