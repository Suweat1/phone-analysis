<template>
  <div class="panel-card">
    <div class="panel-title"><span>利润率优异细分市场（品牌 × 城市 × 年龄段 × 会员等级）</span></div>
    <EChart :option="opt" :height="380" />
    <el-table :data="rows" size="mini" stripe style="margin-top: 16px">
      <el-table-column prop="rankNo" label="排名" width="60" />
      <el-table-column prop="segmentLabelCn" label="细分市场" />
      <el-table-column label="毛利率" width="110">
        <template #default="{ row }">{{ fmtPct(row.grossMargin) }}</template>
      </el-table-column>
      <el-table-column label="毛利" width="120">
        <template #default="{ row }">{{ fmtMoney(row.totalGrossProfit) }}</template>
      </el-table-column>
      <el-table-column label="营收" width="120">
        <template #default="{ row }">{{ fmtMoney(row.totalRevenue) }}</template>
      </el-table-column>
      <el-table-column prop="orderCnt" label="订单数" width="100" />
    </el-table>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtMoney, fmtPct } from '@/utils/format'

export default {
  name: 'SegmentTopMargin',
  components: { EChart },
  data () { return { rows: [] } },
  computed: {
    opt () {
      const y = this.rows.map(r => r.segmentLabelCn).reverse()
      const d = this.rows.map(r => r.grossMargin).reverse()
      return {
        color: this.$cfg.chartPalette,
        grid: { left: 260, right: 60, top: 20, bottom: 30 },
        tooltip: { trigger: 'axis', valueFormatter: v => (v * 100).toFixed(2) + '%' },
        xAxis: { type: 'value', axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } },
        yAxis: { type: 'category', data: y, axisLabel: { fontSize: 11 } },
        series: [{
          name: '毛利率', type: 'bar', data: d,
          itemStyle: { color: '#67C23A', borderRadius: [0, 4, 4, 0] },
          label: { show: true, position: 'right', formatter: p => (p.value * 100).toFixed(1) + '%' }
        }]
      }
    }
  },
  async mounted () { this.rows = (await api.segmentTopMargins()) || [] },
  methods: { fmtMoney, fmtPct }
}
</script>
