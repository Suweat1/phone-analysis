# spark-etl/ — Spark SQL ETL & Streaming

> 此目录为占位骨架。模块尚未生成具体代码；详细职责见 [../CLAUDE.md](../CLAUDE.md)。

## 职责

- **批处理**（Spark SQL 为主）：HDFS Parquet → Hive `phone_ods` → `phone_dwd` → `phone_dws` → `phone_ads` → MySQL
- **结构化流**：Kafka `phone_raw` → 实时聚合 → Hive 告警表 + Kafka `phone_alert`
- **机器学习**（局部 DataFrame API + MLlib）：利润异常检测、机型潜力评分

## 配置

- 配置文件源：`config/spark-etl/application.properties`
- 代码中必须有 `object PhoneConfig`，从 properties 读取所有 URL/topic/HDFS 路径/Hive 库名作为宏变量。
- spark-submit 时通过 `--files config/spark-etl/application.properties` 分发到 executor。

## 提交

参考 [docs/deploy/07-spark.md](../docs/deploy/07-spark.md)：

```bash
spark-submit --master yarn --deploy-mode client \
  --class com.phone.etl.batch.OdsToDwdJob \
  /opt/bigdata/data/jars/phone-etl-1.0.0-SNAPSHOT.jar
```

## TODO

- [ ] 生成 pom.xml（scala 2.12 + Spark 3.3.1 + spark-sql-kafka + spark-hive + spark-mllib）
- [ ] `com.phone.etl.config.PhoneConfig`
- [ ] `com.phone.etl.batch.{RawToOds, OdsToDwd, DwdToDws, DwsToAds, AdsToMysql}`
- [ ] `com.phone.etl.streaming.AlertStreamingJob`
- [ ] `com.phone.etl.ml.{ProfitAnomalyDetector, ModelPotentialScorer}`
