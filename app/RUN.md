# app 模块运行手册 & API 索引

> 假设运行机已按 [docs/deploy/](../docs/deploy/) 完成 MySQL/Redis/Kafka/Hive 部署；
> Spark ETL 至少跑过一次 `InitSchemaJob + PipelineJob`（详见 [../spark-etl/RUN.md](../spark-etl/RUN.md)）。

## 1. 构建（运行机）

```bash
cd ~/phone-analysis
mvn -DskipTests -pl app -am clean package
ls app/target/phone-analysis-app.jar
```

## 2. 启动

```bash
mkdir -p /opt/bigdata/log/app
nohup java -jar ~/phone-analysis/app/target/phone-analysis-app.jar \
  --spring.config.location=file:${HOME}/phone-analysis/config/app/application.yml \
  > /opt/bigdata/log/app/app.log 2>&1 &
```

健康检查：

```bash
curl http://phone-analysis:8080/api/health
```

## 3. REST API

所有 URL 已带 `server.servlet.context-path=/api` 前缀。

### 3.1 健康
| Method | URL | 说明 |
|---|---|---|
| GET | `/api/health` | 启动自检（Kafka/Hive URL、SSE 订阅数等） |

### 3.2 字段中英映射
| Method | URL | 参数 | 说明 |
|---|---|---|---|
| GET | `/api/dict/columns` | — | 全量映射 Map<英文, ColumnDict> |
| GET | `/api/dict/cn?en=brand` | en | 单查中文名 |
| POST | `/api/dict/refresh` | — | 强制从 `ads_column_dict` 重载（ETL 跑完后调） |

### 3.3 看板（8 个板块 + 1 总览）
| Method | URL | 参数 | 说明 |
|---|---|---|---|
| GET | `/api/dashboard/overview` | — | 首屏综合（含 5 张 TopN 表） |
| GET | `/api/dashboard/profit-anomaly` | `from?` `to?` `yyyy-MM-dd` | 利润异常日序列 |
| GET | `/api/dashboard/metric/codes` | — | 列出所有可用 metric_code |
| GET | `/api/dashboard/metric/trend` | `code` `from?` `to?` | 单指标日序列 + MoM/YoY |
| GET | `/api/dashboard/low-contrib/model` | — | 低贡献机型 TopN |
| GET | `/api/dashboard/low-contrib/channel` | — | 低贡献渠道 TopN |
| GET | `/api/dashboard/profit-decomp` | `ym?` `yyyy-MM` | 利润下滑归因（含 mom / yoy 两组） |
| GET | `/api/dashboard/profit-decomp/months` | — | 最近 24 个月 yyyy-MM |
| GET | `/api/dashboard/high-value/model` | — | 高价值机型 TopN |
| GET | `/api/dashboard/segment/top-margin` | — | 利润率优异细分市场 TopN |
| GET | `/api/dashboard/growth-potential` | — | 增长潜力点 TopN |

### 3.4 实时告警
| Method | URL | 说明 |
|---|---|---|
| GET | `/api/alert/stream` | SSE 端点。Vue: `new EventSource('/api/alert/stream')`，监听 `alert` 事件 |
| GET | `/api/alert/recent` | 最近 100 条历史，用于初次进入页面 |
| GET | `/api/alert/_status` | 当前 SSE 订阅数 / 历史条数 |

## 4. 数据流

```
EventSimulator (Scheduler) ──KafkaProducer──▶ Kafka phone_raw
                              │
                              └─ 5% 概率 ──▶ Kafka phone_alert ──▶ AlertConsumer ──▶ AlertService
                                                       │
                                                       └ Spark Streaming 也会写这个 topic
                                                       │
                                                       ▼
                                            SSE  /api/alert/stream  ──▶ Vue 告警栏
                                            Redis phone:alert:recent (LIST, TTL)
```

看板 REST：
```
Controller ─▶ DashboardService ─┬─▶ Redis cache (phone:dashboard:*)
                                └─▶ MyBatis-Plus Mapper ──▶ MySQL ads_*
```

## 5. 缓存策略

| Key 前缀 | 类型 | TTL | 失效方式 |
|---|---|---|---|
| `phone:dashboard:*` | String/JSON | 300s（`phone.redis.dashboard-ttl-seconds`） | 自然过期 |
| `phone:alert:recent` | List | 86400s（`phone.redis.alert-ttl-seconds`） | LTRIM 保留 100 条 + 过期 |

## 6. 关闭模拟器

如果只想验证看板查询，关掉模拟器：

```yaml
phone:
  simulator:
    enabled: false
```

或启动参数 `--phone.simulator.enabled=false`。
