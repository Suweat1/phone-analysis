package com.phone.etl.batch

import com.phone.etl.common.{ColumnMapping, JobRunner}
import com.phone.etl.config.PhoneConfig
import org.apache.spark.sql.{Row, SaveMode, SparkSession}
import org.apache.spark.sql.types.{StringType, StructField, StructType}

import scala.collection.JavaConverters._

/**
 * 把 [[ColumnMapping]] 的字段映射刷到 MySQL `ads_column_dict`。
 * SpringBoot 后续从这张表给前端做中英展示映射，避免三处维护。
 *
 * 字典表只有 4 列：column_en / column_cn / layer / category（见 05-mysql-ads.sql）。
 *
 * 由 [[InitSchemaJob]] 在 DDL 部署完后自动调用一次；
 * 后续如果改了 ColumnMapping，可单独 spark-submit 重跑本 Job 同步。
 */
object ColumnDictJob {

  def main(args: Array[String]): Unit = JobRunner.run("column-dict") { spark =>
    write(spark)
  }

  /** 供 InitSchemaJob 复用 */
  def write(spark: SparkSession): Unit = {
    val rows = ColumnMapping.enToCn.toSeq.map { case (en, cn) =>
      Row(
        en,
        cn,
        ColumnMapping.enToLayer.getOrElse(en, "unknown"),
        ColumnMapping.enToCategory.getOrElse(en, "derived")
      )
    }

    val schema = StructType(Array(
      StructField("column_en", StringType, nullable = false),
      StructField("column_cn", StringType, nullable = false),
      StructField("layer",     StringType, nullable = true),
      StructField("category",  StringType, nullable = true)
    ))

    val df = spark.createDataFrame(rows.asJava, schema)
    df.write
      .mode(SaveMode.Overwrite)
      .option("truncate", "true")
      .jdbc(PhoneConfig.Mysql.url, "ads_column_dict", PhoneConfig.Mysql.jdbcProperties)

    println(s"[column-dict] ${rows.size} rows -> MySQL.ads_column_dict OK")
  }
}
