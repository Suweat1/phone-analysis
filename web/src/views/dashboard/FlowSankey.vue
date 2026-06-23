<template>
  <div class="panel-card">
    <div class="panel-title">
      <span>资金流向桑基图（渠道 → 品牌 → 年龄段，按毛利）</span>
      <span style="font-size:12px;color:#909399">条带宽度代表毛利金额，悬停可看精确数值</span>
    </div>
    <EChart :option="sankeyOpt" :height="600" />
  </div>
</template>

<script>
import EChart from '@/components/EChart.vue'
import api from '@/api'
import { fmtMoney } from '@/utils/format'

export default {
  name: 'FlowSankey',
  components: { EChart },
  data () { return { edges: [] } },
  async mounted () { this.edges = (await api.extSalesSankey()) || [] },
  computed: {
    sankeyOpt () {
      // 节点：渠道 / 品牌 / 年龄段（用前缀区分，避免重名碰撞）
      const nodeSet = new Set()
      const links = []
      for (const e of this.edges) {
        // layerIdx=0: 渠道→品牌, layerIdx=1: 品牌→年龄段
        const sPrefix = e.layerIdx === 0 ? '渠道:' : '品牌:'
        const tPrefix = e.layerIdx === 0 ? '品牌:' : '年龄段:'
        const s = sPrefix + e.source
        const t = tPrefix + e.target
        nodeSet.add(s); nodeSet.add(t)
        links.push({ source: s, target: t, value: e.value })
      }
      const nodes = Array.from(nodeSet).map(name => ({ name }))
      return {
        color: this.$cfg.chartPalette,
        tooltip: {
          trigger: 'item',
          formatter: (p) => {
            if (p.dataType === 'edge') {
              return `${p.data.source} → ${p.data.target}<br/>毛利: ${fmtMoney(p.data.value)}`
            }
            return p.data.name
          }
        },
        series: [{
          type: 'sankey',
          data: nodes,
          links,
          emphasis: { focus: 'adjacency' },
          nodeAlign: 'justify',
          lineStyle: { color: 'gradient', curveness: 0.5, opacity: 0.55 },
          label: { fontSize: 11 }
        }]
      }
    }
  }
}
</script>
