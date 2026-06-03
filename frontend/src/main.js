import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './style.css'
import './collaboration/autoInit'
import { clearLegacyStaticDemoSession } from './mock/staticDemo'

clearLegacyStaticDemoSession()

const app = createApp(App)

// 注册 Element Plus 图标，页面中可以直接使用图标组件。
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(router)
app.use(ElementPlus)
app.mount('#app')
