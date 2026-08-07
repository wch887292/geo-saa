import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import { ElLoading } from 'element-plus/es/components/loading/index.mjs'
import {
  Odometer,
  Search,
  Notebook,
  Edit,
  FolderChecked,
  Share,
  DataAnalysis,
  Setting,
  Lock,
  Cpu,
  ArrowDown,
  Document,
  Promotion,
  Medal,
  UserFilled
} from '@element-plus/icons-vue'

// Element Plus 按需引入：模板中的 <el-*> 组件由 vite 插件自动按需加载并注入样式，
// 这里仅需手动导入「编程式组件 / 指令」所需的样式。
// - base：组件样式依赖的设计变量（--el-*）
// - message / message-box / notification：ElMessage / ElMessageBox / ElNotification 的样式
// - loading：v-loading 指令的样式
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'

const app = createApp(App)
app.use(router)
app.use(createPinia())

// 仅注册实际用到的图标（避免 import * 全量打入 2000+ 图标），显式注册即可按需打包。
const icons = {
  Odometer,
  Search,
  Notebook,
  Edit,
  FolderChecked,
  Share,
  DataAnalysis,
  Setting,
  Lock,
  Cpu,
  ArrowDown,
  Document,
  Promotion,
  Medal,
  UserFilled
}
for (const [name, component] of Object.entries(icons)) {
  app.component(name, component)
}

// 手动注册 v-loading 指令（原先由 app.use(ElementPlus) 提供）
app.directive('loading', ElLoading.directive)

app.mount('#app')
