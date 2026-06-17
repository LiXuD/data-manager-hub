import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElMessage } from 'element-plus'
import router from './router'
import App from './App.vue'
import './styles/index.scss'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 全局错误处理器
app.config.errorHandler = (err, _instance, info) => {
  console.error('全局错误:', err, info)
  ElMessage.error('系统异常，请稍后重试')
}

app.mount('#app')
