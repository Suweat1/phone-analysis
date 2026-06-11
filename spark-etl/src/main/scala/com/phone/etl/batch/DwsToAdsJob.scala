package com.phone.etl.batch

import com.phone.etl.common.JobRunner
import com.phone.etl.config.PhoneConfig

/**
 * DWS → ADS：8 张看板结果表。
 *
 * 算法细节
 * --------
 * 1) ads_profit_anomaly
 *    - 用窗口算 30 日滚动毛利率均值，偏差超过 PhoneConfig.Ml.profitAnomalyThreshold 视为异常；
 *    - anomaly_level：|dev| >= 2*th 'high'；>= th 'mid'；>= 0.5*th 'low'；else 'normal'。
 *
 * 2) ads_metric_trend
 *    - 长表（每日 × 8 个指标），用 LAG 算 MoM、YoY（去年同日）。
 *
 * 3) ads_low_contrib_model / channel
 *    - 按 total_gross_profit ASC 取 TopN（=配置项 lowContribTopN，固定 10）。
 *
 * 4) ads_profit_decomp
 *    - 经典价量分解：
 *        contribution(unit_price) =  ΔP * AVG(Q)
 *        contribution(sales_qty)  =  ΔQ * AVG(P)
 *        contribution(cost)       = -ΔCost
 *        contribution(marketing)  = -ΔMarketing
 *        contribution(other)      = profit_delta - 上 4 项
 *    - 同时输出 MoM 与 YoY 两种 compare_type。
 *
 * 5) ads_high_value_model
 *    - 综合评分：0.4*norm(margin) + 0.3*norm(revenue) + 0.2*norm(growth) + 0.1*norm(rating)
 *    - growth = 近一月销量 / 上一月销量 - 1（缺失视为 0）。
 *
 * 6) ads_segment_top_margin
 *    - 按 gross_margin DESC 取 TopN（=10），并要求 order_cnt >= 5 避免噪音。
 *
 * 7) ads_growth_potential
 *    - 机型 × 月 × 渠道 三维聚合后打分：
 *      potential = 0.5*norm(growth) + 0.3*(1 - norm(marketing_ratio)) + 0.2*norm(gross_margin)
 *    - 仅保留 gross_margin > 0 的行。
 */
object DwsToAdsJob {

  // 用配置而非魔法数；不过 ads_*_top_n 阈值来自 app 的 dashboard 配置，
  // ETL 端用本地常量（与 application.yml 数值保持一致即可）。
  private val TopN: Int = 10

