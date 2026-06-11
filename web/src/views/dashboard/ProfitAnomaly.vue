<template>
  <div class="panel-card">
    <div class="panel-title">
      <span>利润异常监测 — 日毛利率 vs 30 日滚动均值</span>
      <el-date-picker
        v-model="range" type="daterange" size="mini" value-format="yyyy-MM-dd"
        range-separator="到" start-placeholder="开始日期" end-placeholder="结束日期"
        @change="load"
      />
    </div>
    <EChart :option="chartOpt" :height="400" />

    <el-divider content-position="left">异常日列表</el-divider>
    <el-table :data="anomalies" size="mini" stripe>
      <el-table-column prop="saleDate" label="日期" width="120" />
      <el-table-column label="毛利率" width="100">
        <template #default="{ row }">{{ fmtPct(row.grossMargin) }}</template>
      </el-table-column>
      <el-table-column label="30 日均值" width="120">
        <template #default="{ row }">{{ fmtPct(row.rollingMargin30d) }}</template>
      </el-table-column>
      <el-table-column label="偏差" width="100">
        <template #default="{ row }">{{ fmtPct(row.deviationRatio) }}</template>
      </el-table-column>
      <el-table-column label="级别" width="100">
        <template #default="{ row }">
          <el-tag size="mini" :type="tagType(row.anomalyLevel)">{{ row.anomalyLevel }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtPct } from '@/utils/format'

export default {
  name: 'ProfitAnomaly',
  components: { EChart },
  data () { return { range: [], rows: [] } },
  computed: {
    anomalies () { return this.rows.filter(r => r.isAnomaly) },
    chartOpt () {
      const dates = this.rows.map(r => r.saleDate)
      return {
        color: this.$cfg.chartPalette,
        legend: { data: ['毛利率', '30 日均值', '异常点'], top: 0 },
        grid: { left: 60, right: 30, top: 40, bottom: 50 },
        tooltip: { trigger: 'axis', valueFormatter: (v) => v == null ? '-' : (v * 100).toFixed(2) + '%' },
        xAxis: { type: 'category', data: dates, axisLabel: { rotate: 45 } },
        yAxis: { type: 'value', axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } },
        series: [
          { name: '毛利率', type: 'line', data: this.rows.map(r => r.grossMargin), smooth: true },
          { name: '30 日均值', type: 'line', data: this.rows.map(r => r.rollingMargin30d), smooth: true, lineStyle: { type: 'dashed' } },
          {
            name: '异常点', type: 'scatter', symbolSize: 12,
            data: this.rows.map(r => r.isAnomaly ? r.grossMargin : null),
            itemStyle: { color: '#F56C6C' }
          }
        ]
      }
    }
  },
  mounted () { this.load() },
  methods: {
    fmtPct,
    async load () {
      const [from, to] = this.range || []
      this.rows = (await api.profitAnomaly(from, to)) || []
    },
    tagType (lv) {
      return ({ high: 'danger', mid: 'warning', low: 'info', normal: 'success' }[lv]) || 'info'
    }
  }
}
</script>
