// src/main.js — 入口
import Vue from 'vue'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import * as echarts from 'echarts'

import App from './App.vue'
import router from './router'
import config from '@/config'
import { loadDict } from '@/utils/dict'

Vue.use(ElementUI, { size: 'small' })
Vue.config.productionTip = false

// 全局：$echarts 与 $cfg
Vue.prototype.$echarts = echarts
Vue.prototype.$cfg = config

// 启动前预热中英映射
loadDict().finally(() => {
  new Vue({ router, render: (h) => h(App) }).$mount('#app')
})
