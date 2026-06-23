<template>
  <div>
    <!-- 日历热力图 -->
    <div class="panel-card">
      <div class="panel-title">
        <span>日营收日历热力图</span>
        <span style="font-size:12px;color:#909399">颜色越深 = 营收越高，灰格为无数据</span>
      </div>
      <EChart :option="calendarOpt" :height="320" />
    </div>

    <!-- 品牌 × 月份 矩形热力 -->
    <div class="panel-card">
      <div class="panel-title">
        <span>品牌 × 月份 毛利率矩形热力</span>
        <span style="font-size:12px;color:#909399">每格代表 (品牌, 年月) 的毛利率</span>
      </div>
      <EChart :option="brandMonthOpt" :height="420" />
    </div>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtMoney, fmtPct } from '@/utils/format'

export default {
  name: 'HeatmapAnalysis',
  components: { EChart },
  data () { return { calRows: [], brandMonth: [] } },
  async mounted () {
    this.calRows    = (await api.extCalendarHeat()) || []
    this.brandMonth = (await api.extBrandMonthHeat()) || []
  },
  computed: {
    calendarOpt () {
      // 日历热力图：用 calendar coordinate + heatmap series
      const data = this.calRows.map(r => [r.saleDate, r.totalRevenue])
      const values = data.map(d => d[1]).filter(v => v != null)
      const max = values.length ? Math.max(...values) : 0
      // 推断年份范围
      const years = Array.from(new Set(this.calRows.map(r => r.saleDate && r.saleDate.slice(0, 4)).filter(Boolean))).sort()
      const range = years.length === 1 ? years[0] : [years[0], years[years.length - 1]]
      return {
        tooltip: {
          formatter: (p) => `${p.value[0]}<br/>营收: ${fmtMoney(p.value[1])}`
        },
        visualMap: {
          min: 0, max,
          calculable: true, orient: 'horizontal',
          left: 'center', top: 0,
          inRange: { color: ['#e0f3f8', '#74add1', '#4575b4', '#313695'] }
        },
        calendar: {
          top: 60, left: 40, right: 40, cellSize: ['auto', 14],
          range,
          itemStyle: { borderWidth: 0.5, borderColor: '#fff' },
          yearLabel: { show: years.length > 1 }
        },
        series: [{
          type: 'heatmap', coordinateSystem: 'calendar', data
        }]
      }
    },
    brandMonthOpt () {
      const brands = Array.from(new Set(this.brandMonth.map(r => r.brand))).sort()
      const months = Array.from(new Set(this.brandMonth.map(r => r.saleYm))).sort()
      // heatmap series 接受 [xIdx, yIdx, value, ...原始行] 这种数组，最后一项放原始行方便 tooltip
      const data = this.brandMonth.map(r => [months.indexOf(r.saleYm), brands.indexOf(r.brand), r.grossMargin, r])
      const values = data.map(d => d[2]).filter(v => v != null)
      const min = values.length ? Math.min(...values) : 0
      const max = values.length ? Math.max(...values) : 1
      return {
        tooltip: {
          formatter: (p) => {
            const r = p.value[3]
            return `${r.brand} · ${r.saleYm}<br/>毛利率: ${fmtPct(r.grossMargin)}<br/>营收: ${fmtMoney(r.totalRevenue)}`
          }
        },
        grid: { left: 80, right: 40, top: 60, bottom: 80 },
        xAxis: { type: 'category', data: months, axisLabel: { rotate: 45 } },
        yAxis: { type: 'category', data: brands },
        visualMap: {
          min, max, calculable: true, orient: 'horizontal',
          left: 'center', top: 10,
          formatter: v => (v * 100).toFixed(1) + '%',
          inRange: { color: ['#F56C6C', '#FAC858', '#67C23A'] }
        },
        series: [{
          name: '毛利率', type: 'heatmap', data,
          itemStyle: { borderColor: '#fff', borderWidth: 1 }
        }]
      }
    }
  }
}
</script>
