# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

phone-analysis 是一个手机销售数据的大数据分析项目，最终交付一个 **可视化分析看板系统**。

**核心数据流（离线主链 + 模拟实时旁链）**：

```
本机 (macOS, 仅开发):
  data/source-phone.xlsx
   ↓ scripts/preprocess.py (pandas)
  data/processed/phone.parquet  → git commit
   ↓ git push

运行机 (Ubuntu 20.04 虚拟机, 单节点伪分布式):
  git pull → hdfs put → /phone-analysis/raw/phone.parquet
   ↓ spark-etl (Spark SQL, scala 2.12)
  Hive: phone_ods → phone_dwd → phone_dws → phone_ads
   ↓ spark.write
  MySQL.phone_analysis (看板聚合表) + Redis (热点缓存)
   ↓
  SpringBoot REST (app/)
   ↓
  Vue 2 + ECharts (web/)  ← 可视化看板

[模拟实时链]
  SpringBoot 定时器 → Kafka(phone_raw)
   → Spark Structured Streaming → Hive 告警表 + Kafka(phone_alert)
   → SpringBoot 推送 → Vue 告警栏
```

**看板要回答的业务问题**：手机利润异常、经济指标波动、低贡献机型/渠道识别、利润下滑归因（客单价/销量/成本/营销费用 四维度）、高价值机会机型、利润率优异细分市场、被低估的增长潜力点。

## 开发与运行环境分离

- **本机（macOS, darwin 25.5.0）**：仅写代码 + git push，**几乎没有运行时**（无 java/maven/spark/node）。
  - **不要在本机执行 `mvn`、`sbt`、`spark-submit`、`npm run dev` 等编译/运行命令**，会失败。
  - 唯一例外：若用户明确说「本机能跑 python」，预处理脚本 `scripts/preprocess.py` 可在本机跑一次。
- **运行机（Ubuntu 20.04 虚拟机，glibc 2.31，主机名 `phone-analysis`，用户 `bigdata`）**：所有大数据组件 + 应用层在此运行。
- **代码同步链路**：本机 git push → 远程仓库 → 运行机 `git pull`。**不要尝试 ssh 到虚拟机**（在另一台物理机上，未配置直连）。

## 关键决策（已锁定）

| 决策点 | 选定方案 |
|---|---|
| 仓库布局 | Monorepo 单仓多模块 |
| Spark 风格 | Spark SQL 为主（DataFrame 辅助，MLlib 仅在异常/预测处局部用） |
| 原始数据接入 | 本机 pandas 一次性预处理 → CSV/Parquet → git → HDFS |
| Kafka 模式 | Zookeeper 模式（非 KRaft） |
| Kafka 用途 | 离线主链 + 模拟实时旁链（SpringBoot 模拟事件） |
| Hive Metastore | Remote Metastore（9083），Spark on Hive 通过 thrift 接 |
| Spark 运行模式 | local[*]（单 JVM；8GB VM 不够同时跑 HDFS+Hive+Kafka+ZK+MySQL+Redis+YARN，且 20k 行用 local 反更快。如需切回 yarn 改 `config/spark/spark-defaults.conf` 与 `config/spark-etl/application.properties` 的 `spark.master` 即可） |

## 仓库布局

```
phone-analysis/
├── app/                # Spring Boot 2.7.x 应用层（REST + 告警推送 + Kafka 生产者）
├── spark-etl/          # Scala 2.12 + Spark SQL 3.3.1 的 ETL 与 Streaming 作业
├── web/                # Vue 2.x + ECharts 看板前端
├── config/             # 所有组件 + 三个模块的项目版配置文件（部署时 cp 到目标路径）
│   ├── mysql/          # my.cnf
│   ├── redis/          # redis.conf
│   ├── hadoop/         # hadoop-env.sh, core-site.xml, hdfs-site.xml, mapred-site.xml, yarn-site.xml
│   ├── hive/           # hive-env.sh, hive-site.xml
│   ├── kafka/          # zookeeper.properties, server.properties
│   ├── spark/          # spark-env.sh, spark-defaults.conf
│   ├── maven/          # settings.xml
│   ├── app/            # application.yml（Spring Boot）
│   ├── spark-etl/      # application.properties（Spark 作业用）
│   └── web/            # .env / config.js（Vue）
├── data/
│   ├── source-phone.xlsx    # 原始数据
│   └── processed/           # 预处理产物（parquet/csv），由脚本生成，提交 git
├── scripts/            # 部署、启停、预处理脚本
│   ├── preprocess.py        # 本机 pandas 预处理
│   ├── start-all.sh         # 运行机一键启动（见 docs/deploy/10-startup-order.md）
│   └── stop-all.sh
├── docs/
│   └── deploy/         # 部署文档 00~11（已完成）
├── pom.xml             # Maven 父 pom，聚合 app + spark-etl + common
└── CLAUDE.md
```

