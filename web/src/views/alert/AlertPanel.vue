<template>
  <div class="alert-panel">
    <div class="panel-title">
      <span>实时告警</span>
      <el-tag size="mini" :type="connected ? 'success' : 'info'">
        {{ connected ? '已连接' : '未连接' }}
      </el-tag>
    </div>
    <div class="alert-list">
      <div v-if="!list.length" class="alert-empty">暂无告警</div>
      <el-card
        v-for="a in list"
        :key="a.alertId"
        class="alert-item"
        shadow="never"
        :body-style="{ padding: '10px 12px' }"
      >
        <div class="alert-row1">
          <el-tag size="mini" :color="colorOf(a.level)" effect="dark" class="level-tag">
            {{ (a.level || '').toUpperCase() }}
          </el-tag>
          <span class="alert-title">{{ a.title }}</span>
        </div>
        <div class="alert-row2">{{ a.content }}</div>
        <div class="alert-row3">
          <span>{{ formatTime(a.triggeredAt) }}</span>
          <span v-if="a.deviation != null">偏差 {{ (a.deviation * 100).toFixed(1) }}%</span>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script>
import api from '@/api'
import { AlertStream } from '@/utils/sse'

export default {
  name: 'AlertPanel',
  data () {
    return { list: [], connected: false, stream: null }
  },
  async mounted () {
    try { this.list = (await api.alertRecent()) || [] } catch (e) { /* ignore */ }
    this.stream = new AlertStream((msg) => {
      this.list.unshift(msg)
      if (this.list.length > this.$cfg.alertMaxHistory) this.list.pop()
    })
    this.stream.open()
    this.connected = true
  },
  beforeDestroy () { if (this.stream) this.stream.close() },
  methods: {
    colorOf (level) {
      return this.$cfg.alertColor[level] || this.$cfg.alertColor.low
    },
    formatTime (t) {
      if (!t) return ''
      return String(t).replace('T', ' ').slice(0, 19)
    }
  }
}
</script>

<style lang="scss" scoped>
.alert-panel { padding: 16px; height: 100%; display: flex; flex-direction: column; }
.alert-list { flex: 1; overflow-y: auto; }
.alert-empty { color: #909399; font-size: 13px; text-align: center; padding-top: 40px; }
.alert-item { margin-bottom: 8px; border-left: 3px solid #909399; }
.alert-row1 { display: flex; align-items: center; gap: 6px; }
.alert-row1 .level-tag { color: #fff !important; border: none; }
.alert-title { font-weight: 600; color: #303133; font-size: 13px; }
.alert-row2 { color: #606266; font-size: 12px; margin: 6px 0 4px; line-height: 1.5; }
.alert-row3 { color: #909399; font-size: 11px; display: flex; justify-content: space-between; }
</style>
