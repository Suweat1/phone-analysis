package com.phone.etl.common

import com.phone.etl.config.PhoneConfig
import org.apache.spark.sql.SparkSession

/**
 * SparkSession 工厂：所有 Job 通过本工厂获取会话，确保
 *  - master / appName / Hive 集成 等参数统一来自 [[PhoneConfig]]；
 *  - 不在业务代码里写死 spark.master 或开关。
 */
object SparkSessionFactory {

  /**
   * @param appNameSuffix 在 PhoneConfig.Spark.appName 后追加的后缀，便于 YARN UI 区分
   */
  def build(appNameSuffix: String = ""): SparkSession = {
    val finalAppName =
      if (appNameSuffix.isEmpty) PhoneConfig.Spark.appName
      else s"${PhoneConfig.Spark.appName}-$appNameSuffix"

    SparkSession.builder()
      .appName(finalAppName)
      .master(PhoneConfig.Spark.master)
      .config("spark.sql.session.timeZone", PhoneConfig.App.timezone)
      .config("spark.sql.shuffle.partitions",
        PhoneConfig.Streaming.shufflePartitions.toString)
      .enableHiveSupport()
      .getOrCreate()
  }
}