## 配置 + 宏变量铁律

**所有模块（app/spark-etl/web）必须遵循以下模式**：

1. **应用层真实配置直接放在源码目录**（保证本机 `mvn package` / `npm run build` 可跑、零外置依赖）：
   - `app/` → `app/src/main/resources/application.yml`（打进 jar 的 classpath）
   - `spark-etl/` → `spark-etl/src/main/resources/application.properties`（打进 jar 的 classpath，运行机 `--files` 可覆盖）
   - `web/` → `web/.env`、`web/.env.development`、`web/.env.production`（Vue CLI 默认读这三个）
2. **代码中存在 `Config` 类**：把配置项映射为「宏变量」，例如：
   - Java：`@ConfigurationProperties("phone")` → `PhoneConfig.mysqlUrl`
   - Scala：`object PhoneConfig { val mysqlUrl: String = conf.getString("phone.mysql.url") }`
   - Vue：`src/config/index.js` → `this.$cfg.apiBase`
3. **业务代码只引用宏变量**，**禁止** 在业务代码里硬编码：URL、路径、用户名、密码、端口、HDFS 前缀、Hive 库名、Kafka topic。
4. **修改密码 / 主机名 / 路径** 只动相应模块的 `src/main/resources/`（或 `web/.env*`）这一份，业务代码不动。
5. **`config/` 目录只放两类**：
   - **集群组件配置**（mysql/redis/hadoop/hive/kafka/spark/maven）：由 `scripts/deploy-config.sh` 软链到运行机组件目录。
   - **应用层覆盖配置（可选）**：`config/app/application.yml`、`config/spark-etl/application.properties` 为运行机特别覆盖模板，用 Spring `spring.config.additional-location` / Spark `--files` 注入；不强制使用。

## 强制约束（运行机侧）

1. **禁用 systemd**：所有组件用 `nohup` 或自带 `*.sh` 启动；任何 `systemd unit` 严禁出现。
2. **环境变量统一写入 `/home/bigdata/.bashrc`**，**禁止** 写到 `/etc/profile.d/*.sh`。
3. **运行用户 `bigdata`**：sudo 免密 + ssh 免密 `localhost`（Hadoop 要求）；**主机名 `phone-analysis`**。
4. **统一密码**：MySQL=`123456`，Hive=`123456`，Redis=`redis123`。
5. **目录约定 `/opt/bigdata/{log,data,software,service}`**：`software/` 放原始 tar 包，`service/` 是解压目标，`log/<组件>/` 放日志，`data/` 放业务数据。
6. **JDK 必须软链接**：`/opt/bigdata/service/jdk` → 实际版本目录，规避小版本升级。
7. **二进制部署 + Redis 源码编译**：除 Redis 用源码 `make`（官方无预编译），其余组件全部用 tar.gz/tar.xz 预编译包。
8. **跨组件 jar 冲突需要手动覆盖**（典型：guava / jline / MySQL JDBC）；改动必须更新 `docs/deploy/11-jar-conflicts.md`。
9. **Java 代码必须 JDK 1.8 兼容**：源码与字节码均锁 1.8（`maven.compiler.source/target=1.8`）。**禁止** 使用 JDK 9+ 引入的 API，常见踩坑：
   - `List.of(...)` / `Set.of(...)` / `Map.of(...)` → 用 `Arrays.asList(...)` / `Collections.singletonList(...)` / `new HashMap<>(){{ put(k,v); }}`
   - `var` 局部变量推断 → 显式类型
   - `String.repeat / lines / strip / isBlank` → Guava `Strings.repeat` 或手写
   - `Files.readString / writeString` → `new String(Files.readAllBytes(...), UTF_8)`
   - `Optional.orElseThrow()`（无参重载） → `.orElseThrow(() -> ...)` 或 `.get()`
   - `Stream.toList()` → `.collect(Collectors.toList())`
   - 局部 `record` / sealed 类 / switch expression → 普通 class / switch 语句
