// src/router/index.js
import Vue from 'vue'
import Router from 'vue-router'

Vue.use(Router)

export default new Router({
  mode: 'hash',
  routes: [
    { path: '/', redirect: '/overview' },
    { path: '/overview',         component: () => import('@/views/dashboard/Overview.vue') },
    { path: '/profit-anomaly',   component: () => import('@/views/dashboard/ProfitAnomaly.vue') },
    { path: '/metric-trend',     component: () => import('@/views/dashboard/MetricTrend.vue') },
    { path: '/low-contrib',      component: () => import('@/views/dashboard/LowContrib.vue') },
    { path: '/profit-decomp',    component: () => import('@/views/dashboard/ProfitDecomp.vue') },
    { path: '/high-value',       component: () => import('@/views/dashboard/HighValue.vue') },
    { path: '/segment',          component: () => import('@/views/dashboard/SegmentTopMargin.vue') },
    { path: '/growth-potential', component: () => import('@/views/dashboard/GrowthPotential.vue') }
  ]
})
