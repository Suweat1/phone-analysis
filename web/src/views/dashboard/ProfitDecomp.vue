<template>
  <div class="panel-card">
    <div class="panel-title">
      <span>利润下滑归因 — 4 维度（客单价 / 销量 / 成本 / 营销）</span>
      <div>
        <el-select v-model="ym" size="mini" placeholder="月份" @change="load">
          <el-option v-for="m in months" :key="m" :label="m" :value="m" />
        </el-select>
      </div>
    </div>
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="sub-title">环比（vs 上月）</div>
        <EChart :option="optMom" :height="380" />
      </el-col>
      <el-col :span="12">
        <div class="sub-title">同比（vs 去年同月）</div>
        <EChart :option="optYoy" :height="380" />
      </el-col>
    </el-row>

    <el-table :data="flatRows" size="mini" stripe style="margin-top: 16px">
      <el-table-column prop="saleYm" label="月份" width="100" />
      <el-table-column prop="compareType" label="类型" width="80" />
      <el-table-column prop="baseYm" label="对照期" width="100" />
      <el-table-column prop="factorNameCn" label="因素" width="100" />
      <el-table-column label="贡献金额" width="140">
        <template #default="{ row }">{{ fmtMoney(row.contribution) }}</template>
      </el-table-column>
      <el-table-column label="贡献占比" width="120">
        <template #default="{ row }">{{ fmtPct(row.contributionPct) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtMoney, fmtPct } from '@/utils/format'

export default {
  name: 'ProfitDecomp',
  components: { EChart },
  data () { return { months: [], ym: null, mom: [], yoy: [] } },
  computed: {
    optMom () { return this.waterfallOpt(this.mom) },
    optYoy () { return this.waterfallOpt(this.yoy) },
    flatRows () { return [...this.mom, ...this.yoy] }
  },
  async mounted () {
    this.months = (await api.recentMonths()) || []
    this.ym = this.months[0] || null
    await this.load()
  },
  methods: {
    fmtMoney, fmtPct,
    async load () {
      const data = (await api.profitDecomp(this.ym)) || {}
      this.mom = data.mom || []
      this.yoy = data.yoy || []
    },
    waterfallOpt (rows) {
      // 简化版瀑布：直接柱状图（正/负不同色），便于看哪一项拉跨毛利
      const sorted = [...rows].sort((a, b) => a.factor.localeCompare(b.factor))
      const x = sorted.map(r => r.factorNameCn)
      const v = sorted.map(r => r.contribution)
      return {
        color: this.$cfg.chartPalette,
        grid: { left: 70, right: 30, top: 20, bottom: 30 },
        tooltip: { trigger: 'axis', valueFormatter: fmtMoney },
        xAxis: { type: 'category', data: x },
        yAxis: { type: 'value' },
        series: [{
          name: '毛利贡献', type: 'bar', data: v,
          itemStyle: { color: (p) => p.value >= 0 ? '#67C23A' : '#F56C6C' },
          label: { show: true, position: 'top', formatter: (p) => fmtMoney(p.value) }
        }]
      }
    }
  }
}
</script>

<style scoped>
.sub-title { font-size: 13px; color: #606266; margin-bottom: 8px; padding-left: 8px; }
</style>
