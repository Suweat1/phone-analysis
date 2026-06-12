package com.phone.etl.streaming

import com.phone.etl.common.SparkSessionFactory
import com.phone.etl.config.PhoneConfig
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{StreamingQuery, Trigger}
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.slf4j.LoggerFactory

import java.util.UUID

/**
 * 实时告警流式 Job：消费 `phone_raw`（Kafka）→ 异常规则打分 → 双写：
 *   1) Kafka `phone_alert`：下游 SpringBoot AlertConsumer 消费 → SSE 推 Vue（毫秒级 UX）
 *   2) Hive `phone_ads.ads_realtime_alert`：日分区追加，供历史回填 / 离线分析
 *
 * 设计要点：
 *  - 用 foreachBatch 才能在单一流里做"既写 Kafka 又写 Hive"的多 sink；
 *  - watermark + eventTime 选择性使用：本 Job 是单事件级别规则，不做窗口聚合，所以仅靠 trigger 控制延迟；
 *    将来若加 1 分钟聚合写 `dws_realtime_metric_1min`，需在该路径上单独加 withWatermark；
 *  - JSON schema 与 [[com.phone.app.dto.SaleEvent]] 字段名一一对应（Jackson + Spark 默认都用 snake_case→camelCase 时需注意；
 *    我们走显式 schema 解析，避免命名风险）；
 *  - checkpoint 在 `PhoneConfig.Hdfs.checkpointDir/raw-alert/`，保证作业重启可断点续传；
 *  - 优雅退出：注册 shutdown hook，让 spark-submit kill 时能完成当前 batch。
 *
 * 提交示例：
 *   spark-submit --master yarn --deploy-mode client \
 *     --class com.phone.etl.streaming.RawStreamingJob \
 *     --files application.properties \
 *     phone-analysis-spark-etl.jar
 */
object RawStreamingJob {

  private val log = LoggerFactory.getLogger(getClass)

  /** 与 SaleEvent.java 字段一一对应。LocalDateTime 在 Jackson 默认序列化为 ISO 字符串。 */
  private val saleEventSchema: StructType = StructType(Seq(
    StructField("eventId",            StringType,  true),
    StructField("eventTime",          StringType,  true),  // 先按 string 收，再 cast 成 timestamp
    StructField("brand",              StringType,  true),
    StructField("model",              StringType,  true),
    StructField("processor",          StringType,  true),
    StructField("storageGb",          IntegerType, true),
    StructField("promotion",          StringType,  true),
    StructField("unitPrice",          IntegerType, true),
    StructField("salesQty",           IntegerType, true),
    StructField("productionCost",     DoubleType,  true),
    StructField("marketingCostTotal", DoubleType,  true),
    StructField("userCity",           StringType,  true),
    StructField("userAge",            IntegerType, true),
    StructField("userGender",         StringType,  true),
    StructField("userMemberLevel",    StringType,  true)
  ))

  def main(args: Array[String]): Unit = {
    val spark = SparkSessionFactory.build("raw-streaming")
    // 动态分区写 Hive 必须开
    spark.conf.set("hive.exec.dynamic.partition", "true")
    spark.conf.set("hive.exec.dynamic.partition.mode", "nonstrict")
    spark.conf.set("spark.sql.sources.partitionOverwriteMode", "dynamic")

    log.info(s"[RawStreamingJob] START  kafka=${PhoneConfig.Kafka.bootstrapServers}  topic=${PhoneConfig.Kafka.topicRaw}")

    val query = build(spark)

    // shutdown hook：允许 kill -SIGTERM 时优雅停止
    sys.addShutdownHook {
      log.warn("[RawStreamingJob] shutdown hook fired, stopping...")
      try query.stop() catch { case t: Throwable => log.error("query.stop fail", t) }
      try spark.stop() catch { case t: Throwable => log.error("spark.stop fail", t) }
    }

    query.awaitTermination()
  }

  /** 构建并启动 streaming query；提取出来便于将来加 IT 测试。 */
  def build(spark: SparkSession): StreamingQuery = {
    val kafkaRaw = readRaw(spark)
    val parsed   = parse(kafkaRaw)
    val tagged   = StreamingAnomalyRules.tag(parsed)
    val alerted  = tagged.filter(col("alert_type").isNotNull)

    alerted.writeStream
      .queryName("raw-to-alert")
      .option("checkpointLocation",
        PhoneConfig.Hdfs.fullPath(s"${PhoneConfig.Hdfs.checkpointDir}/raw-alert"))
      .trigger(Trigger.ProcessingTime(s"${PhoneConfig.Streaming.triggerIntervalSeconds} seconds"))
      .foreachBatch { (batch: DataFrame, batchId: Long) =>
        writeBatch(spark, batch, batchId)
      }
      .outputMode("append")
      .start()
  }

  /** 从 Kafka 读 `phone_raw` 原始字节流。 */
  private def readRaw(spark: SparkSession): DataFrame = {
    spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", PhoneConfig.Kafka.bootstrapServers)
      .option("subscribe",               PhoneConfig.Kafka.topicRaw)
      .option("startingOffsets",         PhoneConfig.Kafka.startingOffsets)
      .option("kafka.group.id",          PhoneConfig.Kafka.consumerGroup)
      .option("failOnDataLoss",          "false")
      .load()
  }

