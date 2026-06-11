# app/ — Spring Boot 应用层

> 此目录为占位骨架。模块尚未生成具体代码；详细职责见 [../CLAUDE.md](../CLAUDE.md)。

## 职责

- 提供看板 REST API（读 MySQL/Hive 聚合表，写 Redis 缓存）。
- 通过 HiveServer2 JDBC 查询 `phone_ads` 层做即席分析。
- Kafka 生产者：定时模拟「销售事件」推到 `phone_raw`。
- 订阅 `phone_alert`，通过 SSE / WebSocket 向 Vue 推送告警。

## 配置

- 配置文件源：`config/app/application.yml`
- 部署时由运行机 `cp ~/phone-analysis/config/app/application.yml ./application.yml`
- 代码中必须有 `PhoneConfig`（`@ConfigurationProperties("phone")`），业务代码只引用宏变量，**禁止** 硬编码 URL/密码/路径/topic。

## TODO

- [ ] 生成 pom.xml（Spring Boot 2.7.x + spring-data-redis + spring-kafka + mybatis + hive-jdbc-standalone）
- [ ] `com.phone.app.config.PhoneConfig`
- [ ] `com.phone.app.controller.*`
- [ ] `com.phone.app.service.*`
- [ ] `com.phone.app.kafka.*`
