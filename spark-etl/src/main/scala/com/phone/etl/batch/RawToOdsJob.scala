package com.phone.etl.batch

import com.phone.etl.common.JobRunner
import com.phone.etl.config.PhoneConfig

/**
 * Raw → ODS：
 * - ODS 是 EXTERNAL TABLE，LOCATION 指向 HDFS `phone-analysis/raw/`；
 * - 本 Job 只需 `MSCK REPAIR TABLE` 让 Hive 重新扫描 parquet 文件即可；
 * - 数据落地由运维侧 `hdfs dfs -put` 完成（见 docs/deploy/10-startup-order.md）。
 *
 * 提交：
 *   spark-submit --class com.phone.etl.batch.RawToOdsJob \
 *     --files application.properties \
 *     phone-analysis-spark-etl.jar
 */
object RawToOdsJob {

  def main(args: Array[String]): Unit = JobRunner.run("raw-to-ods") { spark =>
    JobRunner.useDb(spark, PhoneConfig.Hive.odsDb)
    spark.sql("MSCK REPAIR TABLE ods_phone_sales")

    val cnt = spark.sql("SELECT COUNT(*) AS c FROM ods_phone_sales").head().getLong(0)
    println(s"[raw-to-ods] ods_phone_sales row count = $cnt")
  }
}