  /** 解析 Kafka value(JSON) → DataFrame；同时把 camelCase 字段重命名为 snake_case，
   *  与规则集 / DWD 命名一致。 */
  private[streaming] def parse(rawKafka: DataFrame): DataFrame = {
    val asJson = rawKafka.selectExpr("CAST(value AS STRING) AS json")
    val parsed = asJson.select(from_json(col("json"), saleEventSchema).as("e")).select("e.*")

    parsed
      .withColumnRenamed("eventId",            "event_id")
      .withColumn("event_time", to_timestamp(col("eventTime"))).drop("eventTime")
      .withColumnRenamed("storageGb",          "storage_gb")
      .withColumnRenamed("unitPrice",          "unit_price")
      .withColumnRenamed("salesQty",           "sales_qty")
      .withColumnRenamed("productionCost",     "production_cost")
      .withColumnRenamed("marketingCostTotal", "marketing_cost_total")
      .withColumnRenamed("userCity",           "user_city")
      .withColumnRenamed("userAge",            "user_age")
      .withColumnRenamed("userGender",         "user_gender")
      .withColumnRenamed("userMemberLevel",    "user_member_level")
  }

  /**
   * foreachBatch 实际工作体：
   *  - 给每条告警补 alert_id（UUID）、triggered_at（current_timestamp）、dt（处理日期）；
   *  - cache 一次，免得两个 sink 重算上游；
   *  - 写 Hive `ads_realtime_alert`（按 dt 分区追加）；
   *  - 写 Kafka `phone_alert`（JSON，与 AlertMessage.java 字段对齐）。
   */
  private[streaming] def writeBatch(spark: SparkSession, batch: DataFrame, batchId: Long): Unit = {
    if (batch.isEmpty) {
      log.info(s"[batch $batchId] empty, skip")
      return
    }

    val uuidUdf = udf(() => UUID.randomUUID().toString)
    val enriched = batch
      .withColumn("alert_id",     uuidUdf())
      .withColumn("triggered_at", current_timestamp())
      .withColumn("dt",           date_format(current_timestamp(), "yyyy-MM-dd"))
      .withColumn("related_entity", concat_ws("·", col("brand"), col("model")))
      .cache()

    try {
      val cnt = enriched.count()
      log.info(s"[batch $batchId] $cnt alerts → kafka & hive")

      // ─── sink 1: Hive ads_realtime_alert ───────────────────────────
      writeHive(spark, enriched)

      // ─── sink 2: Kafka phone_alert ─────────────────────────────────
      writeKafka(enriched)
    } finally {
      enriched.unpersist()
    }
  }

  /** 写 Hive：select 出与 DDL 一致的列序，按 dt 分区追加。 */
  private def writeHive(spark: SparkSession, enriched: DataFrame): Unit = {
    val table = s"${PhoneConfig.Hive.adsDb}.ads_realtime_alert"

    val ordered = enriched.select(
      col("alert_id"),
      col("alert_type"),
      col("alert_level"),
      col("alert_title"),
      col("alert_content"),
      col("related_entity"),
      col("deviation"),
      col("event_id"),
      col("event_time"),
      col("brand"),
      col("model"),
      col("promotion"),
      col("unit_price"),
      col("sales_qty"),
      col("revenue"),
      col("gross_profit"),
      col("gross_margin"),
      col("marketing_ratio"),
      col("triggered_at"),
      col("dt")
    )

    ordered.write
      .mode("append")
      .insertInto(table)
  }

  /**
   * 写 Kafka：把每行装成与 [[com.phone.app.dto.AlertMessage]] 一致的 JSON。
   *
   * 字段映射：
   *   alertId       <- alert_id
   *   type          <- alert_type
   *   level         <- alert_level
   *   title         <- alert_title
   *   content       <- alert_content
   *   triggeredAt   <- triggered_at（Jackson 反序列化 LocalDateTime 用 ISO_LOCAL_DATE_TIME）
   *   relatedEntity <- related_entity
   *   deviation     <- deviation
   */
  private def writeKafka(enriched: DataFrame): Unit = {
    val asJson = enriched.select(
      to_json(struct(
        col("alert_id").as("alertId"),
        col("alert_type").as("type"),
        col("alert_level").as("level"),
        col("alert_title").as("title"),
        col("alert_content").as("content"),
        // SpringBoot 端 LocalDateTime 默认 ISO 串：yyyy-MM-ddTHH:mm:ss
        date_format(col("triggered_at"), "yyyy-MM-dd'T'HH:mm:ss").as("triggeredAt"),
        col("related_entity").as("relatedEntity"),
        col("deviation").as("deviation")
      )).as("value"),
      col("alert_id").as("key")
    )

    asJson.write
      .format("kafka")
      .option("kafka.bootstrap.servers", PhoneConfig.Kafka.bootstrapServers)
      .option("topic",                    PhoneConfig.Kafka.topicAlert)
      .save()
  }
}
