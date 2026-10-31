import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { fileURLToPath, URL } from 'node:url'

const toKebabCase = (value: string) => value.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()
const elementPlusParentComponents: Record<string, string> = {
  'anchor-link': 'anchor',
  'aside': 'container',
  'avatar-group': 'avatar',
  'breadcrumb-item': 'breadcrumb',
  'button-group': 'button',
  'carousel-item': 'carousel',
  'checkbox-button': 'checkbox',
  'checkbox-group': 'checkbox',
  'collapse-item': 'collapse',
  'descriptions-item': 'descriptions',
  'dropdown-item': 'dropdown',
  'dropdown-menu': 'dropdown',
  'footer': 'container',
  'form-item': 'form',
  'header': 'container',
  'main': 'container',
  'menu-item': 'menu',
  'menu-item-group': 'menu',
  'option': 'select',
  'option-group': 'select',
  'radio-button': 'radio',
  'radio-group': 'radio',
  'skeleton-item': 'skeleton',
  'splitter-panel': 'splitter',
  'step': 'steps',
  'sub-menu': 'menu',
  'tab-pane': 'tabs',
  'table-column': 'table',
  'timeline-item': 'timeline',
  'tour-step': 'tour'
}

const elementPlusComponentResolver = (name: string) => {
  if (!/^El[A-Z]/.test(name)) return undefined
  const componentName = toKebabCase(name.slice(2))
  const importName = elementPlusParentComponents[componentName] || componentName
  return {
    name,
    from: `element-plus/es/components/${importName}/index`,
    sideEffects: `element-plus/es/components/${componentName}/style/css`
  }
}

const elementPlusDirectiveResolver = {
  type: 'directive' as const,
  resolve(name: string) {
    if (name !== 'Loading') return undefined
    return {
      name: 'ElLoadingDirective',
      from: 'element-plus/es/components/loading/index',
      sideEffects: 'element-plus/es/components/loading/style/css'
    }
  }
}

export default defineConfig(({ command }) => ({
  plugins: [
    vue(),
    AutoImport({
      dts: command === 'serve' ? 'auto-imports.d.ts' : false,
      imports: ['vue', 'vue-router', 'pinia']
    }),
    Components({
      // Production builds reuse declarations and can run beside the preview server.
      dts: command === 'serve' ? 'components.d.ts' : false,
      resolvers: [elementPlusComponentResolver, elementPlusDirectiveResolver]
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('/node_modules/element-plus/') || id.includes('/node_modules/@element-plus/icons-vue/')) return 'vendor-element-plus'
          if (id.includes('/node_modules/html5-qrcode/')) return 'vendor-qr-scanner'
          if (id.includes('/node_modules/qrcode/')) return 'vendor-qrcode'
          if (id.includes('/node_modules/@amap/amap-jsapi-loader/')) return 'vendor-map'
          if (id.includes('/node_modules/@iconify/')) return 'vendor-icons'
          if (id.includes('/node_modules/axios/')) return 'vendor-http'
          if (id.includes('/node_modules/vue/') || id.includes('/node_modules/@vue/') || id.includes('/node_modules/vue-router/') || id.includes('/node_modules/pinia/')) return 'vendor-vue'
        }
      }
    }
  },
  server: {
    host: 'localhost',
    port: 3000,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
}))
