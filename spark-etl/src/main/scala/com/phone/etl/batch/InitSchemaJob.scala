package com.phone.etl.batch

import com.phone.etl.common.{JobRunner, SqlRunner}

/**
 * 一次性 DDL 部署 Job：
 *   - 建 4 个 Hive 库 + ODS/DWD/DWS/ADS 全部表；
 *   - 同步触发 `ColumnDictJob` 把字段字典刷到 MySQL `ads_column_dict`。
 *
 * 提交：
 *   spark-submit --class com.phone.etl.batch.InitSchemaJob \
 *     --files application.properties \
 *     phone-analysis-spark-etl.jar
 *
 * 注：本 Job 是幂等的（DROP IF EXISTS + CREATE），可重复执行覆盖最新 DDL。
 *     但 ODS 是 EXTERNAL TABLE，DROP 不会清 HDFS 数据，可放心。
 */
object InitSchemaJob {

  def main(args: Array[String]): Unit = JobRunner.run("init-schema") { spark =>
    JobRunner.printContext()

    Seq(
      "ddl/00-init-databases.sql",
      "ddl/01-ods.sql",
      "ddl/02-dwd.sql",
      "ddl/03-dws.sql",
      "ddl/04-ads.sql",
      "ddl/06-streaming.sql"
    ).foreach(SqlRunner.runFromResource(spark, _))

    // 同 Job 内顺便刷字段字典
    ColumnDictJob.write(spark)
  }
}
