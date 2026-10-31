<template>
  <button v-if="route.path !== '/assistant'" class="assistant-fab" aria-label="打开影票观" @click="router.push('/assistant')">
    <MaterialIcon class="msr" name="smart_toy" /><span>影票观</span>
  </button>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()
const openAssistant = (event: Event) => {
  const question = (event as CustomEvent<string>).detail
  void router.push({ path: '/assistant', query: typeof question === 'string' ? { q: question.slice(0, 500) } : {} })
}
onMounted(() => window.addEventListener('open-movie-assistant', openAssistant))
onBeforeUnmount(() => window.removeEventListener('open-movie-assistant', openAssistant))
</script>
<style scoped>
.assistant-fab { position:fixed;right:24px;bottom:24px;z-index:1000;display:flex;align-items:center;gap:9px;padding:15px 20px;border:0;border-radius:999px;color:#171a17;background:#ff8b0a;box-shadow:0 8px 26px #30220d30;font-size:16px;line-height:1.5;font-weight:700;cursor:pointer }
@media(max-width:600px){.assistant-fab{right:14px;bottom:14px;padding:14px}.assistant-fab span{display:none}}
</style>
