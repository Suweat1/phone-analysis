# phone-analysis

手机销售数据大数据分析项目 —— 端到端从原始 Excel 到可视化看板。

## 仓库结构

```
phone-analysis/
├── app/             Spring Boot 2.7.x REST + Kafka 生产者 + 告警推送
├── spark-etl/       Scala 2.12 + Spark SQL 3.3.1 的 ETL / Streaming 作业
├── web/             Vue 2.x + ECharts 可视化看板
├── config/          各组件 + 三模块的项目版配置（部署时 cp 到目标路径）
├── data/            源数据 + 预处理产物
├── scripts/         预处理 / 启停脚本
├── docs/deploy/     单节点伪分布式部署文档（00~11）
└── pom.xml          Maven 父 pom（聚合 app + spark-etl）
```

## 关键文档

- 项目宪法：[CLAUDE.md](./CLAUDE.md)
- 部署阅读顺序：[docs/deploy/README.md](./docs/deploy/README.md)

## 开发模式

- **本机（macOS）**：仅写代码、git push，没有运行时（不要在本机跑 mvn / spark-submit / npm run）。
- **运行机（Ubuntu 20.04 虚拟机）**：所有大数据组件 + 应用在 `/opt/bigdata/` 下伪分布式运行；通过 `git pull` 同步代码。

## 数据流

```
本机 Excel → pandas 预处理 → parquet (git) →
运行机 HDFS → Spark SQL 分层 (ods/dwd/dws/ads) →
MySQL/Redis → Spring Boot → Vue + ECharts 看板
```
