<template>
  <div class="panel-card">
    <div class="panel-title">
      <span>经济指标波动</span>
      <div>
        <el-select v-model="code" size="mini" placeholder="指标" @change="load">
          <el-option v-for="m in metrics" :key="m.metricCode" :label="m.metricNameCn" :value="m.metricCode" />
        </el-select>
        <el-date-picker
          v-model="range" type="daterange" size="mini" value-format="yyyy-MM-dd" style="margin-left: 8px"
          range-separator="到" start-placeholder="开始日期" end-placeholder="结束日期"
          @change="load"
        />
      </div>
    </div>
    <EChart :option="opt" :height="420" />
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'

export default {
  name: 'MetricTrend',
  components: { EChart },
  data () { return { metrics: [], code: 'revenue', range: [], rows: [] } },
  computed: {
    opt () {
      const x = this.rows.map(r => r.saleDate)
      const v = this.rows.map(r => r.metricValue)
      const m = this.rows.map(r => r.momRatio)
      const y = this.rows.map(r => r.yoyRatio)
      return {
        color: this.$cfg.chartPalette,
        legend: { data: ['指标值', '环比', '同比'], top: 0 },
        grid: { left: 70, right: 60, top: 40, bottom: 50 },
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: x, axisLabel: { rotate: 45 } },
        yAxis: [
          { type: 'value', name: '指标值', position: 'left' },
          { type: 'value', name: '%', position: 'right', axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } }
        ],
        series: [
          { name: '指标值', type: 'line', data: v, smooth: true, yAxisIndex: 0 },
          { name: '环比',   type: 'line', data: m, smooth: true, yAxisIndex: 1, lineStyle: { type: 'dashed' } },
          { name: '同比',   type: 'line', data: y, smooth: true, yAxisIndex: 1, lineStyle: { type: 'dotted' } }
        ]
      }
    }
  },
  async mounted () {
    this.metrics = (await api.metricCodes()) || []
    if (this.metrics.length && !this.metrics.some(m => m.metricCode === this.code)) {
      this.code = this.metrics[0].metricCode
    }
    await this.load()
  },
  methods: {
    async load () {
      const [from, to] = this.range || []
      this.rows = (await api.metricTrend(this.code, from, to)) || []
    }
  }
}
</script>
