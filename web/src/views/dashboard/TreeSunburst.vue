<template>
  <div>
    <div class="panel-card">
      <div class="panel-title">
        <span>品牌 → 机型 层级（Treemap）</span>
        <el-radio-group v-model="treemapMetric" size="mini" @change="$forceUpdate()">
          <el-radio-button label="totalRevenue">按营收</el-radio-button>
          <el-radio-button label="totalGrossProfit">按毛利</el-radio-button>
          <el-radio-button label="totalQty">按销量</el-radio-button>
        </el-radio-group>
      </div>
      <EChart :option="treemapOpt" :height="500" />
    </div>

    <div class="panel-card">
      <div class="panel-title"><span>品牌 → 机型 旭日图（Sunburst）</span></div>
      <EChart :option="sunburstOpt" :height="540" />
    </div>
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtMoney, fmtInt, fmtPct } from '@/utils/format'

export default {
  name: 'TreeSunburst',
  components: { EChart },
  data () { return { rows: [], treemapMetric: 'totalRevenue' } },
  async mounted () { this.rows = (await api.extBrandModelTree()) || [] },
  computed: {
    // 把扁平行聚合成 { brand → [models...] } 的两层树
    tree () {
      const map = {}
      for (const r of this.rows) {
        if (!map[r.brand]) map[r.brand] = []
        map[r.brand].push(r)
      }
      return Object.keys(map).map(brand => {
        const models = map[brand]
        const sum = (key) => models.reduce((acc, m) => acc + (m[key] || 0), 0)
        return {
          name: brand,
          children: models.map(m => ({
            name: m.model,
            value: m[this.treemapMetric],
            extra: m
          })),
          totalRevenue: sum('totalRevenue'),
          totalGrossProfit: sum('totalGrossProfit'),
          totalQty: sum('totalQty')
        }
      })
    },
    treemapOpt () {
      const metricLabel = { totalRevenue: '营收', totalGrossProfit: '毛利', totalQty: '销量' }[this.treemapMetric]
      return {
        color: this.$cfg.chartPalette,
        tooltip: {
          formatter: (p) => {
            const r = p.data.extra
            if (!r) return `${p.name}`   // 品牌节点
            return `${r.brand}·${r.model}<br/>` +
                   `营收: ${fmtMoney(r.totalRevenue)}<br/>` +
                   `毛利: ${fmtMoney(r.totalGrossProfit)}<br/>` +
                   `毛利率: ${fmtPct(r.grossMargin)}<br/>` +
                   `销量: ${fmtInt(r.totalQty)}`
          }
        },
        series: [{
          name: metricLabel, type: 'treemap',
          data: this.tree.map(b => ({
            name: b.name,
            children: b.children
          })),
          leafDepth: 2,
          breadcrumb: { show: true, bottom: 10 },
          levels: [
            { itemStyle: { borderColor: '#fff', borderWidth: 4, gapWidth: 4 } },
            { colorSaturation: [0.35, 0.7], itemStyle: { borderColor: '#fff', borderWidth: 1, gapWidth: 1 } }
          ],
          label: { show: true, formatter: '{b}' },
          upperLabel: { show: true, height: 24, fontSize: 12, color: '#fff' }
        }]
      }
    },
    sunburstOpt () {
      // 按品牌营收降序 + 每个品牌内按机型营收降序，旭日图本身有自旋
      return {
        color: this.$cfg.chartPalette,
        tooltip: {
          formatter: (p) => {
            const r = p.data.extra
            if (!r) return `${p.name}: ${fmtMoney(p.value)}`
            return `${r.brand}·${r.model}<br/>` +
                   `营收: ${fmtMoney(r.totalRevenue)}<br/>` +
                   `毛利: ${fmtMoney(r.totalGrossProfit)}`
          }
        },
        series: [{
          type: 'sunburst', radius: ['12%', '92%'],
          data: this.tree.map(b => ({
            name: b.name, value: b.totalRevenue,
            children: b.children.map(c => ({ ...c, value: c.extra.totalRevenue }))
          })),
          label: { rotate: 'radial', minAngle: 5 },
          levels: [
            {},
            { r0: '15%', r: '50%', itemStyle: { borderWidth: 2 }, label: { rotate: 'tangential' } },
            { r0: '50%', r: '88%', itemStyle: { borderWidth: 1 }, label: { align: 'right' } }
          ]
        }]
      }
    }
  }
}
</script>
