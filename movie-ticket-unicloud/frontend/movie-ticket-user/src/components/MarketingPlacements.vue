<template>
  <section v-if="visibleItems.length" class="marketing-placements" aria-label="影院精选活动">
    <div class="placement-heading"><span>影院精选</span><h2>{{ slot === 'HOME' ? '好电影，也有好提案' : '在这里，发现观影新灵感' }}</h2></div>
    <div class="placement-grid"><article v-for="item in visibleItems" :key="item.id" class="placement-card">
      <div class="placement-poster"><img :src="item.imageUrl" :alt="item.title" loading="lazy" /></div>
      <div class="placement-copy"><span class="cinema-name">{{ item.cinemaName }} · {{ item.movieTitle }}</span><h3>{{ item.title }}</h3><p>{{ item.copy }}</p>
        <router-link :to="item.targetUrl">查看场次 <span aria-hidden="true">↗</span></router-link>
      </div>
    </article></div>
  </section>
</template>
<script setup lang="ts">
import { apiUrl } from '@/utils/api-url'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
const props = defineProps<{ slot: 'HOME' | 'CINEMA'; cinemaId?: number }>()
const items = ref<{ id: string; title: string; copy: string; cinemaName: string; movieTitle: string; imageUrl: string; targetUrl: string; endAt?: string }[]>([])
const now = ref(Date.now())
const visibleItems = computed(() => items.value.filter(item => !item.endAt || new Date(item.endAt).getTime() > now.value))
let controller: AbortController | undefined
async function load() {
  controller?.abort(); const current = new AbortController(); controller = current
  if (props.slot === 'CINEMA' && !props.cinemaId) return
  try {
    const params = new URLSearchParams({ slot: props.slot })
    if (props.cinemaId) params.set('cinemaId', String(props.cinemaId))
    const response = await fetch(apiUrl('/api/marketing/placements?' + params), { signal: current.signal, cache: 'no-store' })
    if (!response.ok) return
    const result = await response.json()
    if (!current.signal.aborted && result.code === 200) items.value = (result.data || []).map((item: any) => ({ ...item, imageUrl: apiUrl(item.imageUrl) }))
  } catch { /* Published marketing is optional; movie browsing remains available. */ }
}
watch(() => [props.slot, props.cinemaId], () => { items.value = []; void load() }, { immediate: true })
const expiryTimer = setInterval(() => { now.value = Date.now() }, 1000)
const reloadTimer = setInterval(() => { if (document.visibilityState === 'visible') void load() }, 300000)
onBeforeUnmount(() => { controller?.abort(); clearInterval(expiryTimer); clearInterval(reloadTimer) })
</script>
<style scoped>
.marketing-placements{margin:30px 0 38px}.placement-heading>span{font-size:11px;letter-spacing:2px;color:#d28a58;font-weight:700}.placement-heading h2{font-size:22px;letter-spacing:-.5px;margin:9px 0 20px;color:inherit}.placement-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px}.placement-card{display:grid;grid-template-columns:minmax(0,42%) minmax(0,1fr);background:rgba(24,35,55,.88);border:1px solid rgba(180,195,220,.13);border-radius:16px;overflow:hidden;min-height:240px}
.placement-poster{display:flex;align-items:center;justify-content:center;min-width:0;padding:12px;background:rgba(8,15,28,.45)}
.placement-poster img{display:block;width:100%;height:auto;max-height:460px;object-fit:contain}
.placement-copy{min-width:0;padding:24px;display:flex;flex-direction:column;align-items:flex-start;overflow-wrap:anywhere}.cinema-name{font-size:11px;color:#d6b28c;line-height:1.6}.placement-copy h3{font-size:19px;color:#f1f5fb;line-height:1.5;margin:12px 0}.placement-copy p{font-size:12px;color:#a8b6cc;line-height:1.9;margin:0 0 20px;white-space:pre-wrap}.placement-copy a{margin-top:auto;display:inline-flex;gap:28px;font-size:12px;font-weight:600;color:#f1c89f;text-decoration:none;border-bottom:1px solid #815c3e;padding-bottom:5px}@media(max-width:1000px){.placement-grid{grid-template-columns:1fr}}@media(max-width:550px){.placement-card{grid-template-columns:minmax(0,1fr)}.placement-poster img{max-height:none}.placement-copy{padding:17px}.placement-copy h3{font-size:16px}}
</style>
