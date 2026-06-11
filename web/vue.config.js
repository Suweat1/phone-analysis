// vue.config.js — Vue CLI 5 配置
// 真实参数从 .env 读（VUE_APP_*），所有"宏变量"集中由 src/config/index.js 暴露给业务代码

const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  publicPath: process.env.VUE_APP_PUBLIC_PATH || '/',
  outputDir: 'dist',
  assetsDir: 'static',
  productionSourceMap: false,
  devServer: {
    host: '0.0.0.0',
    port: parseInt(process.env.VUE_APP_DEV_PORT || '8081', 10),
    proxy: {
      // 把 /api/* 转发到 SpringBoot，避免本机开发 CORS / SSE 跨域问题
      '/api': {
        target: process.env.VUE_APP_API_PROXY || 'http://phone-analysis:8080',
        changeOrigin: true,
        ws: true
      }
    }
  },
  configureWebpack: {
    performance: { hints: false }
  }
})
