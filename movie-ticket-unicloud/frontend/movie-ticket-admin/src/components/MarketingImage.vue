<template>
  <div class="marketing-image"><img v-if="blobUrl" :src="blobUrl" :alt="alt" /><span v-else>{{ failed ? '素材加载失败' : '加载素材…' }}</span></div>
</template>
<script setup lang="ts">
import { fetchCloudFile, apiUrl } from '@/utils/api-url'
import { ref, watch, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/stores/user'
const props = defineProps<{ src: string; alt: string }>()
const store = useUserStore()
const blobUrl = ref(''), failed = ref(false)
let controller: AbortController | undefined
watch(() => props.src, async src => {
  controller?.abort()
  if (blobUrl.value) URL.revokeObjectURL(blobUrl.value)
  blobUrl.value = ''; failed.value = false
  const current = new AbortController(); controller = current
  try {
    const response = await fetchCloudFile(apiUrl(src), { headers: { Authorization: 'Bearer ' + store.token }, signal: current.signal })
    if (!response.ok) throw new Error('图片不可用')
    const blob = await response.blob()
    if (!current.signal.aborted) blobUrl.value = URL.createObjectURL(blob)
  } catch { if (!current.signal.aborted) failed.value = true }
}, { immediate: true })
onBeforeUnmount(() => { controller?.abort(); if (blobUrl.value) URL.revokeObjectURL(blobUrl.value) })
</script>
<style scoped>
.marketing-image{background:#edf0f5;display:flex;align-items:center;justify-content:center;overflow:hidden;min-height:110px;color:#6b7280;font-size:13px}
.marketing-image img{display:block;width:100%;height:100%;object-fit:contain}
</style>
