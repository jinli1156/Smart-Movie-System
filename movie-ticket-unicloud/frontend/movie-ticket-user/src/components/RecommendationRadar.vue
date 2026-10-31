<template>
  <div class="radar" role="img" :aria-label="ariaLabel">
    <svg viewBox="0 0 260 230" aria-hidden="true">
      <g class="grid">
        <polygon v-for="level in 4" :key="level" :points="polygonPoints(level * 25)" />
        <line v-for="point in outerPoints" :key="`${point.x}-${point.y}`" :x1="center.x" :y1="center.y" :x2="point.x" :y2="point.y" />
      </g>
      <polygon class="value-area" :points="valuePoints" />
      <circle v-for="point in plottedPoints" :key="point.label" :cx="point.x" :cy="point.y" r="3.5" />
      <g v-for="label in labelPoints" :key="label.text" class="axis-label">
        <text :x="label.x" :y="label.y" text-anchor="middle">{{ label.text }}</text>
        <text :x="label.x" :y="label.y + 13" text-anchor="middle" class="value">{{ label.value }}</text>
      </g>
    </svg>
    <div class="radar-summary">{{ data.summary }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RecommendationRadar } from '@/types'

const props = defineProps<{ data: RecommendationRadar }>()
const center = { x: 130, y: 108 }
const radius = 72
const axes = computed(() => Object.entries(props.data.dimensions))

const pointAt = (index: number, percent: number, extra = 0) => {
  const angle = -Math.PI / 2 + index * Math.PI * 2 / Math.max(axes.value.length, 1)
  const distance = (radius + extra) * Math.max(0, Math.min(100, percent)) / 100
  return { x: center.x + Math.cos(angle) * distance, y: center.y + Math.sin(angle) * distance }
}
const polygonPoints = (percent: number) => axes.value.map((_, index) => {
  const point = pointAt(index, percent)
  return `${point.x},${point.y}`
}).join(' ')
const outerPoints = computed(() => axes.value.map((_, index) => pointAt(index, 100)))
const plottedPoints = computed(() => axes.value.map(([label, value], index) => ({ label, ...pointAt(index, value) })))
const valuePoints = computed(() => plottedPoints.value.map(point => `${point.x},${point.y}`).join(' '))
const labelPoints = computed(() => axes.value.map(([text, value], index) => ({ text, value, ...pointAt(index, 100, 28) })))
const ariaLabel = computed(() => `推荐偏好雷达图：${axes.value.map(([name, value]) => `${name}${value}分`).join('，')}`)
</script>

<style scoped>
.radar{display:flex;flex-direction:column;align-items:center;gap:5px;padding:10px 4px 12px;border:1px solid rgba(255,255,255,.1);border-radius:12px;background:rgba(255,255,255,.04)}
svg{display:block;width:100%;max-width:285px;height:auto;overflow:visible}.grid polygon,.grid line{fill:none;stroke:rgba(255,255,255,.14);stroke-width:1}.value-area{fill:rgba(192,132,252,.28);stroke:#c084fc;stroke-width:2;stroke-linejoin:round}.radar circle{fill:#fff;stroke:#a855f7;stroke-width:2}.axis-label text{fill:rgba(255,255,255,.75);font-size:9px;font-weight:600}.axis-label .value{fill:#fff;font-family:'JetBrains Mono',monospace;font-size:9px;font-weight:700}.radar-summary{padding:0 12px;color:rgba(255,255,255,.72);font-size:11px;line-height:1.55;text-align:center}
</style>
