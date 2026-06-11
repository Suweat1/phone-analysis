package com.phone.etl.batch

import com.phone.etl.common.JobRunner
import com.phone.etl.config.PhoneConfig

/**
 * ODS → DWD：
 * - 字段透传 + 8 个派生指标 + 5 个时间/年龄分桶维度；
 * - 按 `dt='yyyy-MM'` 月分区 INSERT OVERWRITE 全量重算（开发期数据小，全量更简单）。
 *
 * 派生口径与 02-dwd.sql 头部注释严格一致。
 */
object OdsToDwdJob {

  def main(args: Array[String]): Unit = JobRunner.run("ods-to-dwd") { spark =>
    val ods = PhoneConfig.Hive.odsDb
    val dwd = PhoneConfig.Hive.dwdDb

    val sql =
      s"""
         |INSERT OVERWRITE TABLE $dwd.dwd_phone_sales PARTITION (dt)
         |SELECT
         |  sale_date,
         |  YEAR(sale_date)                                AS sale_year,
         |  MONTH(sale_date)                               AS sale_month,
         |  CAST(CEIL(MONTH(sale_date) / 3.0) AS INT)      AS sale_quarter,
         |  CAST(
         |    CASE WHEN DAYOFWEEK(sale_date) = 1 THEN 7
         |         ELSE DAYOFWEEK(sale_date) - 1 END
         |    AS INT
         |  )                                              AS sale_dow,
         |  brand,
         |  model,
         |  processor,
         |  camera_pixel_wan,
         |  battery_capacity_mah,
         |  screen_size_inch,
         |  storage_gb,
         |  refresh_rate_hz,
         |  os,
         |  COALESCE(NULLIF(TRIM(promotion), ''), '无')     AS promotion,
         |  user_rating,
         |
         |  unit_price,
         |  sales_qty,
         |  CAST(unit_price AS DOUBLE) * sales_qty         AS revenue,
         |
         |  production_cost,
         |  marketing_cost_total,
         |  logistics_cost,
         |  platform_commission_total,
         |  after_sales_cost,
         |  (COALESCE(production_cost, 0)
         |    + COALESCE(marketing_cost_total, 0)
         |    + COALESCE(logistics_cost, 0)
         |    + COALESCE(platform_commission_total, 0)
         |    + COALESCE(after_sales_cost, 0))             AS total_cost,
         |
         |  (CAST(unit_price AS DOUBLE) * sales_qty
         |    - (COALESCE(production_cost, 0)
         |       + COALESCE(marketing_cost_total, 0)
         |       + COALESCE(logistics_cost, 0)
         |       + COALESCE(platform_commission_total, 0)
         |       + COALESCE(after_sales_cost, 0)))         AS gross_profit,
         |
         |  CASE
         |    WHEN CAST(unit_price AS DOUBLE) * sales_qty = 0 THEN 0
         |    ELSE (CAST(unit_price AS DOUBLE) * sales_qty
         |          - (COALESCE(production_cost, 0)
         |             + COALESCE(marketing_cost_total, 0)
         |             + COALESCE(logistics_cost, 0)
         |             + COALESCE(platform_commission_total, 0)
         |             + COALESCE(after_sales_cost, 0)))
         |         / (CAST(unit_price AS DOUBLE) * sales_qty)
         |  END                                            AS gross_margin,
         |
         |  CASE
         |    WHEN CAST(unit_price AS DOUBLE) * sales_qty = 0 THEN 0
         |    ELSE COALESCE(marketing_cost_total, 0)
         |         / (CAST(unit_price AS DOUBLE) * sales_qty)
         |  END                                            AS marketing_cost_ratio,
         |
         |  CASE
         |    WHEN CAST(unit_price AS DOUBLE) * sales_qty = 0 THEN 0
         |    ELSE COALESCE(accessory_sales_total, 0)
         |         / (CAST(unit_price AS DOUBLE) * sales_qty)
         |  END                                            AS accessory_attach,
         |
         |  CASE
         |    WHEN CAST(unit_price AS DOUBLE) * sales_qty = 0 THEN 0
         |    ELSE COALESCE(warranty_total_sales, 0)
         |         / (CAST(unit_price AS DOUBLE) * sales_qty)
         |  END                                            AS warranty_attach,
         |
         |  case_sales,
         |  earphone_sales,
         |  charger_sales,
         |  cable_sales,
         |  screen_protector_sales,
         |  accessory_sales_total,
         |  warranty_1y_sales,
         |  warranty_2y_sales,
         |  accident_insurance_sales,
         |  screen_insurance_sales,
         |  warranty_total_sales,
         |
         |  user_city,
         |  user_age,
         |  CASE
         |    WHEN user_age IS NULL          THEN '未知'
         |    WHEN user_age < 20             THEN '<20'
         |    WHEN user_age < 30             THEN '20-30'
         |    WHEN user_age < 40             THEN '30-40'
         |    WHEN user_age < 50             THEN '40-50'
         |    ELSE                                '50+'
         |  END                                            AS age_group,
         |  user_gender,
         |  user_member_level,
         |
         |  DATE_FORMAT(sale_date, 'yyyy-MM')              AS dt
         |FROM $ods.ods_phone_sales
         |""".stripMargin

    spark.sql(sql)

    val cnt = spark.sql(s"SELECT COUNT(*) AS c FROM $dwd.dwd_phone_sales").head().getLong(0)
    println(s"[ods-to-dwd] dwd_phone_sales row count = $cnt")
  }
}
