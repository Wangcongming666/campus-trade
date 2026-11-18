import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { MotionPlugin } from '@vueuse/motion'

import '@fontsource-variable/inter' // Inter 可变字体（数字/拉丁）
import 'element-plus/dist/index.css' // 1. Element 基础样式
import 'element-plus/theme-chalk/dark/css-vars.css' // 2. Element 官方暗色变量（html.dark 时生效）
import './styles/tokens.css' // 3. 设计变量（亮/暗）
import './styles/element-plus-theme.css' // 4. Element 主题覆盖
import './styles/index.scss' // 5. 全局样式（旧变量别名）
import './styles/components.scss' // 6. 通用组件样式与动效关键帧

import App from './App.vue'
import router from './router'
import reveal from './directives/reveal'
import './composables/useTheme' // 主题：跟随系统 / 手动选择（html.dark）

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.use(MotionPlugin)
app.directive('reveal', reveal)
app.mount('#app')
