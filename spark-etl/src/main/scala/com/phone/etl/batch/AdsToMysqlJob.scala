package com.phone.etl.batch

import com.phone.etl.common.JobRunner
import com.phone.etl.config.PhoneConfig
import org.apache.spark.sql.{SaveMode, SparkSession}

/**
 * ADS → MySQL：把 Hive `phone_ads.*` 8 张表全表 overwrite 回 MySQL `phone_analysis.*`。
 *
 * Spring Boot 看板直接查 MySQL（响应快、无需 Hive 依赖），所以这一步必须放在
 * 看板可用之前跑完。
 */
object AdsToMysqlJob {

  /** Hive 表名 → MySQL 表名（这里一一对应，名字相同） */
  private val tables: Seq[String] = Seq(
    "ads_profit_anomaly",
    "ads_metric_trend",
    "ads_low_contrib_model",
    "ads_low_contrib_channel",
    "ads_profit_decomp",
    "ads_high_value_model",
    "ads_segment_top_margin",
    "ads_growth_potential"
  )

  def main(args: Array[String]): Unit = JobRunner.run("ads-to-mysql") { spark =>
    tables.foreach(writeOne(spark, _))
    println(s"[ads-to-mysql] ${tables.size} 张表已回写 MySQL")
  }

  private def writeOne(spark: SparkSession, table: String): Unit = {
    val df = spark.sql(s"SELECT * FROM ${PhoneConfig.Hive.adsDb}.$table")
    df.write
      .mode(SaveMode.Overwrite)                      // 整表替换
      .option("truncate", "true")                     // 用 truncate 保留 schema 与索引
      .jdbc(PhoneConfig.Mysql.url, table, PhoneConfig.Mysql.jdbcProperties)
    println(s"[ads-to-mysql] $table -> MySQL OK")
  }
}
