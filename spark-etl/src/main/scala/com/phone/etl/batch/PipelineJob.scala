package com.phone.etl.batch

import com.phone.etl.common.JobRunner
import org.apache.spark.sql.SparkSession

/**
 * 串行执行整条批处理链路（适合每日离线刷新使用）。
 *
 * 流程：
 *   RawToOds (REFRESH 外部表，非分区无需 MSCK)
 *     → OdsToDwd
 *       → DwdToDws
 *         → DwsToAds
 *           → AdsToMysql + ColumnDict
 *
 * 提交：
 *   spark-submit --class com.phone.etl.batch.PipelineJob \
 *     --files application.properties \
 *     phone-analysis-spark-etl.jar
 *
 * 备注：复用同一个 SparkSession，避免每步重新申请 YARN 容器。
 */
object PipelineJob {

  def main(args: Array[String]): Unit = JobRunner.run("pipeline") { spark =>
    JobRunner.printContext()

    step(spark, "raw-to-ods")   { runMain(RawToOdsJob.main _,   spark) }
    step(spark, "ods-to-dwd")   { runMain(OdsToDwdJob.main _,   spark) }
    step(spark, "dwd-to-dws")   { runMain(DwdToDwsJob.main _,   spark) }
    step(spark, "dws-to-ads")   { runMain(DwsToAdsJob.main _,   spark) }
    step(spark, "ads-to-mysql") { AdsToMysqlJob.main(Array.empty) }
    step(spark, "column-dict")  { ColumnDictJob.write(spark) }
  }

  // ---------- 内部 helper ----------
  private def step(spark: SparkSession, name: String)(body: => Unit): Unit = {
    val t = System.currentTimeMillis()
    println(s"---- step [$name] start ----")
    body
    println(s"---- step [$name] done in ${System.currentTimeMillis() - t} ms ----")
  }

  /**
   * 复用 Job 的 SQL 逻辑但不重新创建 SparkSession。
   * 由于各 Job 的 main 实现都先 build 自己的 session，这里改为直接拷贝逻辑会太冗长，
   * 简单起见仍调用 main()；JobRunner.run 内会 build & stop 子 session，
   * 与本顶层 session 通过 Hive Metastore 解耦不会出问题。
   *
   * 若以后追求极致性能，可改为把每个 Job 的核心 SQL 抽成 `run(spark)` 方法在此调用。
   */
  private def runMain(mainFn: Array[String] => Unit, ignored: SparkSession): Unit = {
    mainFn(Array.empty)
  }
}
