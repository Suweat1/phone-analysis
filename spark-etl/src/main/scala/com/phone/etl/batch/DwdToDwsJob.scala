package com.phone.etl.batch

import com.phone.etl.common.JobRunner
import com.phone.etl.config.PhoneConfig

/**
 * DWD → DWS：6 张聚合宽表（日 / 月 / 品牌 / 机型 / 渠道 / 细分市场）。
 *
 * 所有指标统一口径（与 03-dws.sql 注释一致）：
 *   total_revenue       = SUM(revenue)
 *   total_qty           = SUM(sales_qty)
 *   avg_unit_price      = total_revenue / total_qty
 *   total_cost          = SUM(total_cost)
 *   total_marketing     = SUM(marketing_cost_total)
 *   total_gross_profit  = SUM(gross_profit)
 *   gross_margin        = total_gross_profit / total_revenue
 *   marketing_ratio     = total_marketing / total_revenue
 *   accessory_sales     = SUM(accessory_sales_total)
 *   warranty_sales      = SUM(warranty_total_sales)
 *   order_cnt           = COUNT(*)
 */
object DwdToDwsJob {

  def main(args: Array[String]): Unit = JobRunner.run("dwd-to-dws") { spark =>
    val dwd = PhoneConfig.Hive.dwdDb
    val dws = PhoneConfig.Hive.dwsDb

    // 公共 SELECT 列表（聚合指标） —— 用 group key 拼装到不同 GROUP BY
    def agg(groupKeys: String, extra: String = ""): String =
      s"""
         |  $groupKeys,
         |  SUM(revenue)                                   AS total_revenue,
         |  CAST(SUM(sales_qty) AS BIGINT)                 AS total_qty,
         |  CASE WHEN SUM(sales_qty) = 0 THEN 0
         |       ELSE SUM(revenue) / SUM(sales_qty)
         |  END                                            AS avg_unit_price,
         |  SUM(total_cost)                                AS total_cost,
         |  SUM(marketing_cost_total)                      AS total_marketing,
         |  SUM(gross_profit)                              AS total_gross_profit,
         |  CASE WHEN SUM(revenue) = 0 THEN 0
         |       ELSE SUM(gross_profit) / SUM(revenue)
         |  END                                            AS gross_margin,
         |  CASE WHEN SUM(revenue) = 0 THEN 0
         |       ELSE SUM(marketing_cost_total) / SUM(revenue)
         |  END                                            AS marketing_ratio,
         |  SUM(accessory_sales_total)                     AS accessory_sales,
         |  SUM(warranty_total_sales)                      AS warranty_sales,
         |  CAST(COUNT(*) AS BIGINT)                       AS order_cnt
         |  $extra
         |""".stripMargin

    // 1) 日聚合
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $dws.dws_sales_daily
         |SELECT ${agg("sale_date")}
         |FROM $dwd.dwd_phone_sales
         |GROUP BY sale_date
         |""".stripMargin)

    // 2) 月聚合
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $dws.dws_sales_monthly
         |SELECT
         |  sale_year,
         |  sale_month,
         |  CONCAT(LPAD(CAST(sale_year AS STRING), 4, '0'), '-',
         |         LPAD(CAST(sale_month AS STRING), 2, '0'))    AS sale_ym,
         |  SUM(revenue)                                        AS total_revenue,
         |  CAST(SUM(sales_qty) AS BIGINT)                      AS total_qty,
         |  CASE WHEN SUM(sales_qty) = 0 THEN 0
         |       ELSE SUM(revenue) / SUM(sales_qty)
         |  END                                                 AS avg_unit_price,
         |  SUM(total_cost)                                     AS total_cost,
         |  SUM(marketing_cost_total)                           AS total_marketing,
         |  SUM(gross_profit)                                   AS total_gross_profit,
         |  CASE WHEN SUM(revenue) = 0 THEN 0
         |       ELSE SUM(gross_profit) / SUM(revenue)
         |  END                                                 AS gross_margin,
         |  CASE WHEN SUM(revenue) = 0 THEN 0
         |       ELSE SUM(marketing_cost_total) / SUM(revenue)
         |  END                                                 AS marketing_ratio,
         |  SUM(accessory_sales_total)                          AS accessory_sales,
         |  SUM(warranty_total_sales)                           AS warranty_sales,
         |  CAST(COUNT(*) AS BIGINT)                            AS order_cnt
         |FROM $dwd.dwd_phone_sales
         |GROUP BY sale_year, sale_month
         |""".stripMargin)

    // 3) 品牌聚合
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $dws.dws_sales_by_brand
         |SELECT ${agg("brand", ", CAST(COUNT(DISTINCT model) AS BIGINT) AS model_cnt")}
         |FROM $dwd.dwd_phone_sales
         |GROUP BY brand
         |""".stripMargin)

    // 4) 机型聚合
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $dws.dws_sales_by_model
         |SELECT ${agg("brand, model", ", AVG(CAST(user_rating AS DOUBLE)) AS avg_user_rating")}
         |FROM $dwd.dwd_phone_sales
         |GROUP BY brand, model
         |""".stripMargin)

    // 5) 渠道聚合（只取部分指标，与 DDL 对齐）
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $dws.dws_sales_by_channel
         |SELECT
         |  promotion,
         |  SUM(revenue)                                   AS total_revenue,
         |  CAST(SUM(sales_qty) AS BIGINT)                 AS total_qty,
         |  CASE WHEN SUM(sales_qty) = 0 THEN 0
         |       ELSE SUM(revenue) / SUM(sales_qty)
         |  END                                            AS avg_unit_price,
         |  SUM(total_cost)                                AS total_cost,
         |  SUM(marketing_cost_total)                      AS total_marketing,
         |  SUM(gross_profit)                              AS total_gross_profit,
         |  CASE WHEN SUM(revenue) = 0 THEN 0
         |       ELSE SUM(gross_profit) / SUM(revenue)
         |  END                                            AS gross_margin,
         |  CASE WHEN SUM(revenue) = 0 THEN 0
         |       ELSE SUM(marketing_cost_total) / SUM(revenue)
         |  END                                            AS marketing_ratio,
         |  CAST(COUNT(*) AS BIGINT)                       AS order_cnt
         |FROM $dwd.dwd_phone_sales
         |GROUP BY promotion
         |""".stripMargin)

    // 6) 细分市场聚合
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $dws.dws_sales_by_segment
         |SELECT
         |  brand,
         |  user_city,
         |  age_group,
         |  user_member_level,
         |  SUM(revenue)                                   AS total_revenue,
         |  CAST(SUM(sales_qty) AS BIGINT)                 AS total_qty,
         |  CASE WHEN SUM(sales_qty) = 0 THEN 0
         |       ELSE SUM(revenue) / SUM(sales_qty)
         |  END                                            AS avg_unit_price,
         |  SUM(total_cost)                                AS total_cost,
         |  SUM(gross_profit)                              AS total_gross_profit,
         |  CASE WHEN SUM(revenue) = 0 THEN 0
         |       ELSE SUM(gross_profit) / SUM(revenue)
         |  END                                            AS gross_margin,
         |  CAST(COUNT(*) AS BIGINT)                       AS order_cnt
         |FROM $dwd.dwd_phone_sales
         |GROUP BY brand, user_city, age_group, user_member_level
         |""".stripMargin)

    println(s"[dwd-to-dws] 6 张表已刷新")
  }
}
