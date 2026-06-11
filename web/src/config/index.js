// src/config/index.js — 唯一「宏变量」入口（与 SpringBoot 端的 PhoneConfig 同等地位）
// 业务代码只能从这里取配置；禁止在组件内写死 URL、刷新周期、阈值。

const env = process.env

export default {
  // 应用
  appName: env.VUE_APP_NAME || 'phone-analysis 看板',

  // 后端接口前缀
  apiBase: env.VUE_APP_API_BASE || '/api',

  // SSE 端点（走 apiBase 同源 / 同代理）
  sseUrl: (env.VUE_APP_API_BASE || '/api') + '/alert/stream',

  // 看板自动刷新（毫秒）—— 与 application.yml.phone.dashboard.refreshIntervalSeconds 对齐 (默认 600s)
  dashboardRefreshMs: parseInt(env.VUE_APP_DASHBOARD_REFRESH_MS || '600000', 10),

  // 告警栏最多保留条数（前端侧）
  alertMaxHistory: parseInt(env.VUE_APP_ALERT_MAX_HISTORY || '100', 10),

  // 告警级别 → ECharts / Element 颜色
  alertColor: {
    high: '#F56C6C',
    mid:  '#E6A23C',
    low:  '#909399'
  },

  // 看板调色板（统一所有 ECharts 图）
  chartPalette: [
    '#5470C6', '#91CC75', '#FAC858', '#EE6666', '#73C0DE',
    '#3BA272', '#FC8452', '#9A60B4', '#EA7CCC'
  ]
}
