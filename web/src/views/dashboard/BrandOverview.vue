<template>
  <div>
    <!-- 第 1 行：饼图 / 环图 + 雷达图 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="panel-card">
          <div class="panel-title"><span>品牌营收占比（饼图）</span></div>
          <EChart :option="pieOpt" :height="320" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="panel-card">
          <div class="panel-title"><span>品牌毛利占比（环图）</span></div>
          <EChart :option="doughnutOpt" :height="320" />
        </div>
      </el-col>
    </el-row>

    <!-- 第 2 行：雷达 + 漏斗 -->
    <el-row :gutter="16">
      <el-col :span="14">
        <div class="panel-card">
          <div class="panel-title"><span>Top 品牌多维雷达（5 维归一化）</span></div>
          <EChart :option="radarOpt" :height="380" />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="panel-card">
          <div class="panel-title"><span>品牌营收漏斗（按营收阶梯）</span></div>
          <EChart :option="funnelOpt" :height="380" />
        </div>
      </el-col>
    </el-row>

    <!-- 第 3 行：4 个仪表盘 -->
    <div class="panel-card">
      <div class="panel-title"><span>全局 KPI 仪表盘</span></div>
      <el-row :gutter="8">
        <el-col v-for="g in kpiGauges" :key="g.kpiCode" :span="6">
          <EChart :option="gaugeOpt(g)" :height="260" />
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtMoney } from '@/utils/format'

export default {
  name: 'BrandOverview',
  components: { EChart },
  data () {
    return { brands: [], kpiGauges: [] }
  },
  async mounted () {
    this.brands    = (await api.extBrandSummary()) || []
    this.kpiGauges = (await api.extKpiGauge()) || []
  },
  computed: {
    // 饼图：品牌营收占比
    pieOpt () {
      return {
        color: this.$cfg.chartPalette,
        tooltip: { trigger: 'item', valueFormatter: fmtMoney },
        legend: { type: 'scroll', bottom: 0 },
        series: [{
          name: '营收', type: 'pie', radius: ['0%', '60%'],
          center: ['50%', '45%'],
          data: this.brands.map(b => ({ name: b.brand, value: b.totalRevenue })),
          label: { formatter: '{b}\n{d}%' }
        }]
      }
    },
    // 环图：品牌毛利占比
    doughnutOpt () {
      return {
        color: this.$cfg.chartPalette,
        tooltip: { trigger: 'item', valueFormatter: fmtMoney },
        legend: { type: 'scroll', bottom: 0 },
        series: [{
          name: '毛利', type: 'pie',
          radius: ['40%', '65%'],
          center: ['50%', '45%'],
          data: this.brands.map(b => ({ name: b.brand, value: b.totalGrossProfit })),
          itemStyle: { borderColor: '#fff', borderWidth: 2 },
          label: { formatter: '{b}: {d}%' }
        }]
      }
    },
    // 雷达：Top 5 品牌 × 5 维（雷达字段已归一化）
    radarOpt () {
      const top = [...this.brands].sort((a, b) => (b.totalRevenue || 0) - (a.totalRevenue || 0)).slice(0, 5)
      return {
        color: this.$cfg.chartPalette,
        tooltip: {},
        legend: { type: 'scroll', bottom: 0, data: top.map(b => b.brand) },
        radar: {
          shape: 'polygon',
          indicator: [
            { name: '营收',     max: 1 },
            { name: '毛利率',   max: 1 },
            { name: '销量',     max: 1 },
            { name: '用户评价', max: 1 },
            { name: '低营销费率', max: 1 }
          ],
          radius: '60%'
        },
        series: [{
          type: 'radar',
          data: top.map(b => ({
            name: b.brand,
            value: [b.rRevenue, b.rMargin, b.rQty, b.rRating, b.rLowMarketing]
          })),
          areaStyle: { opacity: 0.18 }
        }]
      }
    },
    // 漏斗：品牌营收阶梯（降序）
    funnelOpt () {
      const sorted = [...this.brands].sort((a, b) => (b.totalRevenue || 0) - (a.totalRevenue || 0))
      return {
        color: this.$cfg.chartPalette,
        tooltip: { trigger: 'item', valueFormatter: fmtMoney },
        legend: { type: 'scroll', bottom: 0 },
        series: [{
          name: '品牌营收', type: 'funnel',
          left: '10%', width: '80%',
          sort: 'descending',
          label: { formatter: '{b}', position: 'inside' },
          data: sorted.map(b => ({ name: b.brand, value: b.totalRevenue }))
        }]
      }
    }
  },
  methods: {
    // 单个仪表盘 option
    gaugeOpt (g) {
      // 比率类：value 在 0~1，target 也在 0~1
      const isRatio = (g.rawUnit || '').includes('%')
      const value   = (g.kpiValue || 0) * (isRatio ? 100 : 1)
      const target  = (g.targetValue || 0) * (isRatio ? 100 : 1)
      const warn    = (g.warnValue || 0)   * (isRatio ? 100 : 1)
      // 颜色刻度：根据 target / warn 高低自动着色
      const targetIsHigh = target >= warn
      const stops = targetIsHigh
        ? [[warn / 100, '#F56C6C'], [target / 100, '#E6A23C'], [1, '#67C23A']]
        : [[target / 100, '#67C23A'], [warn / 100, '#E6A23C'], [1, '#F56C6C']]
      return {
        title: { text: g.kpiNameCn, left: 'center', bottom: 4, textStyle: { fontSize: 13 } },
        series: [{
          type: 'gauge', radius: '80%', min: 0, max: 100,
          axisLine: { lineStyle: { width: 16, color: stops } },
          pointer: { length: '60%', width: 4 },
          axisLabel: { show: false }, axisTick: { show: false }, splitLine: { length: 12 },
          detail: {
            valueAnimation: true, fontSize: 18, offsetCenter: [0, '30%'],
            formatter: v => v.toFixed(1) + (isRatio ? '%' : '')
          },
          data: [{ value }]
        }]
      }
    }
  }
}
</script>
