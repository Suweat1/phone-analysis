package com.phone.etl.common

import com.phone.etl.config.PhoneConfig
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

/**
 * Job 通用模板：构建 SparkSession → 执行 body → 异常重抛 + 计时日志。
 *
 * 所有批处理 Job 入口都通过本工具运行，保证日志格式一致、
 * 异常时 SparkSession 一定 stop。
 */
object JobRunner {

  private val log = LoggerFactory.getLogger(getClass)

  /**
   * @param jobName 用于 SparkUI 与日志，会拼到 PhoneConfig.Spark.appName 之后
   * @param body   接收 SparkSession 的副作用函数
   */
  def run(jobName: String)(body: SparkSession => Unit): Unit = {
    val spark = SparkSessionFactory.build(jobName)
    val start = System.currentTimeMillis()
    log.info(s"============ [$jobName] START ============")
    try {
      // 动态分区（DWD 按月分区，必须开）
      spark.conf.set("hive.exec.dynamic.partition", "true")
      spark.conf.set("hive.exec.dynamic.partition.mode", "nonstrict")
      spark.conf.set("spark.sql.sources.partitionOverwriteMode", "dynamic")

      body(spark)
      val cost = System.currentTimeMillis() - start
      log.info(s"============ [$jobName] OK in $cost ms ============")
    } catch {
      case e: Throwable =>
        log.error(s"[$jobName] FAIL: ${e.getMessage}", e)
        throw e
    } finally {
      spark.stop()
    }
  }

  /** 切换/创建 Hive 当前库 */
  def useDb(spark: SparkSession, db: String): Unit = {
    spark.sql(s"CREATE DATABASE IF NOT EXISTS $db")
    spark.sql(s"USE $db")
  }

  /** 仅供日志：把 PhoneConfig 的关键参数打一次，方便排查 */
  def printContext(): Unit = {
    log.info(s"hdfs.namenode = ${PhoneConfig.Hdfs.namenode}")
    log.info(s"hive.ods/dwd/dws/ads = ${PhoneConfig.Hive.odsDb}/${PhoneConfig.Hive.dwdDb}/" +
      s"${PhoneConfig.Hive.dwsDb}/${PhoneConfig.Hive.adsDb}")
    log.info(s"mysql.url = ${PhoneConfig.Mysql.url}")
    log.info(s"kafka.bootstrap = ${PhoneConfig.Kafka.bootstrapServers}")
  }
}
