<template>
  <div id="app">
    <el-container class="app-shell">
      <el-header class="app-header">
        <div class="brand">{{ $cfg.appName }}</div>
        <div class="header-right">
          <el-tag :type="healthOk ? 'success' : 'danger'" size="small">
            后端 {{ healthOk ? '联通' : '未联通' }}
          </el-tag>
          <span class="time">{{ now }}</span>
        </div>
      </el-header>
      <el-container>
        <el-aside width="200px" class="app-aside">
          <el-menu :default-active="$route.path" router>
            <el-menu-item index="/overview">
              <i class="el-icon-data-board"></i><span>总览</span>
            </el-menu-item>
            <el-menu-item index="/profit-anomaly">
              <i class="el-icon-warning-outline"></i><span>利润异常</span>
            </el-menu-item>
            <el-menu-item index="/metric-trend">
              <i class="el-icon-data-line"></i><span>经济指标波动</span>
            </el-menu-item>
            <el-menu-item index="/low-contrib">
              <i class="el-icon-bottom"></i><span>低贡献机型/渠道</span>
            </el-menu-item>
            <el-menu-item index="/profit-decomp">
              <i class="el-icon-pie-chart"></i><span>利润下滑归因</span>
            </el-menu-item>
            <el-menu-item index="/high-value">
              <i class="el-icon-star-on"></i><span>高价值机会机型</span>
            </el-menu-item>
            <el-menu-item index="/segment">
              <i class="el-icon-medal"></i><span>利润率优异细分</span>
            </el-menu-item>
            <el-menu-item index="/growth-potential">
              <i class="el-icon-top"></i><span>增长潜力点</span>
            </el-menu-item>
          </el-menu>
        </el-aside>
        <el-main class="app-main">
          <router-view />
        </el-main>
        <el-aside width="320px" class="app-alert-aside">
          <AlertPanel />
        </el-aside>
      </el-container>
    </el-container>
  </div>
</template>

<script>
import AlertPanel from '@/views/alert/AlertPanel.vue'
import api from '@/api'

export default {
  name: 'App',
  components: { AlertPanel },
  data () {
    return { healthOk: false, now: '', timer: null }
  },
  mounted () {
    this.checkHealth()
    this.tickTime()
    this.timer = setInterval(this.tickTime, 1000)
  },
  beforeDestroy () { if (this.timer) clearInterval(this.timer) },
  methods: {
    async checkHealth () {
      try { await api.health(); this.healthOk = true } catch (e) { this.healthOk = false }
    },
    tickTime () {
      const d = new Date()
      const pad = (n) => String(n).padStart(2, '0')
      this.now = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
              + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    }
  }
}
</script>

<style lang="scss">
html, body, #app { height: 100%; margin: 0; padding: 0; }
.app-shell { height: 100vh; }
.app-header {
  background: #1f2d3d;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px !important;
  .brand { font-size: 18px; font-weight: 600; letter-spacing: 1px; }
  .header-right { display: flex; align-items: center; gap: 16px; .time { color: #c0c4cc; font-size: 13px; } }
}
.app-aside { background: #fff; border-right: 1px solid #ebeef5; }
.app-main { background: #f4f6f9; padding: 16px; overflow: auto; }
.app-alert-aside { background: #fff; border-left: 1px solid #ebeef5; }
.el-menu { border-right: none !important; }
.panel-card { background: #fff; border-radius: 4px; padding: 16px; margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.04); }
.panel-title { font-size: 14px; color: #303133; font-weight: 600; margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center; }
.chart-wrap { width: 100%; height: 320px; }
.chart-wrap-tall { width: 100%; height: 420px; }
</style>