  def main(args: Array[String]): Unit = JobRunner.run("dws-to-ads") { spark =>
    val dwd = PhoneConfig.Hive.dwdDb
    val dws = PhoneConfig.Hive.dwsDb
    val ads = PhoneConfig.Hive.adsDb
    val th  = PhoneConfig.Ml.profitAnomalyThreshold

    // ============================================================
    // 1) ads_profit_anomaly
    // ============================================================
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $ads.ads_profit_anomaly
         |WITH base AS (
         |  SELECT
         |    sale_date,
         |    gross_margin,
         |    AVG(gross_margin) OVER (
         |      ORDER BY sale_date
         |      ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
         |    ) AS rolling_margin_30d
         |  FROM $dws.dws_sales_daily
         |)
         |SELECT
         |  sale_date,
         |  gross_margin,
         |  rolling_margin_30d,
         |  CASE WHEN rolling_margin_30d = 0 THEN 0
         |       ELSE (gross_margin - rolling_margin_30d) / rolling_margin_30d
         |  END                                          AS deviation_ratio,
         |  CASE
         |    WHEN rolling_margin_30d = 0 THEN false
         |    WHEN ABS((gross_margin - rolling_margin_30d) / rolling_margin_30d) >= $th
         |      THEN true
         |    ELSE false
         |  END                                          AS is_anomaly,
         |  CASE
         |    WHEN rolling_margin_30d = 0 THEN 'normal'
         |    WHEN ABS((gross_margin - rolling_margin_30d) / rolling_margin_30d) >= 2 * $th
         |      THEN 'high'
         |    WHEN ABS((gross_margin - rolling_margin_30d) / rolling_margin_30d) >= $th
         |      THEN 'mid'
         |    WHEN ABS((gross_margin - rolling_margin_30d) / rolling_margin_30d) >= 0.5 * $th
         |      THEN 'low'
         |    ELSE 'normal'
         |  END                                          AS anomaly_level
         |FROM base
         |""".stripMargin)

    // ============================================================
    // 2) ads_metric_trend
    // ============================================================
    val metricLong =
      s"""
         |SELECT sale_date, 'revenue'        AS metric_code, '营收'         AS metric_name_cn, total_revenue       AS metric_value FROM $dws.dws_sales_daily
         |UNION ALL
         |SELECT sale_date, 'qty'            AS metric_code, '销量'         AS metric_name_cn, CAST(total_qty AS DOUBLE)       FROM $dws.dws_sales_daily
         |UNION ALL
         |SELECT sale_date, 'unit_price'     AS metric_code, '平均客单价'   AS metric_name_cn, avg_unit_price                  FROM $dws.dws_sales_daily
         |UNION ALL
         |SELECT sale_date, 'cost'           AS metric_code, '总成本'       AS metric_name_cn, total_cost                      FROM $dws.dws_sales_daily
         |UNION ALL
         |SELECT sale_date, 'marketing'      AS metric_code, '营销费用'     AS metric_name_cn, total_marketing                 FROM $dws.dws_sales_daily
         |UNION ALL
         |SELECT sale_date, 'gross_profit'   AS metric_code, '毛利'         AS metric_name_cn, total_gross_profit              FROM $dws.dws_sales_daily
         |UNION ALL
         |SELECT sale_date, 'gross_margin'   AS metric_code, '毛利率'       AS metric_name_cn, gross_margin                    FROM $dws.dws_sales_daily
         |UNION ALL
         |SELECT sale_date, 'marketing_ratio' AS metric_code, '营销费用率'  AS metric_name_cn, marketing_ratio                 FROM $dws.dws_sales_daily
         |""".stripMargin

    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $ads.ads_metric_trend
         |WITH metric_long AS ($metricLong),
         |with_lag AS (
         |  SELECT
         |    sale_date,
         |    metric_code,
         |    metric_name_cn,
         |    metric_value,
         |    LAG(metric_value, 1) OVER (PARTITION BY metric_code ORDER BY sale_date) AS prev_day,
         |    LAG(metric_value, 365) OVER (PARTITION BY metric_code ORDER BY sale_date) AS prev_year
         |  FROM metric_long
         |)
         |SELECT
         |  sale_date,
         |  metric_code,
         |  metric_name_cn,
         |  metric_value,
         |  CASE WHEN prev_day IS NULL OR prev_day = 0 THEN NULL
         |       ELSE (metric_value - prev_day) / prev_day
         |  END                                          AS mom_ratio,
         |  CASE WHEN prev_year IS NULL OR prev_year = 0 THEN NULL
         |       ELSE (metric_value - prev_year) / prev_year
         |  END                                          AS yoy_ratio
         |FROM with_lag
         |""".stripMargin)

