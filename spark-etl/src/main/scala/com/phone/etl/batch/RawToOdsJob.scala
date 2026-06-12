package com.phone.etl.batch

import com.phone.etl.common.JobRunner
import com.phone.etl.config.PhoneConfig

/**
 * Raw → ODS：
 * - ODS 是 EXTERNAL TABLE，LOCATION 指向 HDFS `phone-analysis/raw/`；
 * - 表非分区，Spark 每次查询都会直接列 LOCATION 下的文件，不需要 `MSCK REPAIR TABLE`
 *   （那条命令仅对分区表有效，Spark 3 会直接报 "only works on partitioned tables"）；
 *   `REFRESH TABLE` 用于让 Spark 清掉文件列表缓存，避免 `hdfs put` 之后看到旧快照。
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
    spark.sql("REFRESH TABLE ods_phone_sales")

    val cnt = spark.sql("SELECT COUNT(*) AS c FROM ods_phone_sales").head().getLong(0)
    println(s"[raw-to-ods] ods_phone_sales row count = $cnt")
  }
}
