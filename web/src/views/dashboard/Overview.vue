<template>
  <div>
    <div class="overview-grid">
      <div class="panel-card">
        <div class="panel-title"><span>低贡献机型 TopN</span></div>
        <EChart :option="lowModelOpt" :height="280" />
      </div>
      <div class="panel-card">
        <div class="panel-title"><span>低贡献渠道 TopN</span></div>
        <EChart :option="lowChannelOpt" :height="280" />
      </div>
      <div class="panel-card">
        <div class="panel-title"><span>高价值机型 TopN</span></div>
        <EChart :option="highValueOpt" :height="280" />
      </div>
      <div class="panel-card">
        <div class="panel-title"><span>利润率优异细分市场</span></div>
        <EChart :option="segmentOpt" :height="280" />
      </div>
      <div class="panel-card span-2">
        <div class="panel-title"><span>增长潜力点（机型 × 渠道 × 月）</span></div>
        <el-table :data="potentialList" size="mini" stripe>
          <el-table-column prop="rankNo" label="排名" width="60" />
          <el-table-column prop="brand" label="品牌" width="80" />
          <el-table-column prop="model" label="机型" />
          <el-table-column prop="saleYm" label="月份" width="90" />
          <el-table-column prop="promotion" label="渠道" width="100" />
          <el-table-column label="毛利率" width="90">
            <template #default="{ row }">{{ fmtPct(row.grossMargin) }}</template>
          </el-table-column>
          <el-table-column label="销量增速" width="100">
            <template #default="{ row }">{{ fmtPct(row.qtyGrowthRatio) }}</template>
          </el-table-column>
          <el-table-column label="潜力评分" width="100">
            <template #default="{ row }">{{ fmtScore(row.potentialScore) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtMoney, fmtPct, fmtScore } from '@/utils/format'

export default {
  name: 'Overview',
  components: { EChart },
  data () {
    return {
      lowModels: [], lowChannels: [], highValues: [], segments: [], potentialList: []
    }
  },
  computed: {
    lowModelOpt   () { return this.barOpt(this.lowModels,    d => `${d.brand}·${d.model}`, 'totalGrossProfit', '毛利') },
    lowChannelOpt () { return this.barOpt(this.lowChannels,  d => d.promotion,             'totalGrossProfit', '毛利') },
    highValueOpt  () { return this.barOpt(this.highValues,   d => `${d.brand}·${d.model}`, 'opportunityScore', '机会评分', false) },
    segmentOpt    () { return this.barOpt(this.segments,     d => d.segmentLabelCn,        'grossMargin',      '毛利率', false, v => (v * 100).toFixed(1) + '%') }
  },
  async mounted () {
    const data = await api.overview()
    this.lowModels     = data.lowContribModels || []
    this.lowChannels   = data.lowContribChannels || []
    this.highValues    = data.highValueModels || []
    this.segments      = data.segmentTopMargins || []
    this.potentialList = data.growthPotentials || []
  },
  methods: {
    fmtMoney, fmtPct, fmtScore,
    barOpt (rows, nameFn, valueKey, seriesName, money = true, fmt = null) {
      const yData = rows.map(nameFn).reverse()
      const data  = rows.map(r => r[valueKey]).reverse()
      return {
        color: this.$cfg.chartPalette,
        grid: { left: 160, right: 30, top: 20, bottom: 30 },
        tooltip: {
          trigger: 'axis',
          formatter: (p) => `${p[0].name}<br/>${seriesName}: ${
            fmt ? fmt(p[0].value) : (money ? fmtMoney(p[0].value) : Number(p[0].value).toFixed(3))
          }`
        },
        xAxis: { type: 'value', axisLabel: { color: '#909399' } },
        yAxis: { type: 'category', data: yData, axisLabel: { color: '#606266', fontSize: 11 } },
        series: [{
          name: seriesName, type: 'bar', data,
          itemStyle: { borderRadius: [0, 4, 4, 0] }
        }]
      }
    }
  }
}
</script>

<style scoped>
.overview-grid {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px;
}
.span-2 { grid-column: span 2; }
</style>
