package com.phone.etl.common

/**
 * 字段中英映射 —— Scala 端的真实源镜像。
 *
 * 与 `scripts/column_mapping.py` 严格保持一致：
 * - Python 端：预处理时把中文列名重命名为英文；
 * - Scala 端：通过本对象把字典刷进 MySQL `ads_column_dict`；
 * - Java/JS 端：从 `ads_column_dict` 反查中文展示。
 *
 * 若新增/修改字段，<b>必须同步更新</b> `scripts/column_mapping.py` 与本文件。
 */
object ColumnMapping {

  /** 普通字段 中文 → 英文 */
  val cnToEn: Map[String, String] = Map(
    "日期"           -> "sale_date",
    "品牌"           -> "brand",
    "型号"           -> "model",
    "处理器性能"     -> "processor",
    "操作系统"       -> "os",
    "促销活动"       -> "promotion",
    "用户评价"       -> "user_rating",
    "客单价"         -> "unit_price",
    "销量"           -> "sales_qty",
    "生产成本"       -> "production_cost",
    "总营销成本"     -> "marketing_cost_total",
    "物流成本"       -> "logistics_cost",
    "总平台佣金"     -> "platform_commission_total",
    "售后服务成本"   -> "after_sales_cost",
    "手机壳销售额"   -> "case_sales",
    "耳机销售额"     -> "earphone_sales",
    "充电器销售额"   -> "charger_sales",
    "数据线销售额"   -> "cable_sales",
    "保护膜销售额"   -> "screen_protector_sales",
    "总配件销售额"   -> "accessory_sales_total",
    "1年延保销售额"  -> "warranty_1y_sales",
    "2年延保销售额"  -> "warranty_2y_sales",
    "意外险销售额"   -> "accident_insurance_sales",
    "碎屏险销售额"   -> "screen_insurance_sales",
    "总延保服务额"   -> "warranty_total_sales",
    "用户所在城市"   -> "user_city",
    "用户年龄"       -> "user_age",
    "用户性别"       -> "user_gender",
    "用户会员等级"   -> "user_member_level"
  )

  /** 拆单位字段：英文 → 中文展示名 */
  val specUnitEnToCnDisplay: Map[String, String] = Map(
    "camera_pixel_wan"     -> "摄像头像素(万)",
    "battery_capacity_mah" -> "电池续航(mAh)",
    "screen_size_inch"     -> "屏幕尺寸(英寸)",
    "storage_gb"           -> "存储容量(GB)",
    "refresh_rate_hz"      -> "屏幕刷新率(Hz)"
  )

  /** Spark 派生 / 聚合列：英文 → 中文展示名 */
  val derivedEnToCn: Map[String, String] = Map(
    "revenue"               -> "营收",
    "total_cost"            -> "总成本",
    "gross_profit"          -> "毛利",
    "gross_margin"          -> "毛利率",
    "marketing_cost_ratio"  -> "营销费用率",
    "accessory_attach"      -> "配件附加率",
    "warranty_attach"       -> "延保附加率",
    "sale_year"             -> "年",
    "sale_month"            -> "月",
    "sale_quarter"          -> "季度",
    "sale_dow"              -> "星期",
    "sale_ym"               -> "年月",
    "age_group"             -> "年龄段",
    "total_revenue"         -> "营收合计",
    "total_qty"             -> "销量合计",
    "avg_unit_price"        -> "平均客单价",
    "total_marketing"       -> "营销费用合计",
    "total_gross_profit"    -> "毛利合计",
    "marketing_ratio"       -> "营销费用率",
    "accessory_sales"       -> "配件销售合计",
    "warranty_sales"        -> "延保销售合计",
    "order_cnt"             -> "订单数",
    "model_cnt"             -> "机型数",
    "avg_user_rating"       -> "平均用户评价",
    "qty_growth_ratio"      -> "销量环比增速",
    "opportunity_score"     -> "机会评分",
    "potential_score"       -> "潜力评分",
    "contribution_ratio"    -> "贡献占比",
    "rolling_margin_30d"    -> "30日滚动毛利率",
    "deviation_ratio"       -> "偏差率",
    "is_anomaly"            -> "是否异常",
    "anomaly_level"         -> "异常级别",
    "metric_code"           -> "指标代号",
    "metric_value"          -> "指标值",
    "mom_ratio"             -> "环比",
    "yoy_ratio"             -> "同比"
  )

  /** 完整 英文 → 中文（供前端反查） */
  val enToCn: Map[String, String] =
    cnToEn.map(_.swap) ++ specUnitEnToCnDisplay ++ derivedEnToCn

  /** 字段所属数仓层 */
  val enToLayer: Map[String, String] = {
    val odsCols = cnToEn.values.toSet ++ specUnitEnToCnDisplay.keySet
    val dwdCols = Set(
      "revenue", "total_cost", "gross_profit", "gross_margin",
      "marketing_cost_ratio", "accessory_attach", "warranty_attach",
      "sale_year", "sale_month", "sale_quarter", "sale_dow", "age_group"
    )
    val dwsCols = Set(
      "total_revenue", "total_qty", "avg_unit_price", "total_marketing",
      "total_gross_profit", "marketing_ratio", "accessory_sales",
      "warranty_sales", "order_cnt", "model_cnt", "avg_user_rating", "sale_ym"
    )
    val adsCols = Set(
      "qty_growth_ratio", "opportunity_score", "potential_score",
      "contribution_ratio", "rolling_margin_30d", "deviation_ratio",
      "is_anomaly", "anomaly_level", "metric_code", "metric_value",
      "mom_ratio", "yoy_ratio"
    )
    enToCn.keys.map { en =>
      val layer =
        if (adsCols.contains(en)) "ads"
        else if (dwsCols.contains(en)) "dws"
        else if (dwdCols.contains(en)) "dwd"
        else if (odsCols.contains(en)) "ods"
        else "unknown"
      en -> layer
    }.toMap
  }

  /** 字段所属业务类目（粗分） */
  val enToCategory: Map[String, String] = {
    def cat(en: String): String = en match {
      case s if Set("sale_date", "sale_year", "sale_month", "sale_quarter",
                    "sale_dow", "sale_ym").contains(s) => "time"
      case s if Set("brand", "model", "processor", "os",
                    "camera_pixel_wan", "battery_capacity_mah",
                    "screen_size_inch", "storage_gb", "refresh_rate_hz").contains(s) => "product"
      case s if Set("user_city", "user_age", "user_gender",
                    "user_member_level", "age_group", "user_rating").contains(s) => "user"
      case s if Set("promotion").contains(s) => "channel"
      case s if s.endsWith("_cost") || s.contains("commission") ||
                s.contains("marketing") => "cost"
      case s if s.endsWith("_sales") || s.startsWith("warranty_") ||
                s.contains("accessory") || s.contains("insurance") => "sales"
      case s if Set("revenue", "total_revenue", "gross_profit", "total_gross_profit",
                    "gross_margin", "unit_price", "sales_qty", "avg_unit_price",
                    "total_qty", "total_cost", "total_marketing", "order_cnt",
                    "model_cnt").contains(s) => "metric"
      case _ => "derived"
    }
    enToCn.keys.map(en => en -> cat(en)).toMap
  }
}
