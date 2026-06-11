<template>
  <div>
    <div class="panel-card">
      <div class="panel-title"><span>低贡献机型 TopN</span></div>
      <EChart :option="modelOpt" :height="320" />
      <el-table :data="models" size="mini" stripe style="margin-top: 10px">
        <el-table-column prop="rankNo" label="排名" width="60" />
        <el-table-column prop="brand" label="品牌" width="100" />
        <el-table-column prop="model" label="机型" />
        <el-table-column label="营收" width="120">
          <template #default="{ row }">{{ fmtMoney(row.totalRevenue) }}</template>
        </el-table-column>
        <el-table-column label="毛利" width="120">
          <template #default="{ row }">{{ fmtMoney(row.totalGrossProfit) }}</template>
        </el-table-column>
        <el-table-column label="毛利率" width="100">
          <template #default="{ row }">{{ fmtPct(row.grossMargin) }}</template>
        </el-table-column>
        <el-table-column label="贡献占比" width="120">
          <template #default="{ row }">{{ fmtPct(row.contributionRatio) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <div class="panel-card">
      <div class="panel-title"><span>低贡献渠道 TopN</span></div>
      <EChart :option="channelOpt" :height="320" />
      <el-table :data="channels" size="mini" stripe style="margin-top: 10px">
        <el-table-column prop="rankNo" label="排名" width="60" />
        <el-table-column prop="promotion" label="渠道" width="120" />
        <el-table-column label="营收" width="120">
          <template #default="{ row }">{{ fmtMoney(row.totalRevenue) }}</template>
        </el-table-column>
        <el-table-column label="毛利" width="120">
          <template #default="{ row }">{{ fmtMoney(row.totalGrossProfit) }}</template>
        </el-table-column>
        <el-table-column label="毛利率" width="100">
          <template #default="{ row }">{{ fmtPct(row.grossMargin) }}</template>
        </el-table-column>
        <el-table-column label="营销费率" width="120">
          <template #default="{ row }">{{ fmtPct(row.marketingRatio) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtMoney, fmtPct } from '@/utils/format'

export default {
  name: 'LowContrib',
  components: { EChart },
  data () { return { models: [], channels: [] } },
  computed: {
    modelOpt ()   { return this.bar(this.models,   d => `${d.brand}·${d.model}`, 'totalGrossProfit') },
    channelOpt () { return this.bar(this.channels, d => d.promotion,             'totalGrossProfit') }
  },
  async mounted () {
    this.models   = (await api.lowContribModels()) || []
    this.channels = (await api.lowContribChannels()) || []
  },
  methods: {
    fmtMoney, fmtPct,
    bar (rows, nameFn, key) {
      const y = rows.map(nameFn).reverse()
      const d = rows.map(r => r[key]).reverse()
      return {
        color: this.$cfg.chartPalette,
        grid: { left: 180, right: 40, top: 20, bottom: 30 },
        tooltip: { trigger: 'axis', valueFormatter: fmtMoney },
        xAxis: { type: 'value' },
        yAxis: { type: 'category', data: y, axisLabel: { fontSize: 11 } },
        series: [{ name: '毛利', type: 'bar', data: d, itemStyle: { color: '#EE6666', borderRadius: [0, 4, 4, 0] } }]
      }
    }
  }
}
</script>
