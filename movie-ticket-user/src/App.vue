<template>
  <div id="app">
    <router-view />
    <MovieAssistant />
  </div>
</template>

<script setup lang="ts">
import MovieAssistant from '@/components/MovieAssistant.vue'
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

.brand-logo-image {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
}

.logo-mark:has(> .brand-logo-image) {
  border-radius: 0;
  background: transparent !important;
}
</style>
