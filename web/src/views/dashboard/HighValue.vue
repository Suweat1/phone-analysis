<template>
  <div class="panel-card">
    <div class="panel-title"><span>高价值机会机型 TopN（综合机会评分）</span></div>
    <EChart :option="opt" :height="420" />
    <el-table :data="rows" size="mini" stripe style="margin-top: 16px">
      <el-table-column prop="rankNo" label="排名" width="60" />
      <el-table-column prop="brand" label="品牌" width="100" />
      <el-table-column prop="model" label="机型" />
      <el-table-column label="营收" width="120">
        <template #default="{ row }">{{ fmtMoney(row.totalRevenue) }}</template>
      </el-table-column>
      <el-table-column label="毛利率" width="100">
        <template #default="{ row }">{{ fmtPct(row.grossMargin) }}</template>
      </el-table-column>
      <el-table-column label="销量增速" width="100">
        <template #default="{ row }">{{ fmtPct(row.qtyGrowthRatio) }}</template>
      </el-table-column>
      <el-table-column label="平均评价" width="100">
        <template #default="{ row }">{{ row.avgUserRating != null ? row.avgUserRating.toFixed(2) : '-' }}</template>
      </el-table-column>
      <el-table-column label="机会评分" width="100">
        <template #default="{ row }">{{ fmtScore(row.opportunityScore) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtMoney, fmtPct, fmtScore } from '@/utils/format'

export default {
  name: 'HighValue',
  components: { EChart },
  data () { return { rows: [] } },
  computed: {
    opt () {
      // 散点图：x=毛利率，y=营收，size=机会评分，颜色=销量增速
      return {
        color: this.$cfg.chartPalette,
        grid: { left: 80, right: 40, top: 30, bottom: 50 },
        tooltip: {
          trigger: 'item',
          formatter: (p) => {
            const r = this.rows[p.dataIndex]
            return `${r.brand}·${r.model}<br/>毛利率 ${fmtPct(r.grossMargin)}<br/>`
                 + `营收 ${fmtMoney(r.totalRevenue)}<br/>`
                 + `销量增速 ${fmtPct(r.qtyGrowthRatio)}<br/>`
                 + `机会评分 ${fmtScore(r.opportunityScore)}`
          }
        },
        xAxis: { type: 'value', name: '毛利率', axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } },
        yAxis: { type: 'value', name: '营收', axisLabel: { formatter: fmtMoney } },
        series: [{
          type: 'scatter',
          symbolSize: (val) => Math.max(12, val[2] * 60),
          data: this.rows.map(r => [r.grossMargin, r.totalRevenue, r.opportunityScore]),
          itemStyle: { color: '#5470C6', opacity: 0.75 },
          label: {
            show: true, position: 'right',
            formatter: (p) => this.rows[p.dataIndex].model, fontSize: 10
          }
        }]
      }
    }
  },
  async mounted () { this.rows = (await api.highValueModels()) || [] },
  methods: { fmtMoney, fmtPct, fmtScore }
}
</script>