10. **Scala 代码必须 2.12 兼容**：Spark 3.3.1 用 Scala 2.12，**禁止** 使用 2.13+ API：
    - `scala.util.Using` / `Using.resource` → 手动 `try { src } finally { src.close() }`
    - `String.linesIterator` → `.split("\n")` 或 `Source.fromString(s).getLines()`
    - `Stream.toIntOption / toDoubleOption` → `Try(s.toInt).toOption`
    - **SLF4J 调 `log.info(fmt, args*)` 最多 2 个变参**（重载受限）；超过用字符串插值 `log.info(s"... ${a} ${b} ${c}")`。混用 `:Any` 强转也容易让重载分发出错，统一插值风格更稳。
    - **`import spark.implicits._` 要求 `spark` 是 stable identifier**（`val` / `lazy val`），不能用 `var spark: SparkSession = _`。ScalaTest 里推荐 `private lazy val spark = SparkSession.builder()...getOrCreate()`，在类层级写 import 而非每个 test 内重复 import。

## 数仓分层规范（Hive）

| 库名 | 角色 | 内容 |
|---|---|---|
| `phone_ods` | 原始接入 | 与 parquet 字段一一对应，最少加工 |
| `phone_dwd` | 明细清洗 | 类型规整、维度拉宽、口径统一 |
| `phone_dws` | 汇总聚合 | 按机型/渠道/时段的指标聚合 |
| `phone_ads` | 应用层 | 看板直接消费的结果表，回写 MySQL |

## 与用户的协作方式

- **遇到设计不确定点（模块设计、技术选型、字段口径、算法选择）必须主动提问**，由用户给出 submit 决定，不要擅自拍板。
- **完成代码后**，按 `/graphify` skill 对代码进行图谱化。
- 解释命令时，若需要用户在本会话运行交互命令，提示用户在输入框前加 `!`（在当前会话内执行，输出回到对话）。

## 当前状态

完成度（按数据流顺序）：

| 模块 | 状态 | 入口文档 |
|---|---|---|
| 部署 | ✅ 12 篇 | `docs/deploy/README.md` |
| 配置 | ✅ 12 个组件 + app/spark-etl/web | `config/README.md` |
| 预处理 | ✅ pandas → CSV+Parquet (20000 × 34) | `scripts/preprocess.py` |
| 数仓 DDL | ✅ Hive 4 库 + MySQL 8 表 + 字典 | `spark-etl/src/main/resources/ddl/README.md` |
| Spark ETL | ✅ 7 个 Job（含 Pipeline 一键跑） | `spark-etl/RUN.md` |
| SpringBoot 应用 | ✅ 19 个 API + SSE 告警 + 模拟器 | `app/RUN.md` |
| Vue 看板 | ✅ 8 个板块 + ECharts + 实时告警栏 | `web/RUN.md` |
| Spark Streaming | ✅ RawStreamingJob（Kafka phone_raw → 规则告警 → Kafka phone_alert + Hive 归档） | `spark-etl/RUN.md §4` |
| 启停一键脚本 | ✅ 6 个脚本（deploy/build/start/stop/status/pipeline） + 2 个流式脚本 | `scripts/README.md` |

**添加新模块或调整决策时，同步更新本文件的「关键决策」、「仓库布局」与本节。**

## 跨模块字段映射真实源（Python ↔ Scala ↔ Java）

字段中英映射目前散在 **三处**，新增/改字段必须三处同步，否则 Spark 写字典 → Java 读字典会遗漏：

1. `scripts/column_mapping.py` —— 预处理脚本（事实上的最早源）
2. `spark-etl/src/main/scala/com/phone/etl/common/ColumnMapping.scala` —— Spark 写入 MySQL `ads_column_dict`
3. `app/src/main/java/com/phone/app/common/ColumnMapping.java` —— SpringBoot 在 `ads_column_dict` 不可用时的兜底

运行时优先以 MySQL `ads_column_dict` 为准；前端从 `/api/dict/columns` 拉一次。