    // ============================================================
    // 3) ads_low_contrib_model
    // ============================================================
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $ads.ads_low_contrib_model
         |WITH total AS (SELECT SUM(total_gross_profit) AS gp FROM $dws.dws_sales_by_model)
         |SELECT
         |  CAST(ROW_NUMBER() OVER (ORDER BY total_gross_profit ASC) AS INT) AS rank_no,
         |  brand,
         |  model,
         |  total_revenue,
         |  total_gross_profit,
         |  gross_margin,
         |  CASE WHEN (SELECT gp FROM total) = 0 THEN 0
         |       ELSE total_gross_profit / (SELECT gp FROM total)
         |  END                                          AS contribution_ratio
         |FROM $dws.dws_sales_by_model
         |ORDER BY total_gross_profit ASC
         |LIMIT $TopN
         |""".stripMargin)

    // ============================================================
    // 4) ads_low_contrib_channel
    // ============================================================
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $ads.ads_low_contrib_channel
         |WITH total AS (SELECT SUM(total_gross_profit) AS gp FROM $dws.dws_sales_by_channel)
         |SELECT
         |  CAST(ROW_NUMBER() OVER (ORDER BY total_gross_profit ASC) AS INT) AS rank_no,
         |  promotion,
         |  total_revenue,
         |  total_gross_profit,
         |  gross_margin,
         |  marketing_ratio,
         |  CASE WHEN (SELECT gp FROM total) = 0 THEN 0
         |       ELSE total_gross_profit / (SELECT gp FROM total)
         |  END                                          AS contribution_ratio
         |FROM $dws.dws_sales_by_channel
         |ORDER BY total_gross_profit ASC
         |LIMIT $TopN
         |""".stripMargin)

    // ============================================================
    // 5) ads_profit_decomp（MoM + YoY 两套）
    // ============================================================
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $ads.ads_profit_decomp
         |WITH monthly AS (
         |  SELECT
         |    sale_ym,
         |    total_revenue, total_qty, avg_unit_price,
         |    total_cost, total_marketing, total_gross_profit
         |  FROM $dws.dws_sales_monthly
         |),
         |paired AS (
         |  SELECT
         |    m.sale_ym                                 AS curr_ym,
         |    'mom'                                     AS compare_type,
         |    LAG(m.sale_ym)            OVER (ORDER BY m.sale_ym) AS base_ym,
         |    m.total_gross_profit                      AS p_curr,
         |    LAG(m.total_gross_profit) OVER (ORDER BY m.sale_ym) AS p_base,
         |    m.avg_unit_price                          AS up_curr,
         |    LAG(m.avg_unit_price)     OVER (ORDER BY m.sale_ym) AS up_base,
         |    m.total_qty                               AS q_curr,
         |    LAG(m.total_qty)          OVER (ORDER BY m.sale_ym) AS q_base,
         |    m.total_cost                              AS c_curr,
         |    LAG(m.total_cost)         OVER (ORDER BY m.sale_ym) AS c_base,
         |    m.total_marketing                         AS mk_curr,
         |    LAG(m.total_marketing)    OVER (ORDER BY m.sale_ym) AS mk_base
         |  FROM monthly m
         |
         |  UNION ALL
         |
         |  SELECT
         |    m.sale_ym                                 AS curr_ym,
         |    'yoy'                                     AS compare_type,
         |    LAG(m.sale_ym, 12)        OVER (ORDER BY m.sale_ym) AS base_ym,
         |    m.total_gross_profit                      AS p_curr,
         |    LAG(m.total_gross_profit, 12) OVER (ORDER BY m.sale_ym) AS p_base,
         |    m.avg_unit_price                          AS up_curr,
         |    LAG(m.avg_unit_price, 12)     OVER (ORDER BY m.sale_ym) AS up_base,
         |    m.total_qty                               AS q_curr,
         |    LAG(m.total_qty, 12)          OVER (ORDER BY m.sale_ym) AS q_base,
         |    m.total_cost                              AS c_curr,
         |    LAG(m.total_cost, 12)         OVER (ORDER BY m.sale_ym) AS c_base,
         |    m.total_marketing                         AS mk_curr,
         |    LAG(m.total_marketing, 12)    OVER (ORDER BY m.sale_ym) AS mk_base
         |  FROM monthly m
         |),
         |decomp AS (
         |  SELECT
         |    curr_ym, compare_type, base_ym,
         |    p_curr, p_base,
         |    (p_curr - p_base)                          AS p_delta,
         |    (up_curr - up_base) * ((q_curr + q_base) / 2.0)  AS c_unit_price,
         |    (q_curr - q_base) * ((up_curr + up_base) / 2.0)  AS c_sales_qty,
         |    -(c_curr - c_base)                         AS c_cost,
         |    -(mk_curr - mk_base)                       AS c_marketing
         |  FROM paired
         |  WHERE base_ym IS NOT NULL
         |),
         |decomp_full AS (
         |  SELECT
         |    curr_ym, compare_type, base_ym, p_curr, p_base, p_delta,
         |    c_unit_price, c_sales_qty, c_cost, c_marketing,
         |    (p_delta - (c_unit_price + c_sales_qty + c_cost + c_marketing)) AS c_other
         |  FROM decomp
         |)
         |SELECT
         |  curr_ym                                      AS sale_ym,
         |  compare_type,
         |  base_ym,
         |  p_curr                                       AS profit_curr,
         |  p_base                                       AS profit_base,
         |  p_delta                                      AS profit_delta,
         |  factor,
         |  CASE factor
         |    WHEN 'unit_price' THEN '客单价'
         |    WHEN 'sales_qty'  THEN '销量'
         |    WHEN 'cost'       THEN '成本'
         |    WHEN 'marketing'  THEN '营销费用'
         |    WHEN 'other'      THEN '其他'
         |  END                                          AS factor_name_cn,
         |  contribution,
         |  CASE WHEN p_delta = 0 THEN 0 ELSE contribution / p_delta END AS contribution_pct
         |FROM decomp_full
         |LATERAL VIEW EXPLODE(
         |  MAP(
         |    'unit_price', c_unit_price,
         |    'sales_qty',  c_sales_qty,
         |    'cost',       c_cost,
         |    'marketing',  c_marketing,
         |    'other',      c_other
         |  )
         |) t AS factor, contribution
         |""".stripMargin)

    // ============================================================
    // 6) ads_high_value_model：综合机会评分
    // ============================================================
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $ads.ads_high_value_model
         |WITH growth AS (
         |  -- 取最后两个月的销量比值作为 qty_growth_ratio
         |  SELECT
         |    brand, model,
         |    SUM(CASE WHEN rn = 1 THEN total_qty ELSE 0 END) AS q_curr,
         |    SUM(CASE WHEN rn = 2 THEN total_qty ELSE 0 END) AS q_prev
         |  FROM (
         |    SELECT
         |      brand, model, sale_ym, total_qty,
         |      ROW_NUMBER() OVER (PARTITION BY brand, model ORDER BY sale_ym DESC) AS rn
         |    FROM (
         |      SELECT brand, model,
         |             CONCAT(LPAD(CAST(sale_year AS STRING), 4, '0'), '-',
         |                    LPAD(CAST(sale_month AS STRING), 2, '0')) AS sale_ym,
         |             SUM(sales_qty) AS total_qty
         |      FROM $dwd.dwd_phone_sales
         |      GROUP BY brand, model, sale_year, sale_month
         |    )
         |  )
         |  WHERE rn <= 2
         |  GROUP BY brand, model
         |),
         |joined AS (
         |  SELECT
         |    m.brand, m.model,
         |    m.total_revenue, m.total_gross_profit,
         |    m.gross_margin, m.avg_user_rating,
         |    CASE WHEN g.q_prev IS NULL OR g.q_prev = 0 THEN 0
         |         ELSE (g.q_curr - g.q_prev) / CAST(g.q_prev AS DOUBLE)
         |    END AS qty_growth_ratio
         |  FROM $dws.dws_sales_by_model m
         |  LEFT JOIN growth g ON m.brand = g.brand AND m.model = g.model
         |),
         |stats AS (
         |  SELECT
         |    MIN(gross_margin)     AS gm_min, MAX(gross_margin)     AS gm_max,
         |    MIN(total_revenue)    AS rv_min, MAX(total_revenue)    AS rv_max,
         |    MIN(qty_growth_ratio) AS gr_min, MAX(qty_growth_ratio) AS gr_max,
         |    MIN(avg_user_rating)  AS rt_min, MAX(avg_user_rating)  AS rt_max
         |  FROM joined
         |),
         |scored AS (
         |  SELECT
         |    j.*,
         |    0.4 * (CASE WHEN (s.gm_max - s.gm_min) = 0 THEN 0
         |                ELSE (j.gross_margin     - s.gm_min) / (s.gm_max - s.gm_min) END)
         |  + 0.3 * (CASE WHEN (s.rv_max - s.rv_min) = 0 THEN 0
         |                ELSE (j.total_revenue    - s.rv_min) / (s.rv_max - s.rv_min) END)
         |  + 0.2 * (CASE WHEN (s.gr_max - s.gr_min) = 0 THEN 0
         |                ELSE (j.qty_growth_ratio - s.gr_min) / (s.gr_max - s.gr_min) END)
         |  + 0.1 * (CASE WHEN (s.rt_max - s.rt_min) = 0 THEN 0
         |                ELSE (j.avg_user_rating  - s.rt_min) / (s.rt_max - s.rt_min) END)
         |  AS opportunity_score
         |  FROM joined j CROSS JOIN stats s
         |)
         |SELECT
         |  CAST(ROW_NUMBER() OVER (ORDER BY opportunity_score DESC) AS INT) AS rank_no,
         |  brand,
         |  model,
         |  total_revenue,
         |  total_gross_profit,
         |  gross_margin,
         |  avg_user_rating,
         |  qty_growth_ratio,
         |  opportunity_score
         |FROM scored
         |ORDER BY opportunity_score DESC
         |LIMIT $TopN
         |""".stripMargin)

    // ============================================================
    // 7) ads_segment_top_margin
    // ============================================================
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $ads.ads_segment_top_margin
         |SELECT
         |  CAST(ROW_NUMBER() OVER (ORDER BY gross_margin DESC) AS INT) AS rank_no,
         |  brand,
         |  user_city,
         |  age_group,
         |  user_member_level,
         |  total_revenue,
         |  total_gross_profit,
         |  gross_margin,
         |  order_cnt,
         |  CONCAT_WS('·', brand, user_city, age_group, user_member_level) AS segment_label_cn
         |FROM $dws.dws_sales_by_segment
         |WHERE order_cnt >= 5
         |ORDER BY gross_margin DESC
         |LIMIT $TopN
         |""".stripMargin)

    // ============================================================
    // 8) ads_growth_potential（机型 × 月 × 渠道）
    // ============================================================
    spark.sql(
      s"""
         |INSERT OVERWRITE TABLE $ads.ads_growth_potential
         |WITH agg AS (
         |  SELECT
         |    brand, model,
         |    CONCAT(LPAD(CAST(sale_year AS STRING), 4, '0'), '-',
         |           LPAD(CAST(sale_month AS STRING), 2, '0')) AS sale_ym,
         |    promotion,
         |    SUM(revenue)                                AS total_revenue,
         |    SUM(gross_profit)                           AS total_gross_profit,
         |    SUM(sales_qty)                              AS total_qty,
         |    SUM(marketing_cost_total)                   AS total_marketing,
         |    CASE WHEN SUM(revenue) = 0 THEN 0
         |         ELSE SUM(gross_profit) / SUM(revenue)
         |    END                                         AS gross_margin,
         |    CASE WHEN SUM(revenue) = 0 THEN 0
         |         ELSE SUM(marketing_cost_total) / SUM(revenue)
         |    END                                         AS marketing_ratio
         |  FROM $dwd.dwd_phone_sales
         |  GROUP BY brand, model, sale_year, sale_month, promotion
         |),
         |with_growth AS (
         |  SELECT
         |    a.*,
         |    LAG(total_qty) OVER (PARTITION BY brand, model, promotion ORDER BY sale_ym) AS q_prev
         |  FROM agg a
         |),
         |with_ratio AS (
         |  SELECT
         |    *,
         |    CASE WHEN q_prev IS NULL OR q_prev = 0 THEN 0
         |         ELSE (total_qty - q_prev) / CAST(q_prev AS DOUBLE)
         |    END AS qty_growth_ratio
         |  FROM with_growth
         |  WHERE gross_margin > 0
         |),
         |stats AS (
         |  SELECT
         |    MIN(qty_growth_ratio) AS gr_min, MAX(qty_growth_ratio) AS gr_max,
         |    MIN(marketing_ratio)  AS mr_min, MAX(marketing_ratio)  AS mr_max,
         |    MIN(gross_margin)     AS gm_min, MAX(gross_margin)     AS gm_max
         |  FROM with_ratio
         |),
         |scored AS (
         |  SELECT
         |    w.*,
         |    0.5 * (CASE WHEN (s.gr_max - s.gr_min) = 0 THEN 0
         |                ELSE (w.qty_growth_ratio - s.gr_min) / (s.gr_max - s.gr_min) END)
         |  + 0.3 * (CASE WHEN (s.mr_max - s.mr_min) = 0 THEN 0
         |                ELSE 1 - (w.marketing_ratio  - s.mr_min) / (s.mr_max - s.mr_min) END)
         |  + 0.2 * (CASE WHEN (s.gm_max - s.gm_min) = 0 THEN 0
         |                ELSE (w.gross_margin     - s.gm_min) / (s.gm_max - s.gm_min) END)
         |  AS potential_score
         |  FROM with_ratio w CROSS JOIN stats s
         |)
         |SELECT
         |  CAST(ROW_NUMBER() OVER (ORDER BY potential_score DESC) AS INT) AS rank_no,
         |  brand,
         |  model,
         |  sale_ym,
         |  promotion,
         |  total_revenue,
         |  total_gross_profit,
         |  gross_margin,
         |  marketing_ratio,
         |  qty_growth_ratio,
         |  potential_score
         |FROM scored
         |ORDER BY potential_score DESC
         |LIMIT $TopN
         |""".stripMargin)

    println("[dws-to-ads] 8 张 ADS 表已刷新")
  }
}
