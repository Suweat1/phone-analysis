<template>
  <div ref="el" :style="{ width: '100%', height: height + 'px' }"></div>
</template>

<script>
// EChart 通用包装：传入 option，自动 init / resize / dispose
export default {
  name: 'EChart',
  props: {
    option: { type: Object, required: true },
    height: { type: Number, default: 320 }
  },
  data () { return { inst: null, ro: null } },
  mounted () {
    this.inst = this.$echarts.init(this.$refs.el)
    this.inst.setOption(this.option)
    this.ro = new ResizeObserver(() => this.inst && this.inst.resize())
    this.ro.observe(this.$refs.el)
  },
  watch: {
    option: {
      handler (v) { this.inst && this.inst.setOption(v, true) },
      deep: true
    }
  },
  beforeDestroy () {
    if (this.ro) this.ro.disconnect()
    if (this.inst) { this.inst.dispose(); this.inst = null }
  }
}
</script>
