package com.phone.etl.config

import com.typesafe.config.{Config, ConfigFactory}

import java.io.File

/**
 * Spark ETL 全局配置宏变量入口（与 `config/spark-etl/application.properties` 一一对应）。
 *
 * <h3>加载顺序</h3>
 * 1. 优先读 spark-submit `--files` 分发后的工作目录文件 `./application.properties`；
 * 2. 退化到 classpath 下同名文件（IDE / 单测使用）。
 *
 * <h3>使用纪律</h3>
 *  - 业务代码（Job / DataFrame 算子）<b>只</b> 引用本对象的字段；
 *  - <b>禁止</b> 在 Scala 文件里写死任何 URL / 路径 / topic / 密码 / 库名；
 *  - 修改这些值只动 application.properties。
 */
object PhoneConfig {

  private lazy val conf: Config = loadConfig()

  private def loadConfig(): Config = {
    // spark-submit --files 分发到 executor 工作目录后，文件就在当前目录
    val external = new File("application.properties")
    if (external.exists()) {
      ConfigFactory.parseFile(external).withFallback(ConfigFactory.load())
    } else {
      ConfigFactory.load("application.properties")
    }
  }

  // ============ Spark ============
  object Spark {
    val appName: String = conf.getString("spark.app.name")
    val master: String  = conf.getString("spark.master")
  }

  // ============ HDFS ============
  object Hdfs {
    val namenode: String      = conf.getString("hdfs.namenode")
    val rawDir: String        = conf.getString("hdfs.raw-dir")
    val odsDir: String        = conf.getString("hdfs.ods-dir")
    val dwdDir: String        = conf.getString("hdfs.dwd-dir")
    val dwsDir: String        = conf.getString("hdfs.dws-dir")
    val adsDir: String        = conf.getString("hdfs.ads-dir")
    val checkpointDir: String = conf.getString("hdfs.checkpoint")

    /** 完整 URL：namenode + 子路径 */
    def fullPath(sub: String): String = s"$namenode$sub"
  }

  // ============ Hive ============
  object Hive {
    val odsDb: String = conf.getString("hive.ods-db")
    val dwdDb: String = conf.getString("hive.dwd-db")
    val dwsDb: String = conf.getString("hive.dws-db")
    val adsDb: String = conf.getString("hive.ads-db")
  }

  // ============ MySQL ============
  object Mysql {
    val url: String       = conf.getString("mysql.url")
    val driver: String    = conf.getString("mysql.driver")
    val user: String      = conf.getString("mysql.user")
    val password: String  = conf.getString("mysql.password")
    val batchSize: Int    = conf.getInt("mysql.batch-size")

    /** Spark DataFrameWriter.jdbc 用的连接属性 */
    def jdbcProperties: java.util.Properties = {
      val p = new java.util.Properties()
      p.setProperty("user", user)
      p.setProperty("password", password)
      p.setProperty("driver", driver)
      p.setProperty("batchsize", batchSize.toString)
      p
    }
  }

  // ============ Kafka ============
  object Kafka {
    val bootstrapServers: String = conf.getString("kafka.bootstrap-servers")
    val topicRaw: String         = conf.getString("kafka.topic-raw")
    val topicMetric: String      = conf.getString("kafka.topic-metric")
    val topicAlert: String       = conf.getString("kafka.topic-alert")
    val consumerGroup: String    = conf.getString("kafka.consumer-group")
    val startingOffsets: String  = conf.getString("kafka.starting-offsets")
  }

  // ============ Structured Streaming ============
  object Streaming {
    val triggerIntervalSeconds: Int = conf.getInt("streaming.trigger-interval-seconds")
    val watermarkMinutes: Int       = conf.getInt("streaming.watermark-minutes")
    val shufflePartitions: Int      = conf.getInt("streaming.shuffle-partitions")
  }

  // ============ MLlib ============
  object Ml {
    val profitAnomalyThreshold: Double = conf.getDouble("ml.profit-anomaly.threshold")
    val profitAnomalyWindowDays: Int   = conf.getInt("ml.profit-anomaly.window-days")
    val modelPotentialKmeansK: Int     = conf.getInt("ml.model-potential.kmeans-k")
    val modelPotentialMaxIter: Int     = conf.getInt("ml.model-potential.max-iter")
  }

  // ============ App ============
  object App {
    val timezone: String = conf.getString("app.timezone")
    val locale: String   = conf.getString("app.locale")
  }
}
