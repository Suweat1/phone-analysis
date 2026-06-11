<template>
  <div class="panel-card">
    <div class="panel-title"><span>增长潜力点（机型 × 月 × 渠道）</span></div>
    <EChart :option="opt" :height="420" />
    <el-table :data="rows" size="mini" stripe style="margin-top: 16px">
      <el-table-column prop="rankNo" label="排名" width="60" />
      <el-table-column prop="brand" label="品牌" width="100" />
      <el-table-column prop="model" label="机型" />
      <el-table-column prop="saleYm" label="月份" width="100" />
      <el-table-column prop="promotion" label="渠道" width="100" />
      <el-table-column label="销量增速" width="120">
        <template #default="{ row }">{{ fmtPct(row.qtyGrowthRatio) }}</template>
      </el-table-column>
      <el-table-column label="毛利率" width="100">
        <template #default="{ row }">{{ fmtPct(row.grossMargin) }}</template>
      </el-table-column>
      <el-table-column label="营销费率" width="120">
        <template #default="{ row }">{{ fmtPct(row.marketingRatio) }}</template>
      </el-table-column>
      <el-table-column label="潜力评分" width="100">
        <template #default="{ row }">{{ fmtScore(row.potentialScore) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtPct, fmtScore } from '@/utils/format'

export default {
  name: 'GrowthPotential',
  components: { EChart },
  data () { return { rows: [] } },
  computed: {
    opt () {
      // 散点图：x = 销量增速，y = 毛利率，size = 潜力评分，
      // 直观看到「右上区域 + 大圆」= 高潜力点
      return {
        color: this.$cfg.chartPalette,
        grid: { left: 80, right: 40, top: 30, bottom: 60 },
        tooltip: {
          trigger: 'item',
          formatter: (p) => {
            const r = this.rows[p.dataIndex]
            return `${r.brand}·${r.model}<br/>` +
                   `${r.saleYm} · ${r.promotion}<br/>` +
                   `销量增速 ${fmtPct(r.qtyGrowthRatio)}<br/>` +
                   `毛利率 ${fmtPct(r.grossMargin)}<br/>` +
                   `营销费率 ${fmtPct(r.marketingRatio)}<br/>` +
                   `潜力评分 ${fmtScore(r.potentialScore)}`
          }
        },
        xAxis: { type: 'value', name: '销量环比增速', axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } },
        yAxis: { type: 'value', name: '毛利率',     axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } },
        series: [{
          type: 'scatter',
          symbolSize: (v) => Math.max(10, v[2] * 60),
          data: this.rows.map(r => [r.qtyGrowthRatio, r.grossMargin, r.potentialScore]),
          itemStyle: { color: '#FAC858', opacity: 0.8, borderColor: '#fff' },
          label: {
            show: true, position: 'top',
            formatter: (p) => this.rows[p.dataIndex].model, fontSize: 10
          }
        }]
      }
    }
  },
  async mounted () { this.rows = (await api.growthPotentials()) || [] },
  methods: { fmtPct, fmtScore }
}
</script>
