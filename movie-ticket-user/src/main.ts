/**
 * 应用启动入口，注册状态管理、路由和界面组件后挂载 Vue 应用。
 */
import { createApp, defineAsyncComponent } from 'vue'
import { createPinia } from 'pinia'
// Programmatic message boxes are not covered by template component auto-imports.
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/message/style/css'
import './styles/dialogs.css'
import App from './App.vue'
import poster from './directives/poster'
import router from './router'

const app = createApp(App)
const pinia = createPinia()

app.component('MaterialIcon', defineAsyncComponent(() => import('./components/MaterialIcon.vue')))
app.directive('poster', poster)

app.use(pinia)
app.use(router)

app.mount('#app')
