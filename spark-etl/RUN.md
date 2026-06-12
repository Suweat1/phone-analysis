# Spark ETL 提交手册

> 假设运行机已按 [docs/deploy/](../../../../../docs/deploy/) 完成所有组件部署，
> 且本模块已构建为 `target/phone-analysis-spark-etl.jar`（`mvn -DskipTests clean package`）。

## 1. 首次部署（仅做一次）

将 jar 上传到运行机：

```bash
mkdir -p /opt/bigdata/data/jars
cp ~/phone-analysis/spark-etl/target/phone-analysis-spark-etl.jar /opt/bigdata/data/jars/
```

> `application.properties` 已被打进 jar 的 classpath（`spark-etl/src/main/resources/application.properties`），
> 默认情况下 spark-submit 不需要 `--files` 分发。
>
> 若要按运行环境覆盖（如运行机端密码与默认不同），仍可：
> ```bash
> cp ~/phone-analysis/config/spark-etl/application.properties /opt/bigdata/data/jars/
> spark-submit ... --files /opt/bigdata/data/jars/application.properties ...
> ```
> `PhoneConfig` 会优先读外部文件，回退到 classpath。

把 parquet 推到 HDFS：

```bash
hdfs dfs -mkdir -p /phone-analysis/raw
hdfs dfs -put -f ~/phone-analysis/data/processed/phone.parquet /phone-analysis/raw/
```

跑一次 DDL 初始化（含字段字典）：

```bash
spark-submit \
  --master yarn --deploy-mode client \
  --class com.phone.etl.batch.InitSchemaJob \
  /opt/bigdata/data/jars/phone-analysis-spark-etl.jar
```

在 MySQL 端先建 8 张回写表（仅一次）：

```bash
mysql -uroot -p123456 phone_analysis \
  < ~/phone-analysis/spark-etl/src/main/resources/ddl/05-mysql-ads.sql
```

## 2. 日常全量刷新

```bash
spark-submit \
  --master yarn --deploy-mode client \
  --class com.phone.etl.batch.PipelineJob \
  /opt/bigdata/data/jars/phone-analysis-spark-etl.jar
```

完成后看板（Spring Boot）刷新即可看到最新结果。

## 3. 单步重跑

| 类 | 用途 |
|---|---|
| `com.phone.etl.batch.RawToOdsJob` | 数据更新后让 Hive 重新识别 parquet |
| `com.phone.etl.batch.OdsToDwdJob` | 派生指标口径变了 |
| `com.phone.etl.batch.DwdToDwsJob` | 聚合维度变了 |
| `com.phone.etl.batch.DwsToAdsJob` | 看板算法 / 阈值变了 |
| `com.phone.etl.batch.AdsToMysqlJob` | 单独把 ADS 同步回 MySQL |
| `com.phone.etl.batch.ColumnDictJob` | 字段中英映射变了 |

提交模板同 §2，把 `--class` 替换为对应类名即可。

## 4. 实时告警流式 Job

```bash
# 启动（长进程，写到 /opt/bigdata/log/spark/streaming.out；pid 在 /opt/bigdata/data/pid/streaming.pid）
bash ~/phone-analysis/scripts/start-streaming.sh

# 停止（优雅，会完成当前 batch）
bash ~/phone-analysis/scripts/stop-streaming.sh
```

数据链路：

```
SpringBoot EventSimulator → Kafka phone_raw
   → RawStreamingJob 解析+异常规则
   ├──→ Kafka phone_alert ─→ AlertConsumer ─→ SSE → Vue 告警栏
   └──→ Hive phone_ads.ads_realtime_alert（按日分区，append）
```

5 条 v1 规则（`streaming/StreamingAnomalyRules.scala`）：

| type | 触发 | level |
|---|---|---|
| `big_loss`          | gross_profit < -1000 且 revenue>0 | **high** |
| `marketing_burnout` | marketing_cost / revenue > 0.4 | mid |
| `profit_anomaly`    | gross_margin <5% 或 >60% | mid |
| `price_outlier`     | unit_price >12000 或 <500 | low |
| `qty_spike`         | sales_qty >= 50 | low |

规则之间优先级从高到低互斥，每条事件最多一个告警。阈值与告警文案见 `StreamingAnomalyRules.Thresholds`。

### 排错

| 现象 | 原因 | 修法 |
|---|---|---|
| 看板没有新告警 | Streaming 进程没起 / EventSimulator 关了 | `jps -l | grep RawStreamingJob`；`curl http://phone-analysis:8080/api/alert/_status` |
| 启动报 `Cannot find data source: kafka` | `spark-sql-kafka-0-10` jar 没分发 | 见 `docs/deploy/07-spark.md` §"Kafka 集成" |
| checkpoint 报权限 | HDFS 上 `/spark-checkpoint/raw-alert` 不存在/不可写 | `hdfs dfs -mkdir -p /spark-checkpoint && hdfs dfs -chown -R bigdata /spark-checkpoint` |
| Hive insertInto 报分区列不匹配 | DDL 改过但流没重启 | `stop-streaming.sh && start-streaming.sh`，必要时 `hdfs dfs -rm -r /spark-checkpoint/raw-alert/*` 重新开始 |
| 想清空当前 offset 重消费 | — | 先 stop，删 checkpoint 目录，再 `kafka.starting-offsets=earliest`（改 application.properties）后重启 |

## 5. 常见排错（批处理）

| 现象 | 原因 | 修法 |
|---|---|---|
| `Table or view 'ods_phone_sales' not found` | 还未跑 InitSchemaJob 或库名不一致 | 跑 InitSchemaJob；检查 `application.properties` 的 `hive.ods-db` |
| `Caused by: org.apache.hadoop.hive.metastore.api.MetaException: Could not connect to meta store` | Hive Metastore 没起 | `nohup hive --service metastore &` |
| `No suitable driver for jdbc:mysql://...` | fat jar 没含 MySQL JDBC | 重 `mvn package`，确认 pom 中 `mysql-connector-java` 不是 provided |
| `Failed to access metastored ... StackOverflowError` | hive-site.xml 没分发 | 确认 `$SPARK_HOME/conf/hive-site.xml` 存在（或 `--files` 分发） |
| MySQL 端字段编码乱 | 表用 utf8mb4 但连接 URL 缺 charset | URL 已带 `characterEncoding=UTF-8`；若仍乱，检查 my.cnf 的 `character-set-server` |
