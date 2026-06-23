<template>
  <div>
    <!-- 箱线图 -->
    <div class="panel-card">
      <div class="panel-title"><span>各品牌客单价分布（箱线图）</span></div>
      <EChart :option="boxOpt" :height="420" />
    </div>

    <!-- 平行坐标 -->
    <div class="panel-card">
      <div class="panel-title"><span>高价值机型多维平行坐标</span></div>
      <EChart :option="parallelOpt" :height="420" />
    </div>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtMoney, fmtPct } from '@/utils/format'

export default {
  name: 'PriceDistribution',
  components: { EChart },
  data () { return { box: [], hv: [] } },
  async mounted () {
    this.box = (await api.extBrandPriceBox()) || []
    this.hv  = (await api.highValueModels()) || []
  },
  computed: {
    boxOpt () {
      const cats = this.box.map(b => b.brand)
      const data = this.box.map(b => [b.qMin, b.q1, b.qMedian, b.q3, b.qMax])
      const outliers = []
      this.box.forEach((b, i) => {
        if (!b.outliers) return
        b.outliers.split(',').filter(Boolean).forEach(v => outliers.push([i, Number(v)]))
      })
      return {
        color: this.$cfg.chartPalette,
        tooltip: {
          trigger: 'item',
          formatter: (p) => {
            if (p.seriesType === 'boxplot') {
              const b = this.box[p.dataIndex]
              return `${b.brand}<br/>最小: ${b.qMin}<br/>Q1: ${b.q1}<br/>中位: ${b.qMedian}<br/>Q3: ${b.q3}<br/>最大: ${b.qMax}`
            }
            return `${this.box[p.value[0]].brand} 离群点: ${p.value[1]}`
          }
        },
        grid: { left: 80, right: 40, top: 40, bottom: 80 },
        xAxis: { type: 'category', data: cats, axisLabel: { rotate: 30 } },
        yAxis: { type: 'value', name: '客单价（元）', scale: true },
        series: [
          {
            name: '客单价分位', type: 'boxplot', data,
            itemStyle: { color: '#9bb4e8', borderColor: '#3066BE' }
          },
          {
            name: '离群点', type: 'scatter', data: outliers,
            symbolSize: 6, itemStyle: { color: '#F56C6C' }
          }
        ]
      }
    },
    parallelOpt () {
      // 平行坐标：高价值机型按 5 个维度对比
      const data = this.hv.map(r => [
        r.totalRevenue,
        r.grossMargin,
        r.qtyGrowthRatio,
        r.avgUserRating || 0,
        r.opportunityScore,
        `${r.brand}·${r.model}`        // tooltip 用
      ])
      return {
        color: this.$cfg.chartPalette,
        tooltip: {
          formatter: (p) => {
            const v = p.value
            return `${v[5]}<br/>` +
                   `营收: ${fmtMoney(v[0])}<br/>` +
                   `毛利率: ${fmtPct(v[1])}<br/>` +
                   `销量增速: ${fmtPct(v[2])}<br/>` +
                   `用户评价: ${Number(v[3]).toFixed(2)}<br/>` +
                   `机会评分: ${Number(v[4]).toFixed(3)}`
          }
        },
        parallelAxis: [
          { dim: 0, name: '营收', axisLabel: { formatter: fmtMoney } },
          { dim: 1, name: '毛利率', axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } },
          { dim: 2, name: '销量增速', axisLabel: { formatter: v => (v * 100).toFixed(0) + '%' } },
          { dim: 3, name: '用户评价' },
          { dim: 4, name: '机会评分' }
        ],
        parallel: { left: 80, right: 120, bottom: 80, top: 40 },
        series: [{
          type: 'parallel',
          lineStyle: { width: 2, opacity: 0.65 },
          data
        }]
      }
    }
  }
}
</script>
