<template>
  <Icon :icon="iconName" aria-hidden="true" />
</template>

<script setup lang="ts">
/**
 * 将图标名称映射到本地图标数据，统一图标尺寸和展示方式。
 */
import { computed } from 'vue'
import { Icon, addCollection } from '@iconify/vue'
import type { IconifyJSON } from '@iconify/types'
import materialSymbols from '@/icons/materialSymbols.json'

const icons = materialSymbols as IconifyJSON
addCollection(icons)

const props = defineProps<{
  name: string
}>()

const iconName = computed(() => {
  const normalized = props.name.trim().replace(/_/g, '-')
  const rounded = `${normalized}-rounded`
  return `material-symbols:${icons.icons[rounded] ? rounded : normalized}`
})
</script>
