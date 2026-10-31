<template>
  <div id="app">
    <router-view />
    <MovieAssistant v-if="assistantReady" />
  </div>
</template>

<script setup lang="ts">
/**
 * 应用根组件，承载路由页面与全局界面。
 */
import { defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'

const MovieAssistant = defineAsyncComponent(() => import('@/components/MovieAssistant.vue'))
const assistantReady = ref(false)
let assistantTimer: number | undefined

onMounted(() => {
  assistantTimer = window.setTimeout(() => {
    assistantReady.value = true
  }, 1500)
})

onBeforeUnmount(() => {
  if (assistantTimer) window.clearTimeout(assistantTimer)
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background-color: #f5f5f5;
}

#app {
  min-height: 100vh;
}

/* Shared sizing behavior for the local SVG icon component. */
.msr,
.material-symbols-rounded {
  line-height: 1;
  display: inline-block;
  flex-shrink: 0;
}

/* Show the exact selected artwork's wordmark region without retyping its glyphs. */
.brand-wordmark {
  display: inline-block !important;
  width: 120px;
  height: 28px;
  flex: 0 0 auto;
  background: url('/xiangzhi-wordmark-source.png') no-repeat;
  background-size: 125.4% auto;
  background-position: 50% 82.85%;
}

img[src="/xiangzhi-ticket.png"] {
  transform: scale(1.5);
}

.brand-logo-image {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

.logo-mark:has(> .brand-logo-image),
.brand-mark:has(> .brand-logo-image) {
  border-radius: 0;
  background: transparent !important;
}
</style>
