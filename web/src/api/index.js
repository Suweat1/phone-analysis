// src/api/index.js — 所有 REST 端点封装，与 app/RUN.md §3 的接口一一对应
import http from './http'

export const api = {
  // 健康
  health: () => http.get('/health'),

  // 字段映射
  dictAll: () => http.get('/dict/columns'),

  // 看板
  overview:           ()              => http.get('/dashboard/overview'),
  profitAnomaly:      (from, to)      => http.get('/dashboard/profit-anomaly', { params: { from, to } }),
  metricCodes:        ()              => http.get('/dashboard/metric/codes'),
  metricTrend:        (code, from, to) => http.get('/dashboard/metric/trend', { params: { code, from, to } }),
  lowContribModels:   ()              => http.get('/dashboard/low-contrib/model'),
  lowContribChannels: ()              => http.get('/dashboard/low-contrib/channel'),
  profitDecomp:       (ym)            => http.get('/dashboard/profit-decomp', { params: { ym } }),
  recentMonths:       ()              => http.get('/dashboard/profit-decomp/months'),
  highValueModels:    ()              => http.get('/dashboard/high-value/model'),
  segmentTopMargins:  ()              => http.get('/dashboard/segment/top-margin'),
  growthPotentials:   ()              => http.get('/dashboard/growth-potential'),

  // 告警（历史）
  alertRecent: () => http.get('/alert/recent')
}

export default api
