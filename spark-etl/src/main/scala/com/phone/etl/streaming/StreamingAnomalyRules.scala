package com.phone.etl.streaming

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._

/**
 * 实时异常检测规则集（v1：纯阈值规则）。
 *
 * 设计：
 *  - 全部用 Spark SQL DataFrame 表达式，确保下推到 executor 并向量化执行；
 *  - 规则之间互斥：每条事件最多触发一个最严重的告警（按优先级排序）；
 *  - 规则参数 **不写死**，从 `PhoneConfig.Streaming` / 显式入参读，便于在 application.properties 调整；
 *  - 留 ML 扩展点：将来加 `ml/AnomalyMlRules.scala` 即可，本对象保持纯规则风格不动。
 *
 * 输入 schema（来自 KafkaSource 解析后的 SaleEvent）：
 *   event_id, event_time, brand, model, processor, storage_gb, promotion,
 *   unit_price, sales_qty, production_cost, marketing_cost_total,
 *   user_city, user_age, user_gender, user_member_level
 *
 * 输出 schema（多加 13 列派生字段 + 告警信息）：
 *   ...原始字段 +
 *   revenue, gross_profit, gross_margin, marketing_ratio,
 *   alert_type, alert_level, alert_title, alert_content, deviation
 *
 * 注意：未触发任何规则的事件，alert_type 为 null，后续 filter 掉即可。
 */
object StreamingAnomalyRules {

  // ─────────── 规则阈值（可被显式覆盖；默认与 application.properties 解耦） ────────────
  case class Thresholds(
    grossMarginLow: Double = 0.05,    // 毛利率 <5% 视为低
    grossMarginHigh: Double = 0.60,   // 毛利率 >60% 视为离谱（高概率脏数据）
    bigLossAmount: Double = -1000.0,  // 单笔毛利亏损金额阈值
    marketingRatioCap: Double = 0.40, // 营销费率 >40% 视为失控
    unitPriceLow: Int = 500,
    unitPriceHigh: Int = 12000,
    qtySpikeAtLeast: Int = 50
  )

