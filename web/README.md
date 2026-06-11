# web/ — Vue 2 + ECharts 可视化看板

> 此目录为占位骨架。模块尚未生成具体代码；详细职责见 [../CLAUDE.md](../CLAUDE.md)。

## 看板板块

| 板块 | 数据来源 |
|---|---|
| 利润异常监测 | `phone_ads.profit_anomaly` |
| 经济指标波动 | `phone_ads.metric_trend` |
| 低贡献机型/渠道 | `phone_ads.low_contrib_model` / `low_contrib_channel` |
| 利润下滑归因（客单价/销量/成本/营销费用） | `phone_ads.profit_decomp` |
| 高价值机会机型 | `phone_ads.high_value_model` |
| 利润率优异细分市场 | `phone_ads.segment_top_margin` |
| 被低估的增长潜力点 | `phone_ads.growth_potential` |
| 实时告警栏 | SSE/WebSocket from `app/` |

## 配置

- 配置文件源：`config/web/.env`（Vue CLI `process.env.VUE_APP_*`）
  - 或 `config/web/config.js`（运行时全局配置，挂在 `window.__APP_CONFIG__`）
- 代码中通过 `src/config/index.js` 统一暴露宏变量（如 `API_BASE`、`SSE_URL`），业务组件只引用宏变量。

## TODO

- [ ] `package.json` + Vue CLI 脚手架
- [ ] `src/config/index.js`
- [ ] `src/api/*`
- [ ] `src/views/dashboard/*` + ECharts 配置
- [ ] `src/views/alert/*` + SSE 订阅
