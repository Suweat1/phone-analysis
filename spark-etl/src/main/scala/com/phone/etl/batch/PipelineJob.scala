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
    step(spark, "column-dict")  { runMain(ColumnDictJob.main _, spark) }
  }

  // ---------- 内部 helper ----------
  private def step(spark: SparkSession, name: String)(body: => Unit): Unit = {
    val t = System.currentTimeMillis()
    println(s"---- step [$name] start ----")
    body
    println(s"---- step [$name] done in ${System.currentTimeMillis() - t} ms ----")
  }

  /**
   * 各子 Job 通过自己的 `JobRunner.run` 拿到 SparkSession 并在 finally 中 stop。
   * Spark 的 `SparkSession.builder().getOrCreate()` 会复用同进程已有的 SparkContext —
   * 因此「子 Job stop session」会把 PipelineJob 这个顶层 session 也一并停掉。
   *
   * 这是有意行为：当前 8GB VM + local 模式下，只跑一条链路，让每步用完即停反而干净。
   * 唯一需要小心的是：**不能在子 Job stop 之后再用顶层 spark 跑任何 RDD**。
   * 历史踩坑：曾把 `ColumnDictJob.write(spark)` 直接接在 ads-to-mysql 之后，
   * 结果 SparkContext 已被前一步 stop，column-dict 触发的 jdbc save 报
   * "Cannot call methods on a stopped SparkContext"。修法是改成本 helper 调用
   * `ColumnDictJob.main` —— 它内部会重新 `getOrCreate` 一个新 SparkContext。
   *
   * 若以后追求极致性能（避免 6 次 SparkContext start/stop ≈ 12s 启动开销），
   * 可改为把每个 Job 的核心 SQL 抽成 `run(spark)` 方法、由 PipelineJob 单一 session
   * 串起来，并删掉子 Job 的 `JobRunner.run` 包装。
   */
  private def runMain(mainFn: Array[String] => Unit, ignored: SparkSession): Unit = {
    mainFn(Array.empty)
  }
}