  /**
   * 在原始事件 DataFrame 上派生指标字段 + 打告警标签。
   * 返回的 DataFrame 既包含未触发的事件（alert_type=null），也包含触发的；
   * 调用方决定要不要 `.filter(col("alert_type").isNotNull)`。
   */
  def tag(events: DataFrame, t: Thresholds = Thresholds()): DataFrame = {
    val withDerived = withDerivedMetrics(events)

    // 规则优先级：从高到低，先匹配先生效
    // 用嵌套 when().otherwise() 表达规则链
    val alertTypeCol = when(
      // R2: 单笔大额亏损（最高优先级）
      col("gross_profit") < lit(t.bigLossAmount) && col("revenue") > 0,
      lit("big_loss")
    ).when(
      // R3: 营销费率失控
      col("marketing_ratio") > lit(t.marketingRatioCap),
      lit("marketing_burnout")
    ).when(
      // R1: 毛利率离谱（双向）
      col("revenue") > 0 && (
        col("gross_margin") < lit(t.grossMarginLow) ||
        col("gross_margin") > lit(t.grossMarginHigh)
      ),
      lit("profit_anomaly")
    ).when(
      // R4: 单价离群
      col("unit_price") > lit(t.unitPriceHigh) ||
      col("unit_price") < lit(t.unitPriceLow),
      lit("price_outlier")
    ).when(
      // R5: 销量异常爆单
      col("sales_qty") >= lit(t.qtySpikeAtLeast),
      lit("qty_spike")
    ).otherwise(lit(null).cast("string"))

    val alertLevelCol = when(col("__alert_type") === "big_loss",          lit("high"))
                       .when(col("__alert_type") === "marketing_burnout", lit("mid"))
                       .when(col("__alert_type") === "profit_anomaly",    lit("mid"))
                       .when(col("__alert_type") === "price_outlier",     lit("low"))
                       .when(col("__alert_type") === "qty_spike",         lit("low"))
                       .otherwise(lit(null).cast("string"))

    // 中文标题模板（与 Vue AlertPanel 直接消费）
    val entity = concat_ws("·", col("brand"), col("model"))
    val titleCol = when(col("__alert_type") === "big_loss",
        concat(lit("【高】单笔大额亏损 "), entity, lit(" 毛利 "),
               round(col("gross_profit"), 0).cast("string"), lit(" 元"))
      ).when(col("__alert_type") === "marketing_burnout",
        concat(lit("【中】营销费率超阈 "), entity, lit(" "),
               (round(col("marketing_ratio") * 100, 1)).cast("string"), lit("%"))
      ).when(col("__alert_type") === "profit_anomaly",
        concat(lit("【中】毛利率异常 "), entity, lit(" "),
               (round(col("gross_margin") * 100, 1)).cast("string"), lit("%"))
      ).when(col("__alert_type") === "price_outlier",
        concat(lit("【低】客单价离群 "), entity, lit(" "),
               col("unit_price").cast("string"), lit(" 元"))
      ).when(col("__alert_type") === "qty_spike",
        concat(lit("【低】销量爆单 "), entity, lit(" "),
               col("sales_qty").cast("string"), lit(" 台"))
      ).otherwise(lit(null).cast("string"))

    val contentCol = concat_ws(" / ",
      concat(lit("渠道="), coalesce(col("promotion"), lit("无"))),
      concat(lit("单价="), col("unit_price").cast("string")),
      concat(lit("销量="), col("sales_qty").cast("string")),
      concat(lit("营收="), round(col("revenue"), 0).cast("string")),
      concat(lit("毛利="), round(col("gross_profit"), 0).cast("string")),
      concat(lit("毛利率="), round(col("gross_margin") * 100, 1).cast("string"), lit("%"))
    )

    // 偏差值（不同规则口径不同，统一为 0~1 的 magnitude）
    val deviationCol = when(col("__alert_type") === "big_loss",
        least(lit(1.0), abs(col("gross_profit")) / lit(5000.0))
      ).when(col("__alert_type") === "marketing_burnout",
        least(lit(1.0), col("marketing_ratio"))
      ).when(col("__alert_type") === "profit_anomaly",
        when(col("gross_margin") < lit(t.grossMarginLow),
             (lit(t.grossMarginLow) - col("gross_margin")) / lit(t.grossMarginLow))
        .otherwise((col("gross_margin") - lit(t.grossMarginHigh)) / lit(1.0 - t.grossMarginHigh))
      ).when(col("__alert_type") === "price_outlier",
        when(col("unit_price") > lit(t.unitPriceHigh),
             (col("unit_price") - lit(t.unitPriceHigh)) / lit(t.unitPriceHigh.toDouble))
        .otherwise((lit(t.unitPriceLow) - col("unit_price")) / lit(t.unitPriceLow.toDouble))
      ).when(col("__alert_type") === "qty_spike",
        least(lit(1.0), col("sales_qty") / lit(200.0))
      ).otherwise(lit(null).cast("double"))

    withDerived
      .withColumn("__alert_type", alertTypeCol)
      .withColumn("alert_type",   col("__alert_type"))
      .withColumn("alert_level",  alertLevelCol)
      .withColumn("alert_title",  titleCol)
      .withColumn("alert_content", contentCol)
      .withColumn("deviation",    deviationCol)
      .drop("__alert_type")
  }

  /** 派生指标：revenue/gross_profit/gross_margin/marketing_ratio。空安全。 */
  private[streaming] def withDerivedMetrics(df: DataFrame): DataFrame = {
    val revenueExpr = coalesce(col("unit_price"), lit(0)) * coalesce(col("sales_qty"), lit(0))
    val grossProfitExpr = revenueExpr -
      coalesce(col("production_cost"), lit(0.0)) -
      coalesce(col("marketing_cost_total"), lit(0.0))

    df.withColumn("revenue",         revenueExpr.cast("double"))
      .withColumn("gross_profit",    grossProfitExpr.cast("double"))
      .withColumn("gross_margin",    when(col("revenue") > 0, col("gross_profit") / col("revenue")).otherwise(lit(0.0)))
      .withColumn("marketing_ratio", when(col("revenue") > 0,
        coalesce(col("marketing_cost_total"), lit(0.0)) / col("revenue")).otherwise(lit(0.0)))
  }
}
