<template>
  <button
    type="button"
    class="watchlist-heart"
    :class="[`is-${size}`, `is-${variant}`, { 'is-active': active, 'is-loading': loading }]"
    :aria-label="accessibleLabel"
    :title="accessibleLabel"
    :aria-pressed="active"
    :disabled="disabled || loading"
    @click="$emit('click', $event)"
  >
    <span v-if="loading" class="watchlist-heart__spinner" aria-hidden="true" />
    <svg
      v-else
      class="watchlist-heart__icon"
      viewBox="0 0 24 24"
      aria-hidden="true"
    >
      <path d="M12 21.35 10.55 20.03C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09A6 6 0 0 1 16.5 3C19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54Z" />
    </svg>
  </button>
</template>

<script setup lang="ts">
/**
 * 可复用想看按钮，统一选中、加载和禁用状态，由父页面执行收藏请求。
 */
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  active?: boolean
  loading?: boolean
  disabled?: boolean
  size?: 'small' | 'medium' | 'large'
  variant?: 'light' | 'dark' | 'overlay'
  label?: string
}>(), {
  active: false,
  loading: false,
  disabled: false,
  size: 'medium',
  variant: 'light',
  label: ''
})

defineEmits<{ click: [event: MouseEvent] }>()

const accessibleLabel = computed(() => props.label || (props.active ? '移出想看' : '加入想看'))
</script>

<style scoped>
.watchlist-heart {
  --heart-size: 42px;
  --heart-fill: #fff;
  --heart-stroke: #8e9691;
  width: var(--heart-size);
  height: var(--heart-size);
  flex: 0 0 var(--heart-size);
  display: inline-grid;
  place-items: center;
  padding: 0;
  appearance: none;
  border: 0;
  border-radius: 50%;
  outline: 0;
  color: var(--heart-fill);
  background: #e5e7e4;
  box-shadow: 0 5px 14px rgba(38, 43, 39, .12);
  cursor: pointer;
  transition: background-color .18s ease, transform .18s ease, box-shadow .18s ease;
}

.watchlist-heart.is-small { --heart-size: 36px; }
.watchlist-heart.is-large { --heart-size: 48px; }
.watchlist-heart__icon {
  width: 68%;
  height: 68%;
  overflow: visible;
  fill: var(--heart-fill);
  stroke: var(--heart-stroke);
  stroke-width: 1.45;
  stroke-linejoin: round;
  filter: drop-shadow(0 2px 3px rgba(30, 34, 31, .28));
  transition: fill .18s ease, stroke .18s ease, transform .18s ease, filter .18s ease;
}

.watchlist-heart:hover:not(:disabled) {
  background: #dcdeda;
  transform: translateY(-2px) scale(1.04);
  box-shadow: 0 8px 18px rgba(38, 43, 39, .16);
}

.watchlist-heart.is-dark {
  --heart-stroke: rgba(255, 255, 255, .95);
  background: rgba(229, 231, 228, .92);
  backdrop-filter: blur(8px);
}

.watchlist-heart.is-overlay {
  --heart-stroke: rgba(92, 99, 94, .72);
  background: rgba(229, 231, 228, .94);
  backdrop-filter: blur(8px);
}

.watchlist-heart.is-active {
  --heart-fill: #ec4899;
  --heart-stroke: #db2777;
  background: #f8e7ef;
}

.watchlist-heart.is-active .watchlist-heart__icon {
  filter: drop-shadow(0 3px 5px rgba(219, 39, 119, .34));
}

.watchlist-heart__spinner {
  width: 48%;
  height: 48%;
  border: 2px solid rgba(142, 150, 145, .32);
  border-top-color: #ec4899;
  border-radius: 50%;
  animation: heart-spin .8s linear infinite;
}

.watchlist-heart:focus-visible {
  outline: 3px solid rgba(236, 72, 153, .28);
  outline-offset: 3px;
}

.watchlist-heart:focus-visible .watchlist-heart__icon {
  filter: drop-shadow(0 0 3px rgba(236, 72, 153, .8));
}

.watchlist-heart:disabled { cursor: not-allowed; opacity: .58; }

@keyframes heart-spin { to { transform: rotate(360deg); } }

@media (prefers-reduced-motion: reduce) {
  .watchlist-heart { transition: none; }
  .watchlist-heart__spinner { animation: none; }
}
</style>
